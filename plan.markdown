# Java Code Memory Visualizer — Phase Two Plan

**Document purpose:** Product and technical plan for adding accounts, persistence, container packaging, CI/CD, and a first cloud deployment to the existing Java Code Memory Visualizer repository.

**Status:** Phase-two implementation is underway (see the status table below; visualization features added 2026-10-31 are on `feature/function-box-implementation`). Email/password authentication now uses stateless JWT cookies, login-first UI, and a local-only OTP bypass. Locally, `mvn clean verify` passes and the app works end to end with H2 (owner, 2026-10-31). The repository is deployment-ready (Docker image, CI/CD workflow, `docs/DEPLOYMENT.md`); Azure resources and provider credentials are not created yet.

> **New here or lost the chat history?** Read [`docs/HANDOVER.md`](docs/HANDOVER.md) first (what was done, decisions, how to continue). The step-by-step deployment checklist is section 11 of this file; screen-by-screen details are in [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

**Overall project status (2026-10-31):** the last change set verified on the developer machine is commit `5590aa3` (run history). The deployment-readiness commit `1c08d1d` (sandbox, CSRF cookie, V4, CI/CD) has not been built by Maven yet; its first build is the pull-request CI run.

| Area | Status | Pending |
|---|---|---|
| Visualizer core (compile, JDI trace, stack, heap, statics, metadata, state changes) | Done; user-verified with samples | Full sample matrix in browser |
| Function-call boxes (waiting/returned rows in caller frames) | Done and verified: owner ran `mvn clean verify` successfully (4 warnings) on 2026-10-31; owner checked the UI in the browser | Merge to `main` |
| Loop boxes (iteration, condition corner, nested loops, per-iteration history) | Done and verified: owner ran `mvn clean verify` successfully (4 warnings) on 2026-10-31; owner checked the UI in the browser | Merge to `main` |
| Stack-first trace layout, compact variable boxes, collapsible frames, fixed step controls, heap objects open independently | Done; owner checked the UI in the browser and declared the frontend complete (2026-10-31) | First CI frontend build |
| Stage 1–2 accounts (H2/SQL Server, signup, OTP, JWT cookie, reset) | Working locally with H2 and OTP `1234` (owner, 2026-10-31); build and H2 migrations verified by `mvn clean verify` | Azure SQL migration, Brevo email and cross-site cookies on first deploy |
| Run history (`visualization_run`: every run, outcome, failure reason; `GET /api/runs`) | Done and verified: owner ran `mvn clean verify` successfully (4 warnings) on 2026-10-31 (H2 migration + integration test) | Azure SQL migration on first deploy; optional UI |
| Stage 3 Docker image | Dockerfile ready (dependency cache, non-root, JDK 21, writable H2 folder); local `docker run` documented | First build in GitHub Actions; sample traces inside the container |
| Stage 4 GitHub Actions CI | `.github/workflows/ci-cd.yml`: backend `mvn verify`, frontend build, Docker build + image check | First green run on a pull request |
| Stage 5–6 Azure deployment (SWA Free, Container Apps, Azure SQL free, Brevo, GHCR, OIDC) | Workflow deploy jobs and step-by-step guide `docs/DEPLOYMENT.md` ready; skipped until Azure variables exist | Create Azure resources, set GitHub variables/secrets, first deploy and smoke test |
| Stage 7 production readiness / sandbox gate | User-code JVM sandboxed (Security Manager: no env/files/network/processes); blocklist gaps closed; per-user limits | Private beta first; per-user CPU/memory isolation and threat review before public launch |
| Future: DSA sheet, company lists, LeetCode picker | Not started | Product decisions |

**Repository root:** `java-visualizer/` (the directory containing `backend/`, `frontend/`, and this file).

**Current application:** React + Vite frontend and Spring Boot 3.2.x backend. The backend compiles submitted Java and runs it in a separate debuggee JVM through JavaCompiler/JDI. Email/password and OTP authentication use local H2 or SQL Server/Azure SQL profiles; JWT-cookie issuance, login gating, migrations, email delivery, and browser flow still need runtime verification. Local H2 and `local-sqlserver` profiles accept OTP `1234`; hosted configuration must use the `azure` profile and an external signing secret. The Docker and safety design must preserve the JDK/JDI requirements and must not treat the AST blocklist as a sandbox.

## 0. Selected low-cost services and push workflow

- **Frontend — Azure Static Web Apps:** select **Free** when creating the app. Published quota: 100 GB bandwidth/month, 250 MB per deployed environment, up to 3 preview environments, 2 custom domains, and free TLS. The Free plan has no paid bandwidth-overage rate; check current quota behavior and GitHub/private-repository terms before deployment.
- **Backend — Azure Container Apps:** select the **Consumption** workload profile, minimum replicas `0`, and a small maximum replica count. Monthly free grant: 180,000 vCPU-seconds, 360,000 GiB-seconds, and 2 million external HTTP requests per subscription. Avoid adding a dedicated profile/VNet unless its cost is accepted.
- **Database — Azure SQL Database:** select **Apply offer / Free database** for an eligible subscription. For **Free limit reached behavior**, choose **Auto-pause the database until next month**; do not choose **Continue using for additional charges**. Allowance: 100,000 serverless vCore-seconds, 32 GB data, and 32 GB backup per database/month; up to 10 eligible databases per subscription.
- **Email — Brevo:** select the **Free** plan for transactional email, currently up to 300 messages/day. Verify a sender address/domain and configure the API key as a backend secret; confirm any account onboarding/sender restrictions and current quota before use.
- Limits are current published offers, may change, and are not a promise that every surrounding resource is free. Registry storage/egress, networking, domain registration, overages, and other subscription usage can cost money. Set budget alerts and verify the final review screen before provisioning.
- **GitHub Actions:** add `.github/workflows/ci-cd.yml`, triggered by `push` to `main` and `pull_request`. Pull requests run checks; a green push to `main` deploys the frontend to Static Web Apps and the backend image/revision to Container Apps. Run versioned Flyway migrations as a controlled backend deployment job; never recreate the database. Brevo is configured through Container Apps secrets and is not independently deployed. GitHub Actions usage quotas are separate from Azure free grants and depend on the GitHub plan.
- Prefer Azure OIDC/federated credentials for workflow login; use GitHub repository secrets only for the non-secret Azure identifiers required by the action. Keep SQL and Brevo credentials in Azure Container Apps secrets. The backend container requires an image registry (Azure Container Registry or GitHub Container Registry); include its storage/egress and private-image configuration in cost review.
- **Domain:** optional for the first deployment. Use Azure-provided URLs at no domain cost; later map one purchased domain to `www` (frontend) and `api` (backend). Azure managed TLS is available for supported custom-domain configurations.

Official quota references: [Static Web Apps quotas](https://learn.microsoft.com/en-us/azure/static-web-apps/quotas), [Container Apps billing](https://learn.microsoft.com/en-us/azure/container-apps/billing), [Azure SQL free offer FAQ](https://learn.microsoft.com/en-us/azure/azure-sql/database/free-offer-faq?view=azuresql), [Brevo transactional email](https://www.brevo.com/products/transactional-email/), and [GitHub Actions workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).

## 1. Product Goal and Phase Scope

Phase two changes the application into an account-based service. A visitor first sees login and can follow a signup link, verify an email address, log in, and recover access. The visualizer requires authentication. Later, a user can optionally save explicitly selected Java projects or visualizations; users must only access their own saved data.

The first authentication experience should be ordinary email and password registration with email one-time-code verification. Password recovery uses a separate email one-time code. This is email-based account verification, not Google/Gmail social sign-in. Google OAuth and mobile-number identity/OTP are later additions and must fit an extensible identity model without requiring a redesign.

The goal is a low-cost demonstration deployment, not a production security certification. Public hosting of arbitrary user Java remains blocked until execution isolation is reviewed and strengthened. The existing separate JVM, time/step/output limits, blocklist, and Docker image are useful controls but do not by themselves securely isolate hostile code from the host.

### In scope

- Relational persistence and versioned schema migrations.
- Signup, email verification, login, logout, password reset, and account ownership checks.
- OTP delivery through a transactional email service, with abuse controls.
- Docker image(s) compatible with JavaCompiler and JDI.
- GitHub Actions CI for backend and frontend; controlled deployment workflow.
- Low-cost cloud pilot with HTTPS, managed database, secrets, monitoring, and budgets.
- Documentation, configuration examples, and tests for the new behavior.

### Not in this phase

- Google OAuth/social login and phone-number OTP (keep the schema ready for these).
- Premium/company question catalogs or curated DSA/LeetCode content.
- Billing, subscriptions, paid plans, or monetization.
- Storing every submitted program or trace automatically.
- Publicly exposing arbitrary Java execution before the sandbox deployment gate is met.

## 2. Database Recommendation and Data Plan

### Recommendation

**Recommended first database: Azure SQL Database using its SQL Server-compatible engine.** This matches the project owner's MSSQL preference and has a current free offer with up to 10 databases per Azure subscription. The published allowance is 100,000 serverless vCore-seconds, 32 GB data, and 32 GB backup storage per database per month. Configure the free-limit behavior to pause the database until the next month, monitor its usage, and set a billing alert; choosing continued use can incur charges above the free allowance. Check the offer's eligibility in the actual Azure subscription before committing to it.

Use file-backed H2 by default for quick local account development; the `azure` Spring profile targets Azure SQL. The SQL Server migrations are authoritative for cloud deployment, so verify them against Azure SQL before release. Keep schema and persistence logic portable where practical. If the free Azure SQL offer is unavailable to the account or region, compare a small paid Azure SQL tier against PostgreSQL managed free tiers before switching engines. Do not put important user data on a temporary database tier without backups/export.

### Data minimization rules

- Do not persist raw source code or trace JSON for every run by default. A submitted program can contain personal, academic, or confidential content.
- Save source code and an associated trace only when the signed-in user explicitly chooses **Save**. Set a documented size limit and retention/deletion behavior.
- Do not log passwords, OTPs, access/refresh tokens, full submitted code, or full trace payloads.
- Keep only the security/audit information needed to investigate account changes and abuse; define retention before collecting client IP or device information.
- Provide account deletion that removes or anonymizes owned project/run data according to the retention policy.

### Initial logical schema

Use UUID/uniqueidentifier primary keys, UTC timestamps, foreign keys, unique constraints on normalized identifiers, and indexes for ownership and expiry queries. Names are logical; final columns and nullability are decided during Stage 1.

| Table | Purpose and fields to consider |
|---|---|
| `app_user` | Account id, normalized email, email verification time, password hash (nullable only if a future social-only account is supported), status, created/updated/deleted timestamps. Store no plaintext password. |
| `user_identity` | Extensible login identities: user id, provider (`local_email`, later `google`, later `phone`), provider subject/normalized identifier, verified timestamp. Unique provider + subject. Do not assume Gmail OAuth and email OTP are the same identity mechanism. |
| `verification_challenge` | Signup verification and password-reset challenges: user/destination, purpose, code hash, created/expiry/consumed times, failed attempts, resend count/next-allowed time. Store a hash, never the delivered OTP. |
| Server-side session tables | Not used; the `SPRING_SESSION` tables created by V2 are dropped by V4 (2026-10-31). Authentication is stateless JWT; password-reset does not immediately revoke already issued JWTs, which remain valid until their 30-minute expiry. |
| `visualization_run` | **Implemented (V3).** One row per visualization by a signed-in user: time, outcome SUCCESS/FAILED, status, message, failure reason, step count, truncated, code length, duration. No source code. Cascade-deleted with the user. |
| `saved_project` | User-owned project label, created/updated time, optional archive/delete time. |
| `saved_source` | User-owned source file/project id, safe filename, Java source, language/version metadata, created/updated time. Save only on explicit action. |
| `saved_visualization` | Optional explicitly saved run: source id or source snapshot, backend/trace schema version, status, timestamp, and bounded trace JSON or compressed payload. Establish quotas/retention and size limits before enabling. |
| `security_event` | Minimal events such as signup, verification, login failure/success, password reset, logout, and session revocation. Exclude credentials and code. Apply short retention. |

Do not add a separate `otp` table with plaintext codes. Verification challenges are one-time, purpose-bound, hashed, expire quickly, have a small attempt limit, are invalidated after success, and are throttled by account and network origin. Avoid account enumeration in signup, login, and password-reset responses.

### Persistence technology

- Spring Data JPA/Hibernate for domain persistence; SQL Server JDBC driver for Azure SQL/SQL Server connectivity.
- Flyway migrations as the schema source of truth. Do not use Hibernate `ddl-auto=update` in deployment.
- Externalize JDBC URL, username, and password through environment/secret storage. Never commit credentials.
- Add separate local/test and cloud profiles; keep the existing trace-specific `visualizer.*` limits independent of database settings.
- Use transaction boundaries in services, not controllers. Enforce record ownership in backend queries, not only in the UI.

## 3. Authentication and OTP Decisions

### First release

1. Signup accepts email + password, normalizes the email, creates an unverified account, and sends an email verification OTP.
2. Verification consumes the challenge and marks the email identity verified.
3. Login accepts email + password and issues a short-lived signed HS256 JWT in an HttpOnly cookie. Keep the signing secret outside source control; the Azure profile requires `VISUALIZER_JWT_SECRET`.
4. Logout clears the browser cookie. Since JWTs are stateless, an already copied token remains usable until its short expiry; add a token-version or revocation mechanism if immediate revocation becomes a requirement.
5. Use Spring Security and a modern adaptive one-way password hash (Argon2id where available; otherwise a deliberately configured BCrypt cost). Enforce TLS, secure token handling, CSRF/CORS policy appropriate to the chosen browser token design, rate limits, and generic auth error text.
6. `/health` and public auth bootstrap/signup/verification/login/password-reset routes do not require a JWT. `/api/visualize`, `/api/auth/me`, and other account routes require valid JWT authentication; retain CSRF protection because the browser sends the auth cookie automatically. Account deletion is deferred and is not part of the current implementation.
7. In the local H2 and `local-sqlserver` development profiles only, use fixed OTP `1234` for signup and password-reset flows when email credentials are unavailable. Hosted profiles must never activate either local OTP profile; they use generated six-digit codes and the configured transactional email provider.

### Email or phone OTP?

Start with **email OTP**. For this application it is the better initial low-budget choice: it is global, requires no country-specific mobile routing, and transactional email currently has free entry plans. Brevo currently advertises 300 sends per day on its free plan; verify sender-domain requirements, daily quota, and account eligibility when configuring it. Do not send production OTP through a developer's personal Gmail inbox/SMTP account. A transactional provider/API is designed for application mail and provides sending logs and bounce handling.

Treat SMS as a later option for phone verification or account recovery where product needs justify the cost. Firebase phone verification requires the pay-as-you-go billing plan for production SMS and bills per message; SMS rates vary by destination and provider. If the primary audience is India, check current India route pricing and any applicable sender/template registration obligations before enabling it. Use fictional/test numbers during development. Add per-phone, per-account, and per-IP throttles and spend caps.

“Continue with Google” is a future OAuth/OpenID Connect feature. It is separate from sending a verification code to a Gmail address. The identity table should support it later without storing a Google password.

## 4. Hosting and Cost Recommendation

### Suggested low-cost pilot architecture

- **Frontend:** Azure Static Web Apps Free, connected to the private GitHub repository with GitHub Actions. The current published Free plan includes static hosting, TLS, custom domains, and 100 GB bandwidth; private-repository publishing and GitHub account plan rules must be checked before choosing it. Cloudflare Pages or another static host can be compared if private Git integration has a plan constraint.
- **Backend:** Azure Container Apps Consumption plan, deploying a custom Docker image. It currently grants the first 180,000 vCPU-seconds, 360,000 GiB-seconds, and 2 million external HTTP requests per subscription/month; it can scale to zero. Monitor usage and configure max replicas/concurrency so trace workload cannot create unexpected scaling charges.
- **Database:** Azure SQL Database free offer described above. Use the pause-at-limit behavior to avoid automatic overage charges, understanding that the app's persistence operations become unavailable after the allowance is exhausted until the next month.
- **Images:** Initially build and deploy through GitHub Actions to Azure Container Apps. Choose an image registry and retention policy as part of CI setup. Private container registry storage/egress can incur charges; include it in the budget review.
- **Secrets:** Azure Key Vault or Container Apps secrets for database/email credentials. GitHub Actions should authenticate to Azure with OpenID Connect/federated identity rather than a long-lived client secret where feasible.
- **Observability:** Application logs without code/credentials, Azure usage/budget alerts, health probes, and a deployment rollback procedure.

Azure's monthly free grants are shared at subscription level, not reserved solely for this app. Some resources, network configuration, registry storage/egress, domain registration, email volume, and usage over the included amounts can still cost money. Set a small spend budget/alert and review every bill line. Free tiers are suitable for a demo/pilot, not an uptime or backup guarantee.

### Alternatives and caveats

- Render has a convenient free demo path, but its current free Postgres databases expire after 30 days and have no backups; free web services sleep after inactivity and are limited to 512 MB RAM. This is acceptable for disposable previews, not a place to keep real user accounts or saved projects.
- If choosing PostgreSQL instead of the MSSQL preference, compare a managed PostgreSQL free tier and ensure backup/retention terms meet the project's needs. Switching should be a deliberate decision before migrations are established.
- Keep all services in a nearby region where possible. Measure startup, JDI behavior, heap/memory use, and database wake-up latency before inviting external testers.

## 5. Docker and Online Execution Safety Gate

The existing backend runs user code in a child debuggee JVM but that JVM still shares the backend container's OS identity, filesystem, and network unless additional isolation is applied. A Docker image alone does not make this safe for arbitrary public submissions.

**Update 2026-10-31:** the child JVM now runs under a Java Security Manager policy that denies environment variables, file access outside its own classes, network, process creation and private reflection, so hosted secrets in environment variables are no longer readable by user code. CPU/memory isolation between users of one replica and a formal threat review are still outstanding, so the first deployment is a private beta.

Before public code execution, require a threat-model review and an execution boundary with no network, non-root identity, read-only base filesystem, isolated temporary workspace, strict CPU/memory/process/file limits, hard wall-clock kill, restricted syscalls/capabilities, and cleanup even on crash. Prefer one disposable isolated sandbox per execution (or a proven equivalent). Keep compiler/JDI support in the runtime image: use a full JDK 21 image, not a JRE-only image, because `JavaCompiler` and `jdk.jdi` are required. Do not mount application secrets or the host Docker socket into a user-code sandbox. If this safety gate is not ready, deploy privately for trusted demonstrations and disable anonymous execution on the public endpoint.

## 6. CI/CD Plan

Use GitHub Actions in the existing private repository. Begin with CI only; deployment must be a separately controlled job/environment.

### Pull request / push CI

- Backend job: set up JDK 21, cache Maven dependencies, run `mvn -B clean verify` from `backend/`, and retain test reports on failure.
- Frontend job: set up the selected Node LTS and pnpm, install using the lockfile (`pnpm install --frozen-lockfile`), run lint/type checks if configured, and `pnpm run build` from `frontend/`.
- Docker job: build the backend image and validate that it contains JDK 21/JDI/compiler modules and runs as non-root. Add dependency/security scanning as a later hardening increment.
- Trigger on pull requests and pushes to `main`; use least-privilege workflow permissions, pinned action versions, and no production secrets in pull-request jobs from untrusted forks.

### Deployment workflow

- Run only after CI is green on the protected main branch or by an approved manual dispatch.
- Build/tag an immutable container image with commit SHA, push to the chosen registry, deploy to a staging Container App, and smoke-test `/health` and authenticated/API behavior.
- Promote to production only after review and sandbox gate. Add rollback to the previous image and expand database schema with backward-compatible migrations.
- Authenticate to Azure using GitHub Actions OIDC/federated credentials; keep cloud secrets out of workflow YAML and Git.
- Monitor GitHub Actions minutes/storage for the private repository because included quotas depend on the account plan; avoid assuming unlimited free CI.

## 7. Ordered Implementation Stages

### Stage 0 — Decisions and baseline

- Confirm database choice (recommended: Azure SQL), target hosting region, frontend/backend origins, repository branch protection, and whether public visualization remains anonymous.
- Confirm Azure subscription eligibility, spending guardrails, intended retention for user code/runs, and acceptable login/session behavior.
- Record current build/test baselines. Export a database backup before any production migration once data exists.
- **Exit criteria:** decisions and monthly budget alert are written down; no resource is provisioned yet.

### Stage 1 — Database foundation

- Add JPA, SQL Server JDBC, Flyway, and any version-specific Flyway SQL Server module compatible with the Spring Boot-managed version.
- Design/migrate the initial identity, verification challenge, saved project/source, and security event schema. Avoid persisting stateless JWTs in the database.
- Add externalized datasource configuration and local test profile; validate migrations against a real SQL Server-compatible database.
- Add indexes, foreign keys, unique normalized identities, transaction boundaries, ownership query patterns, and account-data deletion behavior.
- **Exit criteria:** clean database migration, persistence tests, no secrets in Git, and backup/export procedure documented.

### Stage 2 — Authentication and account APIs

- Add Spring Security and registration, email verification, login, JWT logout, password reset, and current-user flows.
- Add email provider adapter and local development mail sink; do not send real mail in tests.
- Add short OTP expiry, hashed OTPs, one-time purpose binding, attempt/resend limits, generic responses, login throttling, password hash policy, CSRF protection, a short JWT lifetime, and a local-only fixed OTP bypass.
- Defer account deletion pending an explicit product/authorization decision; it has no endpoint in the current implementation.
- Secure saved-project endpoints and prove cross-user access is denied. Keep `/health` public.
- **Exit criteria:** integration tests cover successful and failed flows, expiry/replay, rate limits, JWT expiry/logout behavior, and data ownership. Assess whether immediate JWT revocation is needed before public launch.

### Stage 3 — Docker packaging and local deployment

- Review/update backend `Dockerfile` for reproducible multi-stage image, JDK 21 runtime, Java compiler and JDI module availability, non-root user, health check, and configurable port.
- Add local Docker Compose for backend, SQL Server-compatible development database, and optional local mail sink. Keep secrets in local ignored environment files.
- Test compile and trace of supplied samples inside the image, including Windows-to-Linux path/quote assumptions and child-JVM cleanup.
- **Exit criteria:** clean image build, local compose startup, DB migration, auth smoke test, sample traces, and resource-limit checks.

### Stage 4 — GitHub Actions CI

- Add independent backend, frontend, and image-build workflows or jobs as described above.
- Test workflows on a branch/PR and on main; document private Actions quota monitoring and failure diagnostics.
- Add branch protection requiring successful CI before merges.
- **Exit criteria:** a clean PR runs the whole CI successfully without developer-machine state.

### Stage 5 — Cloud staging deployment

- Provision Azure Static Web Apps, Azure Container Apps Consumption, Azure SQL free offer, a registry, email sender, and secrets using a documented checklist or infrastructure-as-code.
- Configure TLS, allowed CORS origins, database firewall/networking, minimum/maximum replicas, budget/usage alerts, logging retention, and private test access.
- Use a private staging environment while the code-execution sandbox gate is incomplete.
- **Exit criteria:** deployed health/auth/OTP/persistence/sample-trace smoke tests, budget alarms, tested rollback, and an explicit security decision on public execution.

### Stage 6 — Controlled continuous deployment

- Add OIDC-based GitHub Actions deployment to staging, approval gate for production, immutable image tags, compatible DB migration ordering, smoke checks, and rollback.
- Keep production deployment manual/approved until stability and safety are demonstrated.
- **Exit criteria:** documented and rehearsed deployment, rollback, and database recovery procedures.

### Stage 7 — Production readiness review

- Review sandbox isolation, threat model, rate limits, CORS/CSRF/token design, secrets, data minimization, privacy/retention/deletion, backups, observability, and incident response.
- Validate cloud spend under expected concurrent debugger sessions; confirm the database's free-limit behavior and what users see when it pauses.
- Only then decide whether to invite the public or remain a private demo.
- **Exit criteria:** all critical security and recovery items accepted, or a clear list of restrictions for the demo.

## 8. Completion Checklist

*Status as of 2026-10-31; ticked items are done, notes say what is left.*

- [ ] Database and schema decision recorded; Azure SQL free-offer eligibility checked. — Decision recorded (Azure SQL); eligibility is checked when creating the database (runbook step 2).
- [ ] Migrations, backup/export, retention, and account deletion policy implemented. — Migrations V1–V4 done and verified on H2; backup/export, retention and account deletion not done.
- [x] Password and OTP secrets are never stored/logged in plaintext. — BCrypt hashes for passwords and OTPs; secrets only in environment/secret store.
- [ ] Email OTP delivery works in staging; email quota and sender domain are verified. — Brevo setup is runbook step 3.
- [ ] Authentication/session security and account ownership have automated tests. — Integration test covers anonymous rejection and per-user run history isolation; signup/OTP/reset flows not yet tested automatically.
- [ ] Docker image includes JDK/compiler/JDI and does not run as root. — Dockerfile does both; CI checks it on the first pull-request run.
- [ ] User-code execution isolation is approved before public execution is enabled. — Security Manager sandbox in place; private beta until the Stage 7 review.
- [ ] GitHub Actions CI passes on a clean runner; secrets use OIDC/secret store. — Workflow written with OIDC; first run pending.
- [ ] Staging health, authentication, persistence, and trace smoke tests pass. — Runbook step 8.
- [ ] Cost alerts, rollback, backups, and deployment instructions are verified. — Instructions written (`docs/DEPLOYMENT.md`); alerts/rollback are runbook step 9.

## 9. Current Pricing and Reference Links

Pricing and free tiers change; the figures below were checked on **2026-10-18** and must be rechecked before provisioning. Cloud location, tax, account eligibility, and usage affect final cost.

- [Azure SQL Database free offer](https://learn.microsoft.com/en-us/azure/azure-sql/database/free-offer) — per-database monthly compute/storage allowance, up to ten per subscription, and pause-or-pay behavior.
- [Azure Container Apps billing](https://learn.microsoft.com/en-us/azure/container-apps/billing) — monthly Consumption free grants and scaling/billing behavior.
- [Azure Static Web Apps pricing](https://azure.microsoft.com/en-us/pricing/details/app-service/static/) and [hosting plans](https://learn.microsoft.com/en-us/azure/static-web-apps/plans) — free static hosting features and limits.
- [Brevo pricing plans](https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans) — current free email send allowance and transactional email availability.
- [Firebase pricing](https://firebase.google.com/pricing) and [Authentication limits](https://firebase.google.com/docs/auth/limits) — phone-auth billing mode and quotas; use this only as a phone OTP comparison, not as the planned primary identity database.
- [GitHub Actions billing and usage](https://docs.github.com/en/actions/concepts/billing-and-usage) — private repository free-minute and storage quotas depend on account plan.
- [Render free services limitations](https://render.com/docs/free) — comparison showing demo-only service behavior and expiring, unbacked free Postgres.

## 10. Change History

### 2026-10-31 — Handover document and deployment runbook

**Technical analysis:** Added `docs/HANDOVER.md` (project summary, pipeline, timeline of all commits, decisions and reasons, local run, status, next steps, limitations, how to resume) and section 11 of this plan (deployment runbook with a status per step and every value to record). Updated the section 8 checklist to the real state and linked the handover from this plan, `README.md` and `CLAUDE.md`.

**Files changed:** `docs/HANDOVER.md` (new), `plan.markdown`, `README.md`, `CLAUDE.md`.

**Verification:** Documentation only.

### 2026-10-31 — Repository made deployable

**Technical analysis:** Review before deployment fixed: user code could read hosted secrets from environment variables (now a Security Manager sandbox plus `getenv`/method-reference blocklist); all users behind the Azure ingress shared one rate-limit/throttle bucket (now per account and real client IP); the CSRF cookie would break cross-site login (now `SameSite=None; Secure` in the azure profile); Hibernate validation could stop the app on SQL Server (`ddl-auto=none`, Flyway owns the schema); the image could not create its H2 folder; unused Spring Session tables (dropped by V4). Added `.github/workflows/ci-cd.yml` (tests, builds, GHCR image, Container Apps and Static Web Apps deploy via OIDC, guarded until Azure variables exist), `frontend/public/staticwebapp.config.json` and `docs/DEPLOYMENT.md`.

**Files changed:** backend `Tracer.java`, `CodeInspector.java`, `ClientAddress.java`, `VisualizeController.java`, `AuthController.java`, `SecurityConfiguration.java`, properties, migration V4, `Dockerfile`, `.dockerignore`, tests, README; frontend `public/staticwebapp.config.json`; `.github/workflows/ci-cd.yml`; `docs/DEPLOYMENT.md`; documentation and plans; `README.md`.

**Verification:** Sandbox and examples run with the real tracer on JDK 21; inspector run with JavaParser 3.17; other Java changes type-checked against stand-ins. Not yet run: `mvn clean verify` with these changes, Docker build, CI workflow, Azure.

### 2026-10-31 — Run history stored per user

**Technical analysis:** Added the `visualization_run` table (Flyway V3 for H2 and SQL Server), entity, repository and `RunHistoryService`. Every **Run & visualize** by a signed-in user is stored with outcome (SUCCESS/FAILED), status, message and failure reason; `GET /api/runs` returns the caller's totals and 20 newest runs. Source code is not stored. Fixed the integration test, which still called `/api/visualize` anonymously after authentication was added.

**Files changed:** backend migrations V3, `model/run/VisualizationRun.java`, `repository/run/VisualizationRunRepository.java`, `service/run/RunOutcome.java`, `service/run/RunHistoryService.java`, `controller/RunHistoryController.java`, `controller/VisualizeController.java`, `pom.xml`, tests; backend documentation, this file, `README.md`.

**Verification:** Outcome logic and tests run with JDK 21; Spring classes type-checked against stand-ins only. `mvn clean verify` and SQL Server migration pending.

### 2026-10-31 — Compact variables, collapsible frames, independent heap objects, fixed controls

**Technical analysis:** Array elements show a reference as a colour-coded id (`#463`) instead of repeating the type at every index; variable boxes are smaller in both modes; each function frame can be collapsed and expanded (expanded by default, remembered while stepping); heap objects open and close independently (bug: opening a second object closed both); the stack/heap area has a fixed height and state changes moved so the step controls no longer move between steps.

**Files changed:** frontend `components/TracePanels.jsx`, `App.jsx`, `styles.css`; `frontend/documentation/document.markdown`, `frontend/documentation/plan.markdown`, this file; `CLAUDE.md` (documentation rules for future changes).

**Verification:** Headless Chromium with a real trace (`int[]`, `int[][]`, `String[]`): controls at the same position on all steps in both modes, three heap objects open together, collapsed frame stays collapsed. `pnpm run build` pending locally.

### 2026-10-31 — Loop boxes inside stack frames

**Technical analysis:** Running loops are now shown as boxes inside their frame. The backend (`LoopSiteAnalyzer`, `LoopTracker`) finds `for`, for-each, `while` and `do-while` loops, their condition and declared variables, and attaches each running loop to its frame with the iteration, the condition filled with current values, a true/false verdict when the condition is checked, and nested loops. The frontend draws the loop box with the condition in the top-right corner (source small, values larger), the loop's own variables, calls made inside it and nested loop boxes; double-clicking a loop variable shows its value per iteration in the Focused box. The Loop status panel is commented out.

**Files changed:** backend `model/LoopSnapshot.java`, `model/FrameSnapshot.java`, `service/LoopSiteAnalyzer.java`, `service/LoopTracker.java`, `service/CallSiteAnalyzer.java`, `service/TraceEnricher.java`, `LoopTrackerTest.java`; frontend `loopHistory.js`, `components/TracePanels.jsx`, `App.jsx`, `styles.css`, `examples.js`; backend/frontend documentation and `README.md`.

**Verification:** Real tracer runs (nested for with a call, while, do-while, for-each over array and ArrayList) and headless-browser renders against real traces. `mvn clean verify` and `pnpm run build` pending locally.

### 2026-10-31 — Stack-first layout and stable variable-change keys

**Technical analysis:** Call stack and heap moved to the top of the right column with step controls below; the Execution trace header and step context are commented out; state changes sit under the heap (or stack in Normal mode); frames order parameters, function calls, locals, then other; Normal-mode variable cards are compact and frame headings bold. Variable-change keys now number frames from `main`, so a call no longer reports every outer variable as changed.

**Files changed:** frontend `App.jsx`, `components/TracePanels.jsx`, `styles.css`; backend `service/TraceEnricher.java`; documentation.

**Verification:** Headless-browser screenshots with a real FunMulti trace; real tracer output for the change keys.

### 2026-10-31 — Function-call boxes completed

**Technical analysis:** Finished the call box started by Codex on 2026-10-25. Caller frames list calls to user-written methods and constructors on their current line, as `result = fun_add(0, 4)` while waiting and `result = 4 <= fun_add(0, 4)` after return, with the source form on hover. Every waiting caller keeps its row; returned rows stay until the caller leaves the line; nested and repeated calls on one line are listed in evaluation order; library calls are ignored.

**Files changed:** backend `model/FunctionCallSnapshot.java`, `service/CallSiteAnalyzer.java`, `service/FunctionCallTracker.java`, `service/TraceEnricher.java`, `FunctionCallTrackerTest.java`; frontend `components/TracePanels.jsx`, `styles.css`; documentation. Work is on branch `feature/function-box-implementation`.

**Verification:** Real tracer runs covering recursion, nested calls, constructors, void methods and one-line loops; tests passed under a stand-in JUnit runner. `mvn clean verify` pending locally.

### 2026-10-18 — Set login-first UI and JWT authentication direction

**Technical analysis:** Updated phase-two product flow so visitors see login first and signup is an explicit option. Authenticated requests now use a short-lived HttpOnly JWT cookie with CSRF protection; `/api/visualize` requires authentication. Updated the plan to document the local OTP `1234` bypass and hosted secret/email requirements. JWT logout clears the cookie; because the token is stateless, a copied token remains valid until expiry.

**Files changed:**

- `plan.markdown` — revised phase-two product goals, proposed schema notes, auth design, and Stage 1–2 exit criteria; appended this analysis.
- `README.md` — updated account and browser auth descriptions.
- `backend/documentation/plan.markdown`, `backend/documentation/document.markdown`, `frontend/documentation/plan.markdown`, and `frontend/documentation/document.markdown` — revised their respective status, technical flow, and change history.

**Verification:** Frontend bundle was not verified in this shell: Vite transformed 21 modules but Windows denied writing the generated output, and the standard command also received `EPERM` under `node_modules/.vite-temp`. Maven and Java are unavailable, so backend compilation, migrations, startup, and browser auth remain unverified.

### 2026-10-18 — Verify frontend bundle with a permitted output directory

**Technical analysis:** Retried the production build using Vite's runner config loader and an output folder in the permitted temporary workspace. This verifies the current React application bundles, while the ordinary `dist/` output path remains blocked by workspace filesystem permissions. It does not verify API integration or backend authentication.

**Files changed:**

- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — appended this verification result and updated current build status.
- `README.md` — clarified frontend build and backend verification status.
- `plan.markdown` — appended this verification record.

**Verification:** `pnpm.cmd exec vite build --configLoader runner --outDir "C:\Users\Kaushik Sangmuli\.codex\visualizations\2026\10\17\01a14c32-da34-7df1-9c01-59ea069f3067\java-visualizer-frontend-build"` passed with Vite 8.3.4 (21 modules transformed). The temporary output directory was removed afterward. Backend Maven compilation and end-to-end authentication remain unverified.

### 2026-10-18 — Diagnose local H2 startup error from user's log

**Technical analysis:** The backend startup log showed H2 2.2.224 rejecting `MODE=MSSQL`. The H2 SQL Server compatibility mode is named `MSSQLServer`; updated the default local H2 connection URL. The SQL Server profile is unchanged.

**Files changed:**

- `backend/src/main/resources/application.properties` — correct H2 compatibility mode.
- `backend/README.md` and `backend/documentation/document.markdown` — describe the local mode.
- `backend/documentation/plan.markdown` and root `plan.markdown` — record the diagnosis and verification requirement.

**Verification:** Confirmed the exact compatibility-mode name in the [H2 compatibility mode documentation](https://h2database.github.io/html/features.html). Application startup could not be rerun in this coding shell because Java and Maven are unavailable. Restart the app in Eclipse and check that Hikari connects and Flyway migrations complete.

### 2026-10-18 — Remove obsolete session reset path and align CSRF header handling

**Technical analysis:** Kept authentication consistently stateless. Password reset no longer queries Spring Session storage, which was not provisioned and would cause reset requests to fail; existing JWTs continue until their configured short expiry. Configured Spring Security to resolve the CSRF request token in the plain form returned by `/api/auth/csrf` and echoed by the React client.

**Files and methods changed:**

- `backend/src/main/java/com/example/demo/config/SecurityConfiguration.java` — `securityFilterChain` now uses `CsrfTokenRequestAttributeHandler` with the cookie token repository.
- `backend/src/main/java/com/example/demo/service/auth/AuthService.java` — constructor and `resetPassword` no longer inject or query a Spring Session repository.
- `backend/pom.xml` — removed unused `spring-session-jdbc` dependency.
- `backend/src/main/resources/application.properties` — removed Spring Session JDBC settings.
- Root/backend/frontend documentation — updated current authentication status and recorded this change.

**Verification:** `git diff --check` reported no whitespace errors (only expected CRLF conversion notices). Java, `javac`, and Maven are not on PATH in this shell. Vite transformed the frontend modules but could not write build output due to Windows access denied; no backend or end-to-end auth result is claimed.

### 2026-10-18 — Initial Phase Two database, authentication, deployment, and CI/CD plan

**Technical analysis:** Established a relational account/data model, recommended Azure SQL Database to match the MSSQL preference, selected email OTP as the initial low-cost verification path, outlined Spring Security/session controls, proposed an Azure low-cost hosting topology, and added Docker, GitHub Actions, deployment, security, and readiness stages. The plan explicitly gates public arbitrary-code execution on stronger isolation.

**Files changed:**

- `plan.markdown` — new root-level phase-two project plan; no application code or infrastructure was changed.

**Verification:** Inspected the monorepo root, backend Maven dependencies/configuration, and current Git ignore rules. Cloud quotas/pricing references are linked in the plan and should be reconfirmed before deployment.

## 11. Deployment Runbook (step-by-step checklist)

Follow in order; tick each box and fill in the values as you go. Screen-by-screen instructions for each step are in [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md). Free-tier settings are fixed by section 0 of this plan.

**Values to record (never put passwords/keys in this file — store them in a password manager):**

| Name | Where it comes from | Value |
|---|---|---|
| Image | step 0, workflow log | `ghcr.io/kaushiksangmuli/java-visualizer/backend:<sha>` |
| Resource group / region | step 1 | `rg-java-visualizer` / ... |
| SQL server / database | step 2 | `....database.windows.net` / `javavisualizer` |
| `DB_URL` | step 2 | `jdbc:sqlserver://<server>.database.windows.net:1433;database=javavisualizer;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;` |
| Brevo sender | step 3 | ... |
| `FRONTEND_URL` | step 5 | `https://....azurestaticapps.net` |
| Container App name / `BACKEND_URL` | step 6 | ... / `https://....azurecontainerapps.io` |
| Managed identity client ID | step 7 | ... |

**Steps**

- [ ] **0. Merge and publish the image.** Pull request `feature/function-box-implementation` → `main`; CI must pass (backend tests, frontend build, Docker build + image check). Merge. The push publishes `ghcr.io/kaushiksangmuli/java-visualizer/backend:<sha>` (deploy jobs skip — expected). Set the GHCR package visibility to Public (or add registry credentials later).
- [ ] **1. Resource group.** `rg-java-visualizer` in one region (e.g. Central India); use it for everything.
- [ ] **2. Azure SQL Database (free offer).** Apply the free offer; database `javavisualizer`; new server with SQL authentication; free-limit behaviour **Auto-pause until next month**; networking: allow Azure services. Tables are created by Flyway on first backend start (V1–V4).
- [ ] **3. Brevo.** Free account, verify the sender address, create an API key.
- [ ] **4. JWT secret.** Generate 32+ random characters (PowerShell command in `DEPLOYMENT.md`).
- [ ] **5. Static Web App (Free).** Deployment source **Other**; record `FRONTEND_URL`; copy the deployment token.
- [ ] **6. Container App (Consumption).** Image from step 0, ingress external on port 8080, 1 vCPU / 2 Gi, min 0 / max 1 replicas; secrets `db-password`, `jwt-secret`, `brevo-api-key`; env vars `SPRING_PROFILES_ACTIVE=azure`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `VISUALIZER_JWT_SECRET`, `VISUALIZER_BREVO_API_KEY`, `VISUALIZER_EMAIL_SENDER`, `CORS_ALLOWED_ORIGINS=<FRONTEND_URL>`; probes `GET /health`. Record `BACKEND_URL`; check `BACKEND_URL/health` → `{"status":"ok"}`.
- [ ] **7. GitHub → Azure access (OIDC).** User-assigned managed identity with a federated credential for repo `KaushikSangmuli/java-visualizer`, environment `production`; Contributor on the resource group. GitHub environment `production`. Repository variables `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_RESOURCE_GROUP`, `AZURE_CONTAINERAPP_NAME`, `BACKEND_URL`; secret `AZURE_STATIC_WEB_APPS_API_TOKEN`.
- [ ] **8. First deploy and smoke test.** Actions → CI/CD → Run workflow on `main`. Then in Chrome/Edge: open `FRONTEND_URL`, sign up, receive the emailed code, verify, sign in, run "Nested loops with a function call", check `GET /api/runs` count increases.
- [ ] **9. Guardrails.** Budget with alerts at 50 % / 100 %; check the SQL free allowance after a week; rehearse rollback (activate previous revision or redeploy the previous image tag).
- [ ] **10. Afterwards.** Review the 4 Maven warnings; decide on deleting commented-out frontend code; custom domain (fixes Safari/iOS login); Stage 7 production-readiness review before inviting the public.

**After the first deploy, every merge to `main` redeploys automatically** (tests → image → Container App → health check → frontend). To require approval first, add a required reviewer to the GitHub environment `production`.
