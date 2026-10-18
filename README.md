# Java Code Memory Visualizer

An interactive learning tool for Java and data structures. The backend compiles and traces Java source in a separate debuggee JVM. The React frontend lets you submit code, step through the trace, and inspect stack frames, variable values, heap objects, static fields, and console output.

## Project layout

```text
java-visualizer/
├── backend/                 Spring Boot + Maven Java API
├── frontend/                React + Vite web application
├── reference_for_frontend/  Original UI reference, preserved unchanged
├── backend/documentation/   Backend product/technical document and staged plan
├── frontend/documentation/  Frontend product/technical document and staged plan
└── .gitignore
```

Backend planning and change history are in `backend/documentation/`; frontend planning and change history are in `frontend/documentation/`. The Java project is in `backend/`; its physical packages remain under `backend/src/main/java/com/example/demo/...`. The reference project guided the editor, step navigation, call-stack, memory, and output layout. Its C++ execution logic and embedded third-party API credentials were not copied. The live UI accepts Java source and calls this project's API.

## Prerequisites

- JDK 21 (the backend targets Java 17 bytecode) and Maven 3.8+
- Node.js 20.19 or newer and npm
- A modern browser

## Run locally

Start the backend in one terminal:

```powershell
cd "C:\path\to\java-visualizer\backend"
mvn spring-boot:run
```

Start the frontend in a second terminal:

```powershell
cd "C:\path\to\java-visualizer\frontend"
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api/visualize` and `/health` to the backend on port 8080 during development. Keep the backend running while using the UI.

For a production frontend bundle, run `npm run build` from `frontend/`; Vite writes the generated bundle to `frontend/dist/`.

## Eclipse backend import

Import only the Maven project under `backend/`:

1. In Eclipse, select **File → Import → Maven → Existing Maven Projects**.
2. Choose the `java-visualizer/backend` folder containing `pom.xml`.
3. Ensure JDK 21 is configured under **Java → Installed JREs** and the project compiler compliance is Java 17.
4. Run **Maven → Update Project**.
5. Run `VisualizerApplication.java` as a Spring Boot application.

The React application is a separate project in `frontend/` and can be edited in a JavaScript-capable editor.

## API

- `GET /health` returns `{"status":"ok"}`.
- `POST /api/visualize` accepts `{"code":"<Java source>"}` and responds with the trace, stack, variables, heap/static fields, stdout, and handled errors. The frontend displays these fields as each trace step is selected.

Trace responses also include source metadata, line classifications, variable and heap deltas, and a final `end` event. The backend applies a configurable AST API blocklist, per-client rate limit, and concurrency limit. These are guardrails rather than a secure Java sandbox. See `backend/README.md` for Docker restrictions and runtime settings.

## Accounts (phase two)

The backend now includes email/password signup, OTP verification, JWT-cookie login/logout, and password reset. The frontend opens on login with a signup link; authenticated users then reach the visualizer. Local development defaults to a persistent H2 database in `backend/data/` and accepts OTP `1234` without email. Use the `local-sqlserver` profile to connect to local SQL Server using its migrations and the same OTP bypass. Other profiles require a Brevo API key and verified sender. For Azure deployment, activate the `azure` Spring profile, set Azure SQL connection variables, and provide `VISUALIZER_JWT_SECRET` (at least 32 UTF-8 bytes). Never place credentials in frontend code or commit them; do not deploy with a local profile.

The frontend uses the backend's HttpOnly JWT cookie and CSRF token. The backend requires authentication for `/api/visualize`. In cloud builds, set `VITE_API_BASE_URL` to the backend origin and configure backend `CORS_ALLOWED_ORIGINS` to the exact frontend origin. See `backend/README.md` for environment variables and endpoints. The frontend production build passed when written to a permitted temporary location; writing to the project `dist` directory is blocked by this environment's Windows permissions. Backend compilation and end-to-end authentication have not been verified because Java and Maven are unavailable in this coding shell.

## Phase-two services and CI/CD plan

The selected low-cost direction is Azure Static Web Apps Free for the frontend, Azure Container Apps Consumption for the backend, Azure SQL Database Free offer for persistence, and Brevo Free for OTP email. Free limits and creation-time settings are summarized in [`plan.markdown`](plan.markdown). GitHub Actions is planned in `.github/workflows/ci-cd.yml`: validate pull requests, then deploy both applications after a successful push to `main`. This workflow and cloud resources have not yet been created.

## Scope and safety

The current interface supports Java source submission, sample loading, execution, line highlighting, trace playback/step controls, stack and variable inspection, heap/static-field inspection, compile/runtime/blocked API display, state changes, stdout, and raw response inspection. Arbitrary Java execution is not safe without proper OS sandboxing; use trusted code locally only.
