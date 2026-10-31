# Deployment guide (Azure free tiers + GitHub Actions)

This guide deploys the project with the services chosen in [`plan.markdown`](../plan.markdown):

| Part | Service | Plan to pick |
|---|---|---|
| Frontend (React build) | Azure Static Web Apps | **Free** |
| Backend (Docker image) | Azure Container Apps | **Consumption**, min replicas 0, max 1 |
| Database | Azure SQL Database | **Free offer**, "Auto-pause the database until next month" when the limit is reached |
| OTP email | Brevo | **Free** (300 emails/day) |
| Image registry | GitHub Packages (`ghcr.io`) | free |
| CI/CD | GitHub Actions (`.github/workflows/ci-cd.yml`) | included minutes |

```text
Browser ──► Static Web Apps (https://<name>.azurestaticapps.net)      React app
   │
   └──────► Container Apps (https://<app>.<env>.<region>.azurecontainerapps.io)   Spring Boot API
                 │                      └─ compiles & traces user code in a sandboxed child JVM
                 ├──► Azure SQL Database   (accounts, OTP challenges, run history)
                 └──► Brevo API            (OTP emails)
```

Free limits change; re-check each service's review page before clicking **Create**, and set a budget alert (step 9).

> **Known limitation of this split setup.** The frontend and backend are on different sites, so the login cookie is a cross-site cookie (`SameSite=None; Secure`). Chrome, Edge and Firefox accept it; **Safari and all iPhone/iPad browsers block it by default**, so login fails there. The fix is a custom domain later (`www.example.com` → Static Web Apps, `api.example.com` → Container Apps), after which the cookie is same-site and works everywhere.

## How things run automatically

- **GitHub Actions.** GitHub runs every workflow file in `.github/workflows/` when one of the events listed under `on:` happens. `ci-cd.yml` lists: a pull request is opened or updated (tests + builds only, result shown as ✓/✗ on the PR), a push to `main` such as merging a PR (tests, builds, then deploy), and **Run workflow** clicked in the Actions tab. Pushing to a feature branch alone runs nothing. Deploy jobs skip themselves until the Azure variables exist. To deploy only after your approval, add yourself as a required reviewer on the GitHub environment `production`.
- **Database migrations (Flyway).** Every time the backend starts, Flyway compares the scripts in `backend/src/main/resources/db/migration/<h2|sqlserver>/` with its record table `flyway_schema_history` and runs only the new ones, in version order. Starting the app in Eclipse, running `mvn clean verify` (in-memory test database) or the container starting on Azure all bring the database up to date; no SQL is run by hand. Scripts that already ran are never edited (Flyway stores a checksum and refuses to start if one changes); changes are new versions, e.g. V4 drops the `SPRING_SESSION` tables left over from an early server-side login design that JWT login replaced.

---

## Step 0 — Merge the code and publish the first image

1. Open a pull request from `feature/function-box-implementation` to `main`. The **CI/CD** workflow runs backend tests, the frontend build and a Docker build. Merge when it is green.
2. The push to `main` builds the image and pushes it to `ghcr.io/kaushiksangmuli/java-visualizer/backend:<commit-sha>`. The deploy jobs are skipped because Azure is not configured yet; that is expected.
3. On GitHub → your profile → **Packages** → `java-visualizer/backend` → **Package settings** → set visibility to **Public** (simplest; the image contains no secrets). If you keep it private, see "Private image" at the end.
4. Note the full image name with the commit SHA from the workflow log (job **Backend image**).

## Step 1 — Resource group

Azure Portal → **Resource groups** → **Create** → name `rg-java-visualizer`, region close to you (for example **Central India**). Use this region for everything below.

## Step 2 — Azure SQL Database (free offer)

1. Portal → **Azure SQL** → **SQL databases** → **Create**.
2. Click **Apply offer** for the free database (if the banner is shown for your subscription).
3. Database name `javavisualizer`; **Server** → *Create new*: name e.g. `jv-sql-<yourname>`, authentication **SQL authentication**, admin login and a strong password. Save both.
4. **Free limit reached behavior**: **Auto-pause the database until next month** (never "Continue using database for additional charges").
5. **Networking**: Public endpoint, **Allow Azure services and resources to access this server = Yes**. Add your current client IP only if you want to connect from your PC.
6. Create. The JDBC URL is:

   ```text
   jdbc:sqlserver://<server-name>.database.windows.net:1433;database=javavisualizer;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;
   ```

The tables are created automatically by Flyway when the backend starts (migrations V1–V4 in `backend/src/main/resources/db/migration/sqlserver`). You do not run SQL by hand.

## Step 3 — Brevo (OTP email)

1. Create a free account at brevo.com.
2. **Senders, domains & dedicated IPs** → add and verify the sender address the codes will come from.
3. **SMTP & API** → **API keys** → create a key. Save it.

## Step 4 — Generate the JWT signing secret

Any random string of at least 32 characters. In PowerShell:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

## Step 5 — Static Web App (create it first to get the frontend URL)

1. Portal → **Static Web Apps** → **Create**; plan **Free**; deployment source **Other** (our workflow deploys it).
2. After creation, copy the **URL** (e.g. `https://lively-sea-0123.azurestaticapps.net`) — this is `FRONTEND_URL`.
3. **Manage deployment token** → copy the token.

## Step 6 — Container App (backend)

1. Portal → **Container Apps** → **Create**.
   - Container Apps environment: *Create new*, **Consumption only**, same region.
   - Container: uncheck "Use quickstart image"; **Image source: Docker Hub or other registries**; image type Public; registry `ghcr.io`; image and tag from step 0 (e.g. `kaushiksangmuli/java-visualizer/backend:<sha>`).
   - CPU / memory: **1 vCPU / 2 Gi** (the backend plus up to 4 user-program JVMs need the memory).
   - Ingress: **Enabled**, **Accepting traffic from anywhere**, target port **8080**.
2. After creation open the app:
   - **Scale**: min replicas **0**, max replicas **1** (one replica keeps rate limits consistent and costs low).
   - **Secrets**: add `db-password`, `jwt-secret`, `brevo-api-key`.
   - **Containers → Edit and deploy → Environment variables**:

     | Name | Value |
     |---|---|
     | `SPRING_PROFILES_ACTIVE` | `azure` |
     | `DB_URL` | JDBC URL from step 2 |
     | `DB_USERNAME` | SQL admin login |
     | `DB_PASSWORD` | secret reference `db-password` |
     | `VISUALIZER_JWT_SECRET` | secret reference `jwt-secret` |
     | `VISUALIZER_BREVO_API_KEY` | secret reference `brevo-api-key` |
     | `VISUALIZER_EMAIL_SENDER` | verified sender address |
     | `CORS_ALLOWED_ORIGINS` | `FRONTEND_URL` from step 5 (exact, no trailing slash) |
     | `ALLOWED_SIGNUP_EMAILS` | *optional — leave unset so anyone can sign up.* To limit sign-up later: comma-separated emails or `@domains`, e.g. `@cdac.in, friend@gmail.com` |

   - **Health probes**: liveness and readiness HTTP `GET /health` on port 8080.
3. Copy the **Application Url** — this is `BACKEND_URL`. Open `BACKEND_URL/health`; it should return `{"status":"ok"}` (the first request after idle can take ~30–60 s while the container and the paused database wake up).

## Step 7 — Let GitHub Actions deploy (OIDC, no stored Azure password)

1. Portal → **Managed Identities** → **Create** user-assigned identity `id-jv-github` in the resource group.
2. In the identity → **Federated credentials** → **Add** → scenario **GitHub Actions deploying Azure resources**: organization `KaushikSangmuli`, repository `java-visualizer`, entity **Environment**, environment name `production`.
3. Resource group `rg-java-visualizer` → **Access control (IAM)** → **Add role assignment** → **Contributor** → the identity `id-jv-github`.
4. GitHub repo → **Settings → Environments** → create `production` (optionally require your approval before each deploy).
5. GitHub repo → **Settings → Secrets and variables → Actions**:
   - **Variables**: `AZURE_CLIENT_ID` (identity's client ID), `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_RESOURCE_GROUP` = `rg-java-visualizer`, `AZURE_CONTAINERAPP_NAME` = your app name, `BACKEND_URL` = Application Url from step 6.
   - **Secrets**: `AZURE_STATIC_WEB_APPS_API_TOKEN` = deployment token from step 5.

## Step 8 — Deploy and smoke test

1. GitHub → **Actions** → **CI/CD** → **Run workflow** on `main` (later every push to `main` deploys).
2. The workflow tests, builds, pushes the image, updates the Container App, waits for `/health`, then uploads the frontend (built with `VITE_API_BASE_URL = BACKEND_URL`).
3. Open `FRONTEND_URL` in Chrome or Edge: sign up → the 6-digit code arrives by email → verify → sign in → run **Nested loops with a function call**.

## Step 9 — Cost guardrails and rollback

- **Cost Management → Budgets**: create a monthly budget (e.g. ₹200 / $2) with an email alert at 50 % and 100 %.
- Check the Azure SQL database's **free amount remaining** after the first week.
- **Rollback**: Container App → **Revisions** → activate the previous revision, or run `az containerapp update -n <app> -g rg-java-visualizer --image ghcr.io/kaushiksangmuli/java-visualizer/backend:<previous-sha>`.

## Safety before inviting the public

User programs run in a child JVM under a Java Security Manager policy (no environment variables, no files except their own classes, no network, no processes, no reflection on private members), plus the source blocklist, step/time/output limits, per-user rate limits and a non-root container. CPU and memory abuse are bounded by the limits but not isolated from other users of the same replica. Treat the first deployment as a **private beta** (share the URL with known users) until the production-readiness review in `plan.markdown` (Stage 7) is done.

## Private image (optional)

If the GHCR package stays private, create a GitHub personal access token (classic) with `read:packages`, then in the Container App → **Registries** (or `az containerapp registry set`) add server `ghcr.io`, username = your GitHub user, password = the token.

## Run the image locally (optional)

```powershell
docker build -t java-visualizer-backend backend
docker run --rm -p 8080:8080 java-visualizer-backend
```

This uses the default `local` profile (H2 database inside the container, OTP `1234`). Then run the frontend with `pnpm run dev` as usual.
