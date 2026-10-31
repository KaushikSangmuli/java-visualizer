# Java Code Memory Visualizer — Project Document

## 1. Product Documentation

### Product overview

**Java Code Memory Visualizer — Python Tutor for Java and DSA** is a learning application for tracing Java programs. The Spring Boot backend parses and checks submitted source, compiles it, runs it in a separate debuggee JVM, and returns a JSON execution trace enriched with source metadata and state changes. Learners first see a login page, with a signup link for new accounts; verified users access the visualizer using a short-lived JWT in an HTTP-only cookie. Account and OTP challenge data are stored in the configured database, and visualization requests require authentication.

### Product goal

Help a learner understand how a Java or data-structure program executes by showing the current source line and changing execution state. The frontend and backend are separate projects in one workspace; the UI consumes the existing API contract.

### Users and primary flow

A learner selects a sample or enters Java source in the editor and chooses **Run & visualize**. The frontend sends the source to `POST /api/visualize` through the Vite development proxy. The service validates and compiles it, starts it under the Java Debug Interface in a separate process, and returns a JSON result. The learner steps or plays through the trace to inspect source lines, frames, values, heap/static fields, and output. The UI displays compilation failures, runtime exceptions, and truncation states. `GET /health` reports backend availability.

### API contract

- `GET /health` returns `{"status":"ok"}`.
- `GET /api/auth/csrf` initializes the CSRF cookie/token required by auth-changing requests.
- `POST /api/auth/signup` accepts `{ "email", "password" }`; `POST /api/auth/verify-email` accepts `{ "email", "code" }`.
- `POST /api/auth/login` accepts `{ "email", "password" }` and creates a short-lived HTTP-only JWT cookie; `GET /api/auth/me` returns the signed-in email; `POST /api/auth/logout` clears the cookie.
- `POST /api/auth/verification-code/resend`, `/api/auth/password-reset/request`, and `/api/auth/password-reset/complete` support verification resend and OTP password recovery. Auth-changing browser calls must send the `X-XSRF-TOKEN` header obtained from the CSRF endpoint.
- Authenticated `GET /api/runs` returns the signed-in user's run history: `totalRuns`, `successfulRuns`, `failedRuns` and `recentRuns` (20 newest: `createdAt`, `outcome` SUCCESS/FAILED, `status`, `message`, `errorDetail`, `stepCount`, `truncated`, `codeLength`, `durationMs`). Every `POST /api/visualize` by a signed-in user is recorded; the source code itself is not stored.
- Local `local` and `local-sqlserver` profiles only: signup and password reset use fixed OTP `1234`, without email delivery. Hosted `azure` profile has no fixed OTP and requires Brevo settings and `VISUALIZER_JWT_SECRET`.
- Authenticated `POST /api/visualize` accepts JSON with a `code` field, for example:

  ```json
  {"code":"public class Main { public static void main(String[] args) { System.out.println(\"Hello\"); } }"}
  ```

- The response uses camelCase fields, including `status`, `message`, `errors`, `trace`, `truncated`, `truncatedReason`, `exception`, and `stdout`.
- The defined status values are `ok`, `compile_error`, `runtime_error`, `blocked_api`, `busy`, and `error`. The controller returns the service response for handled visualization outcomes.
- A trace event contains a zero-based step index, source line, event name, stdout captured up to that event, `frames`, an `explanation`, `returnValue` where applicable, and a bounded `heap` snapshot. Each frame contains a method/class/return type/source line and visible variables; frames are ordered active method first, then callers. Heap values use `referenceId` to link frame/static/field/array references to identity-deduplicated heap objects.
- Successful responses also contain JavaParser-derived `metadata` (methods, loops, branches); trace events expose `lineType`, `variableChanges`, and `heapChanges` with object-field/array-index before-and-after values, ending with an `end` event. Blocked source includes `blockedApis` with API names, reasons, and source lines.
- Each frame also has `calls` and `loops`. `calls` lists calls the frame made on its current line to user-written methods/constructors: `line`, `expression` (values, e.g. `result = fun_add(0, 4)` or after return `result = 4 <= fun_add(0, 4)`), `state` (`evaluating`/`returned`), `returnValue`, and `sourceExpression` (as written, e.g. `result = fun_add(result, number)`). `loops` lists the loops the frame is running, nested inner-inside-outer: `kind`, `startLine`, `endLine`, `headerLine`, `condition`, `conditionValues` (e.g. `2 < 4`, for-each `2 of 5`), `conditionResult` (true/false only while the condition is checked), `iteration` (0 before the body first runs), `activationStep` (identifies one run of the loop), `variables` (names declared by the loop), and nested `loops`.

### Current planned scope

- Reject empty source and enforce a configurable source-length limit.
- Locate a supported `main` method, including nested classes and accepted argument-array spellings.
- Compile submitted code with the JDK compiler API and return diagnostics on compilation failure.
- Trace line events in a separate JVM using JDI, with configurable step, output, and runtime limits.
- Return captured stdout and uncaught exception details.
- Remove the per-request temporary compilation directory when processing finishes.
- Capture user-visible stack frames, parameters, visible locals, and `this` where applicable.
- Emit method entry (`call`) and exit (`return`) events with explanations and the return value when supported by the debuggee VM.
- Limit each displayed stack snapshot to at most 50 frames.
- Traverse heap objects reachable from visible frame values and user-class static fields; represent arrays, collection internals, user objects, aliases, and cycles without recursive JSON expansion.
- Include static fields declared by user classes and report partial snapshots when configured object, depth, or array-element limits are reached.
- Parse source into method/loop/branch metadata and detect restricted APIs with an AST-based defense-in-depth blocklist.
- Enrich trace events with source line types, loop/branch explanations, variable and heap changes, and a final end event.
- Bound concurrent traces and per-client request rates, configure CORS, run as a non-root container user, and document container restrictions.
- Cover parsing, source handling, metadata, enrichment, and representative API integration scenarios with JUnit 5.
- Attach function-call rows to caller frames: every waiting caller keeps its row, returned rows stay until the caller leaves the line, nested/repeated calls on one line are listed in evaluation order, constructors show the created object, and library calls are ignored.
- Attach running loops to frames with iteration number, condition values and verdict, loop-declared variables and nesting; variable-change keys number frames from `main` so calls do not report outer variables as changed.

The Striver’s DSA sheet, premium company-wise lists, and LeetCode question browser are future product scope recorded in the plan and are not part of this implementation request.

### Product limitations and safety

The trace provides source-line events, stack/variable snapshots, method boundaries, return values, bounded heap graphs, user-class static fields, source classifications, and state deltas. Account signup/login uses email OTP, BCrypt password hashes, short-lived JWT cookies, CSRF protection, and configurable CORS. The local profile uses the fixed OTP `1234` only to unblock development without email; never activate the local profile for a hosted deployment. User programs run in a child JVM started with the Java Security Manager and a per-run policy (`sandbox.policy` next to the compiled classes): they may read only their own classes and a short list of harmless system properties — no environment variables (database password, JWT secret and Brevo key live there), no other files, no network, no processes, no reflection on private members. The AST blocklist (including `getenv` and method references) rejects obvious misuse first; timeouts, step/output limits, per-user rate limits, a concurrency cap and a non-root container bound resource use. CPU/memory are not isolated per user within one replica, so a public deployment should start as a private beta.

## 2. Technical Flow and Configuration

### Runtime flow

#### Frontend request and display flow

1. `frontend/src/App.jsx` holds the editable source, selected sample, backend health state, returned response, and selected trace index.
2. `frontend/src/components/CodeEditor.jsx` renders a line-numbered editor and highlights the active trace line; selecting a sample loads Java source from `frontend/src/examples.js`.
3. **Run & visualize** sends `{ "code": "..." }` to `/api/visualize`. In development, `frontend/vite.config.js` proxies `/api` and `/health` to `http://localhost:8080`; this avoids a separate CORS configuration.
4. The selected trace event drives the line indicator, source context, method stack, visible variables, heap/static-field view, return value, and console output. Reference IDs navigate from a variable/field to its heap object. The raw response can be expanded for inspection.

#### Backend flow

1. Spring Boot starts `backend/src/main/java/com/example/demo/VisualizerApplication.java` in the root package `com.example.demo`, allowing component scanning across its subpackages. Security, JPA, and Flyway are configured by the same application context; the `local` default profile selects H2 and the local-only OTP, `local-sqlserver` selects local SQL Server plus that OTP, and `azure` selects SQL Server without the OTP and requires a hosted JWT secret.
2. `VisualizeController` handles `GET /health` and accepts the optional `VisualizeRequest` body on `POST /api/visualize`; it passes the remote address to request admission.
3. `VisualizeService.visualize` rejects missing/blank or over-limit source. `SourceHelper.strip` masks comments and literals while preserving source positions; `SourceHelper.findMain` identifies the binary launch class and Java filename. `CodeInspector.inspect` uses JavaParser to produce method/loop/branch metadata and AST-based blocked API findings.
4. `RequestAdmissionService.acquire` applies the configured per-client window and concurrency semaphore. Rejected requests return a handled `busy` response. Accepted requests create a unique temporary directory with `Files.createTempDirectory`, write source beneath `src/`, and blank a package declaration without shifting line numbers.
5. `CompilerService.compile` obtains `ToolProvider.getSystemJavaCompiler()`, creates the classes directory, and compiles in-process with debug metadata, UTF-8 encoding, annotation processing disabled, Java 17 API/bytecode targeting, and output directed to the temporary classes directory. Compilation diagnostics become model records.
6. `Tracer.trace` launches the compiled main class with the JDI `CommandLineLaunch` connector. The user program runs in a separate JVM with bounded heap/stack settings, suspended startup, closed standard input, and stdout/stderr drains.
7. JDI class-prepare handling installs a breakpoint at the main method. The tracer records the first breakpoint as a `call` step, then recreates count-filtered line-step requests. Method entry/exit requests capture user method boundaries; exit return values are mirrored through JDI when supported. At each event, the tracer reads up to 50 user frames and their visible variables while the debuggee is suspended. It walks reachable object fields/array elements and static fields declared by loaded user classes using JDI mirrors, deduplicating by object identity to preserve aliases/cycles. It does not invoke debuggee methods.
8. Uncaught exceptions are reported using debuggee frame information and the exception's stored message field. When execution ends or a configured limit is reached, the tracer stops the debuggee as needed and returns its trace/result.
9. `TraceEnricher.enrich` first runs `FunctionCallTracker` (call sites from `CallSiteAnalyzer`) and `LoopTracker` (loop sites from `LoopSiteAnalyzer`) over the raw steps in order, then adds source line classifications, approximate loop/branch explanations, consecutive variable and heap deltas, and a final `end` event. The service always removes temporary files and releases its admission permit in `finally`.
10. After each visualization, `VisualizeController` passes the response to `RunHistoryService.record`, which looks up the signed-in account and saves a `visualization_run` row (`RunOutcome.from` decides SUCCESS/FAILED and builds the failure reason: compiler errors line by line, exception type/line/message, blocked APIs, trace limit, or the busy/error message). Recording failures are logged and never break the visualization response. `GET /api/runs` reads only the caller's rows.
11. Auth endpoints create accounts and persist hashed one-time verification/reset challenges. The local profile stores a BCrypt hash of the fixed `1234` code and skips email delivery; other profiles generate random six-digit codes and send them through Brevo. Login authenticates verified accounts with BCrypt and issues a signed HS256 JWT in an HTTP-only cookie. A JWT cookie filter restores request authentication; Spring Security is stateless, CSRF remains enabled for cookie-authenticated mutations and accepts the plain token fetched by the frontend, and visualization requests require a valid JWT. Login attempts and challenge retries are bounded. Password reset does not revoke already issued JWTs; they expire within 30 minutes.

### Project layout

```text
java-visualizer/
├── backend/
│   ├── pom.xml
│   ├── README.md
│   ├── samples/
│   └── src/main/java/com/example/demo/...
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   └── src/ (React UI and Java examples)
├── reference_for_frontend/ (provided visual reference, kept separate)
├── README.md
├── document.markdown
├── plan.markdown
├── .gitignore
└── (reference assets are not part of the build)
```

Java package names map to real nested directories such as `backend/src/main/java/com/example/demo/service`; the dots are not a literal directory name. The frontend is an independent React/Vite project and does not change Java package structure.

### Build and runtime configuration

- Maven project; Spring Boot parent `3.2.5`; Spring Web provides the HTTP API and Jackson JSON support. JavaParser core supplies AST inspection; JUnit 5 and MockMvc arrive through `spring-boot-starter-test`.
- JDK 21 is the intended runtime/compiler installation; Java source and target bytecode are configured for Java 17.
- `maven.compiler.release` remains intentionally empty; `maven.compiler.source` and `maven.compiler.target` are `17`, preserving access to JDI APIs from the full JDK during compilation.
- Compilation uses `javax.tools.JavaCompiler` with `-g`, `-encoding UTF-8`, `-proc:none`, `--release 17`, and `-d <classesDir>`.
- JDI is provided by the JDK's `jdk.jdi` module. The debuggee runs separately with `-Xmx128m`, `-Xss1m`, UTF-8 encoding properties, and a classpath pointing at compiled temporary classes.
- Run Maven commands from `backend/`: `mvn -q clean compile` and `mvn spring-boot:run`.
- The frontend uses React 19 and Vite 8. Run `npm install` and `npm run dev` from `frontend/`; Node.js 20.19+ is required by the selected Vite version. The Vite development proxy forwards `/api` and `/health` to port 8080.

### `application.properties`

| Property | Default | Purpose |
|---|---:|---|
| `server.port` | `8080` | HTTP port |
| `visualizer.max-code-length` | `20000` | Maximum submitted source length in characters |
| `visualizer.max-steps` | `2000` | Maximum recorded line steps |
| `visualizer.max-stdout-chars` | `20000` | Maximum captured stdout characters |
| `visualizer.timeout-ms` | `10000` | Hard execution deadline in milliseconds |
| `visualizer.max-displayed-frames` | `50` | Maximum user frames included in each trace event |
| `visualizer.max-heap-objects` | `500` | Maximum object nodes in each event's heap graph |
| `visualizer.max-array-elements` | `100` | Maximum elements captured per array |
| `visualizer.max-heap-depth` | `8` | Maximum reference depth expanded from a frame/static root |
| `visualizer.max-concurrent` | `4` | Maximum simultaneous debuggee sessions |
| `visualizer.rate-limit-requests` | `60` | Requests per client within the fixed time window |
| `visualizer.rate-limit-window-ms` | `60000` | Per-client rate-limit window |
| `visualizer.rate-limit-max-clients` | `10000` | Maximum tracked client windows |
| `visualizer.cors.allowed-origins` | local Vite origins | Comma-separated browser origins allowed by CORS |
| `visualizer.auth.login-max-failures` | `10` | Login failures allowed before a client is throttled |
| `visualizer.auth.login-window-ms` | `900000` | Login failure throttle window |
| `visualizer.auth.jwt-ttl-seconds` | `1800` | JWT cookie lifetime |
| `visualizer.auth.cookie-secure` | local `false`, hosted `true` | Requires HTTPS when enabled |
| `visualizer.auth.cookie-same-site` | local `Lax`, hosted `None` | Browser cookie cross-site policy (auth and CSRF cookies) |
| `visualizer.trust-forwarded-for` | local `false`, hosted `true` | Use the last `X-Forwarded-For` entry (appended by the Container Apps ingress) as the client address for login throttling |

Local database defaults to file-backed H2 under `backend/data/` with the `local` default profile. The H2 URL uses compatibility mode `MSSQLServer` (H2's exact mode name). `local` and `local-sqlserver` accept OTP `1234`; the latter uses SQL Server migrations and `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Local profiles generate an ephemeral JWT signing key at startup, so local JWTs become invalid after a backend restart. Use `SPRING_PROFILES_ACTIVE=azure` with database variables and a strong `VISUALIZER_JWT_SECRET` (minimum 32 UTF-8 bytes) for Azure SQL. OTP mail uses `VISUALIZER_BREVO_API_KEY`, `VISUALIZER_EMAIL_SENDER`, and optional `VISUALIZER_EMAIL_SENDER_NAME`. Configure `CORS_ALLOWED_ORIGINS` with exact frontend origins. For hosted HTTPS, keep `AUTH_COOKIE_SECURE=true`; use `AUTH_COOKIE_SAME_SITE=None` only when frontend and backend are on different sites, otherwise `Lax` is preferred. The frontend build accepts `VITE_API_BASE_URL` for the backend origin.

### Verification state

**Status as of 2026-10-31:** Stages 1–8 implemented; function-call boxes and loop boxes implemented on branch `feature/function-box-implementation`. The new call/loop code was compiled with JDK 21 against JavaParser 3.17 and run with the real `Tracer` on several programs outside Spring; its JUnit tests passed under a stand-in runner. `mvn clean verify` with the project's real dependencies is still pending on the developer machine.

Phase-two authentication code is present but has not been built or exercised in this turn. Local development uses a persistent H2 database under ignored `backend/data/`; deploy with the `azure` Spring profile to use Azure SQL. The local OTP bypass is unavailable in the `azure` profile. See `backend/README.md` for environment variables and auth endpoints.

The current task shell cannot resolve `java`, `javac`, `mvn`, or `npm.cmd`; it has Node.js 24.19.0. The authentication changes therefore have not been compiled, started, or browser-tested here. Earlier user-reported trace runs apply to the previous backend state, not to these authentication changes. Run Maven and the frontend build locally after configuring the database and Brevo email credentials.

## 3. Changes Made (Append Only)

### 2026-10-18 — Require JWT authentication and add a local OTP bypass

**Technical analysis:** Replaced server-side login sessions with stateless authentication backed by short-lived signed JWTs in an HttpOnly cookie. CSRF remains enabled because the browser sends the JWT cookie automatically. `/api/visualize` now requires an authenticated request. Local signup and password-reset challenges use the fixed code `1234` and do not call email delivery; outside the local profile, OTPs remain random six-digit values delivered through Brevo. Production configuration requires an external signing secret and does not enable the bypass.

**Files and methods changed:**

- `src/main/java/com/example/demo/config/SecurityConfiguration.java` — `securityFilterChain` switches to stateless authentication, adds the JWT cookie filter, protects visualization, and keeps CSRF on.
- `src/main/java/com/example/demo/config/JwtConfiguration.java` — `jwtSecretKey`, `jwtEncoder`, and `jwtDecoder` validate/configure HS256.
- `src/main/java/com/example/demo/config/JwtCookieAuthenticationFilter.java` — `doFilterInternal` and `authCookie` verify and read the HttpOnly JWT cookie for each request.
- `src/main/java/com/example/demo/service/auth/JwtTokenService.java` — `writeAuthCookie`, `clearAuthCookie`, and `cookie` create and expire short-lived auth cookies.
- `src/main/java/com/example/demo/controller/auth/AuthController.java` — `signup`, `login`, `logout`, `requestReset`, and OTP request records return local OTP guidance and issue/clear JWT cookies.
- `src/main/java/com/example/demo/service/auth/AuthService.java` — `sendChallenge` chooses the profile OTP and skips the email provider only in local mode.
- `pom.xml`, `src/main/resources/application.properties`, `application-local.properties`, `application-local-sqlserver.properties`, and `application-azure.properties` — configure JWT support, local OTP modes, and hosted secret/cookie requirements.
- `documentation/plan.markdown` and frontend documentation files — update the auth plan/flow and verification status.

**Verification:** Pending. Maven compile/startup, database migration, OTP lifecycle, and browser integration have not yet been verified in this turn.

### 2026-10-18 — Add phase-two email authentication foundation

**Technical analysis:** Added persistent account and one-time challenge storage, Flyway migrations for local H2 and Azure SQL, JDBC-backed servlet sessions, BCrypt password hashing, email OTP verification and password reset through Brevo, CSRF-protected session login/logout, a per-IP login-failure throttle, and exact-origin credentialed CORS. Existing `/health` and anonymous `/api/visualize` behavior is preserved. Authenticated browser state is kept in HttpOnly sessions; mutation requests require the CSRF token. Azure credentials are externalized, and local database files are ignored.

**Files and methods changed:**

- `src/main/java/com/example/demo/model/auth/UserAccount.java` — `UserAccount`, `isEnabled`, and password/verification state.
- `src/main/java/com/example/demo/model/auth/EmailChallenge.java` — challenge expiry, attempts, consumption, and resend timing.
- `src/main/java/com/example/demo/repository/auth/UserAccountRepository.java` and `EmailChallengeRepository.java` — email lookup and latest challenge persistence.
- `src/main/java/com/example/demo/service/auth/AuthService.java` — `signup`, `verifyEmail`, `resendSignupCode`, `requestPasswordReset`, `resetPassword`, `latestUsable`, `sendChallenge`, and UTF-8 password length validation.
- `src/main/java/com/example/demo/service/auth/EmailDeliveryService.java` — `sendCode` via Brevo transactional email API.
- `src/main/java/com/example/demo/service/auth/AuthRateLimiter.java` — `isAllowed`, `failed`, and `succeeded` login throttling.
- `src/main/java/com/example/demo/config/SecurityConfiguration.java` — password encoder, authentication manager, CSRF and route policy.
- `src/main/java/com/example/demo/config/WebConfiguration.java` — credentialed CORS and CSRF request headers.
- `src/main/java/com/example/demo/controller/auth/AuthController.java` — CSRF bootstrap, signup, verification, login, current-user, session refresh, logout, resend, and reset endpoints.
- `src/main/java/com/example/demo/controller/auth/AuthExceptionHandler.java` — safe auth/email-service error responses.
- `pom.xml`, `src/main/resources/application.properties`, `application-azure.properties`, and `src/main/resources/db/migration/{h2,sqlserver}/` — dependencies, local/cloud connection profiles, and schema migrations.
- `backend/data/` in `.gitignore` — excludes local H2 database files.

**Verification:** Not run. The current coding shell does not have `java`, `javac`, or Maven on PATH. Database migrations, Spring startup, OTP delivery, and browser authentication still require local configuration and verification.

### 2026-10-18 — Keep JWT authentication stateless and match CSRF token requests

**Technical analysis:** Removed the password-reset dependency on Spring Session because authentication is stateless JWT and no server-side session storage is provisioned. Password-reset success therefore no longer queries missing session tables; existing JWTs remain valid until their 30-minute expiry. CSRF token request handling now accepts the plain token returned by the bootstrap endpoint and sent in the browser header.

**Files and methods changed:**

- `src/main/java/com/example/demo/config/SecurityConfiguration.java` — `securityFilterChain` configures `CsrfTokenRequestAttributeHandler` with the readable CSRF cookie repository.
- `src/main/java/com/example/demo/service/auth/AuthService.java` — constructor and `resetPassword` remove unused Spring Session repository injection and lookup.
- `pom.xml` — removes the unused `spring-session-jdbc` dependency.
- `src/main/resources/application.properties` — removes session JDBC properties.
- `documentation/document.markdown` and `documentation/plan.markdown` — update current auth and verification state; this analysis is appended.

**Verification:** `git diff --check` found no whitespace errors (only line-ending conversion notices). Maven, Java, and `javac` are not available in this shell; backend compilation and login/CSRF runtime behavior remain unverified.

### 2026-10-18 — Correct the local H2 SQL Server compatibility mode

**Technical analysis:** The user's Spring Boot startup log showed H2 2.2.224 rejecting `MODE=MSSQL` with `Unknown mode "MSSQL"`. H2 names this mode `MSSQLServer`; corrected the default local datasource URL. SQL Server JDBC profile configuration is unaffected.

**Files and methods changed:**

- `src/main/resources/application.properties` — default `spring.datasource.url` now selects H2 mode `MSSQLServer`.
- `backend/README.md`, `documentation/document.markdown`, and `documentation/plan.markdown` — describe the corrected mode and record verification status.

**Verification:** Confirmed the mode name against the H2 documentation. The backend could not be restarted in this coding environment because Java and Maven are unavailable; rerun `VisualizerApplication` in Eclipse to verify Flyway and JPA startup.

### 2026-10-17 — Initial project setup and this project document

**Product/technical analysis:** Created the initial Stage 1–2 Maven/Spring Boot project from the supplied workspace files. Migrated package declarations to `com.example.demo` and the `controller`, `model`, `service`, and `tracer` subpackages; added the missing request/response/trace/error model records; wired the compiler and tracer as Spring services; aligned execution limits with the requested properties; added project README, Maven/Eclipse setup guidance, `.gitignore`, and sample programs/requests. Existing originals in the workspace were preserved. The project has not been built or run because Java and Maven were unavailable, so runtime behavior and sample expectations remain unverified.

**Files and methods changed:**

- `src/main/java/com/example/demo/VisualizerApplication.java` — package declaration; retained `main` as the Spring Boot entry point.
- `src/main/java/com/example/demo/controller/VisualizeController.java` — package/import updates; retained `visualize` and `health` endpoint methods.
- `src/main/java/com/example/demo/service/VisualizeService.java` — package/import updates; constructor now injects `CompilerService` and `Tracer` and reads timeout/output settings; `visualize` uses those configured limits and carries truncation reason into the response; `deleteQuietly` retains temporary-directory cleanup.
- `src/main/java/com/example/demo/service/CompilerService.java` — package/import updates and Spring service registration; `compile` retains in-process Java compilation and diagnostics.
- `src/main/java/com/example/demo/service/SourceHelper.java` — package update; retained `strip`, `findMain`, and `blankPackageLine` logic.
- `src/main/java/com/example/demo/tracer/Tracer.java` — package/import updates and Spring service registration; `trace`, `Run.execute`, and `Run` constructor now accept the configured timeout and stdout cap; output capture uses that configured limit. Existing launch, event handling, and cleanup logic was retained.
- `src/main/java/com/example/demo/model/VisualizeRequest.java`, `VisualizeResponse.java`, `TraceStep.java`, `CompileError.java`, and `ExceptionInfo.java` — added DTO records because these files were absent from the supplied workspace.
- `pom.xml`, `src/main/resources/application.properties`, `README.md`, `.gitignore`, and `samples/*` — added/updated project configuration, documentation, hygiene rules, and sample inputs.
- `document.markdown` — added this product/technical/change record.

**Verification:** Package declarations were checked against physical source directories. Build and runtime checks were not run because the required executables were unavailable.

### 2026-10-17 — Fix compiler-reported stderr pump constructor mismatch

**Technical analysis:** The first Maven compile reported that the stderr `OutputPump` call supplied two arguments although the updated constructor requires the stream, capture flag, and character limit. The stderr stream is drained but intentionally not captured, so the call now passes `0` as its unused capture limit. This resolves the signature mismatch without changing stdout capture or trace behavior.

**Files and methods changed:**

- `src/main/java/com/example/demo/tracer/Tracer.java` — `Run.execute` now constructs the stderr pump with `new OutputPump(process.getErrorStream(), false, 0)`.
- `document.markdown` — appended this analysis to the change history.

**Verification:** The Maven build could not be rerun from the command environment because Maven is unavailable on PATH. The previous Eclipse Maven build had reached compilation and reported the constructor mismatch. Rerun `mvn -q clean compile` in Eclipse or PowerShell to verify the fix.

### 2026-10-17 — Implement Stage 3 stack and method snapshots

**Technical analysis:** Extended each trace event with a stack snapshot ordered from the active method to its callers. While the debuggee is suspended, the tracer reads method/class/return-type/line metadata, visible local variables and parameters, and `this` for instance methods. Method-entry and method-exit requests generate `call` and `return` events with explanations; the return value is read from JDI only when the target VM reports support. Object values are represented with safe labels rather than invoking application methods. Frame snapshots are capped at 50, with a configurable limit clamped to that maximum. This does not add heap expansion or static-field snapshots (Stage 4).

**Files and methods changed:**

- `src/main/java/com/example/demo/model/TraceStep.java` — added `frames`, `explanation`, and `returnValue` response fields.
- `src/main/java/com/example/demo/model/FrameSnapshot.java` and `VariableSnapshot.java` — added records for method frames and visible values.
- `src/main/java/com/example/demo/tracer/Tracer.java` — `trace` and `Run` accept the displayed-frame limit; `Run.execute` registers method entry/exit requests; `onBreakpoint`, `onMethodEntry`, `onMethodExit`, `onStep`, and `onException` record enriched events; `snapshotFrames` and `displayValue` build safe snapshots.
- `src/main/java/com/example/demo/service/VisualizeService.java` and `src/main/resources/application.properties` — added `visualizer.max-displayed-frames` and passed it into tracing.
- `plan.markdown`, `README.md`, and `document.markdown` — added the staged plan and updated current scope/technical flow; this entry is appended to the change history.

**Verification:** Compiled all main Java source files using JDK 21 `javac` with `-source 17 -target 17` and cached Maven dependencies. Compilation succeeded with the warning that the system modules path is not set with `-source 17`. The live Stage 3 API response has not been run; restart the Eclipse application with the updated classes before checking method/variable snapshots.

### 2026-10-17 — Confirm Stage 3 Factorial runtime response

**Technical analysis:** The user submitted `samples/factorial.json` after restarting the Eclipse application and provided the API response. The trace includes `main` and nested recursive `fact` frames, parameter values `n = 3`, `2`, and `1`, `return` events with values `1`, `2`, and `6`, the caller local `result = 6`, and final stdout `fact(3) = 6`. This confirms the Stage 3 call-stack and return-value path works for this sample. It does not verify all Java constructs, nested-class tracing, or stack truncation above 50 frames.

**Files and methods changed:**

- `plan.markdown`, `README.md`, and `document.markdown` — updated Stage 3 status and verification evidence; appended this change record. No Java implementation changed for this verification update.

**Verification:** Confirmed from the user's live API response. The shell sandbox attempts did not prevent the implementation: a JDK 21 `javac` compile of all main sources succeeded with Java 17 source/target settings. The command environment still does not expose Maven on PATH, so a Maven CLI build was not run there.

### 2026-10-17 — Implement Stage 4 heap and static-field snapshots

**Technical analysis:** Added a per-trace-event heap graph rooted at visible frame values and static fields declared by loaded user classes. Objects are keyed by JDI unique IDs so aliases and cycles resolve to the same graph node rather than recursive JSON. Primitive and string values remain readable values; other object references carry `referenceId` links to heap nodes. Arrays include their total length and a bounded prefix of elements. `java.util` objects are marked as collections and represented through their JDI-visible fields and nested arrays/nodes; this reads collection storage without invoking collection methods. Static fields are read directly through JDI mirrors. The snapshot reports when configured object-count, traversal-depth, or array-element limits produce a partial graph. Defaults are 500 objects, depth 8, and 100 elements per array. Added model records for memory values, heap objects/fields, static fields, and the aggregate heap snapshot. No Stage 5–8 behavior was added.

**Files and methods changed:**

- `src/main/java/com/example/demo/model/TraceStep.java` — added the `heap` event field.
- `src/main/java/com/example/demo/model/VariableSnapshot.java` — added nullable `referenceId` for linking visible object references.
- `src/main/java/com/example/demo/model/MemoryValue.java`, `HeapFieldSnapshot.java`, `HeapObjectSnapshot.java`, `StaticFieldSnapshot.java`, and `HeapSnapshot.java` — added the structured Stage 4 snapshot model.
- `src/main/java/com/example/demo/tracer/Tracer.java` — `trace` and `Run` accept heap limits; `record` captures heap state for each event; `snapshotFrames` adds variable reference IDs; `snapshotHeap` collects frame/static roots; `HeapBuilder.enqueue`, `value`, `expand`, `markTruncated`, and `kind` walk/deduplicate objects, arrays, and collection internals without running debuggee methods.
- `src/main/java/com/example/demo/service/VisualizeService.java` and `src/main/resources/application.properties` — added and passed `visualizer.max-heap-objects`, `visualizer.max-array-elements`, and `visualizer.max-heap-depth`.
- `plan.markdown`, `README.md`, and the product/technical sections of `document.markdown` — described Stage 4 scope, response shape, configuration, and verification state; this change entry is appended.

**Verification:** `mvn -q clean compile` could not run because `mvn` is not available on PATH in this shell. A direct JDK 21 compile with Java 17 source/target settings was attempted and caught an unsupported `VirtualMachine.objectByID` call; the code was corrected to root from live suspended frames. Rerun the direct compile and use Eclipse Maven Update/Build to confirm the current version. Stage 4 runtime behavior remains unverified; do not infer a successful build or trace until those checks pass.

### 2026-10-17 — Stage 4 compile verification update

**Technical analysis:** The initial direct compiler attempt exposed and led to removal of the unsupported `VirtualMachine.objectByID` call. The next attempt compiled the source but the JDK compiler emitted an archive-close `AccessDeniedException` for an unrelated Maven plugin JAR in the local cache, so that result was treated as inconclusive. A retry outside the sandbox, excluding Maven plugin JARs from the application classpath, completed successfully. The code compiles with the installed JDK 21 using Java 17 source/target settings. The project has not been launched with the new classes, so Stage 4 runtime snapshots remain unverified. Maven CLI remains unavailable on PATH.

**Files and methods changed:** No implementation changed during this verification update. The product/technical verification summaries in `plan.markdown`, `README.md`, and the first two sections of `document.markdown` now record the compile result. This entry is appended to the change history.

**Verification:** Direct `javac` compilation of every file under `src/main/java` succeeded with exit code 0. The compiler emitted only the expected warning that the system modules path was not set with `-source 17`. `mvn -q clean compile`, starting Spring Boot, and submitting a Stage 4 request were not completed from this shell.

### 2026-10-17 — Confirm Stage 4 response shape with Factorial

**Technical analysis:** The user ran `samples/factorial.json` against the updated Eclipse application. The response has a `heap` object on all 19 events, represents the `args` reference as an empty `java.lang.String[]` node (`length: 0`), and shows primitive variables with `referenceId: null`. This verifies serialization and basic heap snapshot integration in the live request path. Factorial uses primitive locals and does not exercise user-defined object fields, user static fields, non-empty arrays, or collections; those Stage 4 behaviors remain to be checked separately.

**Files and methods changed:** No Java implementation changed for this verification. The Stage 4 status/verification descriptions in `plan.markdown`, `README.md`, and the first two sections of `document.markdown` were updated, and this record was appended to the change history.

**Verification:** Confirmed from the user's live `POST /api/visualize` response for `samples/factorial.json`; response status was `ok`, all trace events contained a heap snapshot, and the existing Factorial stdout and return values were present.

### 2026-10-17 — Separate backend documentation from frontend documentation

**Technical analysis:** Split the shared project documentation into backend-specific and frontend-specific product documents and stage plans. The backend product and technical sections now describe only the Java API, compiler, JDI tracer, configuration, and backend verification. Backend stages 1–8 remain intact. The frontend has its own staged plan and append-only technical change history. No Java source, API contract, or backend runtime configuration changed.

**Files changed:**

- `backend/documentation/document.markdown` — backend-only product/technical sections; retained prior backend change entries and appended this organizational change.
- `backend/documentation/plan.markdown` — backend-only current status and stages 1–8.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — separate frontend product/technical documentation, history, and staged plan.
- Root `README.md`, `backend/README.md`, and `frontend/README.md` — point to the documentation locations.

**Verification:** Confirmed the four documentation files exist in their backend/frontend folders. No build was run for this documentation-only change.

### 2026-10-17 — Record future DSA problem-library product scope

**Technical/product analysis:** Added three post-completion product ideas to the backend plan: a Striver’s DSA sheet selector, premium company-wise question collections, and LeetCode problem-number selection with Java dry runs. These are explicitly future scope after the existing backend and frontend stages. No backend behavior, routes, or source code changed. The future work will need a permitted problem-content source and an entitlement decision for premium collections.

**Files changed:**

- ackend/documentation/plan.markdown — appended a future product scope section after the planned backend stages.
- ackend/documentation/document.markdown — appended this change analysis.

**Verification:** Documentation-only update; no build or runtime behavior changed.

### 2026-10-17 — Implement backend Stages 5–8

**Technical analysis:** Added JavaParser-based source metadata for methods, loops, branches, declarations, and returns, plus an AST blocklist for process creation/termination, reflection, native loading, filesystem, and network entry points. The blocklist reports source locations as `blocked_api` and is not a sandbox. Added trace enrichment with line classifications, loop/branch explanations, variable changes, heap object changes, named field/array-element before/after values, and a final `end` event. Added bounded concurrent debuggee admission, per-client rate limiting, configurable CORS, a non-root full-JDK Docker image, and restrictive runtime instructions. Added JUnit tests for source/main handling, AST metadata/blocking, trace enrichment, request limits, and API integration.

**Files and methods changed:**

- `src/main/java/com/example/demo/service/CodeInspector.java` — `inspect`, `inspectImports`, `deniedImport`, `inspectCalls`, `inspectObjectCreation`, and `metadata` implement the AST inspection and blocklist.
- `src/main/java/com/example/demo/service/TraceEnricher.java` — `enrich`, `lineType`, `explanation`, `variables`, `variableChanges`, `heapObjects`, and `heapChanges` add per-event metadata and deltas.
- `src/main/java/com/example/demo/service/RequestAdmissionService.java` — `acquire`, `allowClient`, and `release` enforce request-window and concurrency bounds.
- `src/main/java/com/example/demo/config/WebConfiguration.java` — `addCorsMappings` reads configurable origins.
- `src/main/java/com/example/demo/service/VisualizeService.java` — `visualize` integrates inspection, admission, enrichment, and permit release.
- `src/main/java/com/example/demo/service/CompilerService.java` — `compile` additionally targets Java 17 APIs/bytecode for submitted programs.
- `src/main/java/com/example/demo/controller/VisualizeController.java` — `visualize` supplies the remote client address.
- `src/main/java/com/example/demo/model/CodeMetadata.java`, `MethodMetadata.java`, `SourceRange.java`, `BlockedApi.java`, `VariableChange.java`, `HeapChange.java`, `HeapValueChange.java`, `TraceStep.java`, and `VisualizeResponse.java` — added additive metadata/change fields and DTOs.
- `src/main/resources/application.properties`, `pom.xml`, `Dockerfile`, `.dockerignore`, `README.md`, `src/test/java/com/example/demo/service/*Test.java`, and `src/test/java/com/example/demo/integration/VisualizerIntegrationTest.java` — configuration, JavaParser dependency, non-root JDK container, build context, usage/safety guidance, and tests.
- `documentation/plan.markdown` and `documentation/document.markdown` — updated stage status, technical flow, configuration, verification state, and this append-only entry.

**Verification:** JDK 21.0.12.1 and `jdk.jdi` are available. `mvn -q test` could not run because Maven is not installed/on PATH in the coding shell; Docker build/runtime verification is also pending. Do not treat the backend stages as build-verified until Maven tests pass locally.

### 2026-10-17 — Include field and array values in heap deltas

**Technical analysis:** Expanded Stage 6 heap changes so an allocated or updated object reports the changed field names, array indices, and before/after values. Static field changes now use the same value-delta format. This makes the memory change stream actionable for the frontend instead of only naming the changed object identity.

**Files and methods changed:**

- `src/main/java/com/example/demo/model/HeapChange.java` and `HeapValueChange.java` — added nested value details to heap-change records.
- `src/main/java/com/example/demo/service/TraceEnricher.java` — `staticValues`, `heapChanges`, `objectValues`, `diffValues`, and `display` compute value-level deltas.
- `src/test/java/com/example/demo/service/TraceEnricherTest.java` — verifies allocated and changed object-field values plus the final limit event.
- `frontend/src/App.jsx` and `frontend/src/styles.css` — render named heap value changes and before/after values.
- Backend and frontend `document.markdown` / `plan.markdown` files — updated current technical scope and recorded this change.

**Verification:** The updated frontend production build passed with Vite 8.3.4 (20 modules transformed). Backend Maven tests remain unrun because Maven is unavailable in this coding shell.

### 2026-10-31 — Function call boxes in caller frames

**Technical analysis:** Completed the function-call box started on 2026-10-25. Each frame in a trace event now has a `calls` list describing calls it made on its current line to user-written methods and constructors. A row is `evaluating` while the callee runs and `returned` (with its result) after the callee exits; it stays until the caller moves to another line. Every waiting caller in the stack keeps its row, so `main` still shows `fun_multi(4, 2)` while `fun_add` runs. Several calls on one line (for example `add(add(1, 2), 3)`) are listed in Java evaluation order and matched by source position, so repeated method names resolve correctly; a call on a line that runs again (one-line loop) replaces the previous row. Rows show argument values (`result = fun_add(0, 4)`, after return `result = 4 <= fun_add(0, 4)`) plus the source form (`result = fun_add(result, number)`). A receiving variable or `return` is shown only when the call is the whole assigned/returned value. Constructors show the created object (`n = Node@42 <= new Node(5)`). Library methods (`Math.max`, `println`) never create a user frame and therefore never get a row. Calls inside lambdas and field/static initializers are not attributed. Rows are tracked by stack depth from `main`; when recursion exceeds the 50 displayed frames, rows for the cut-off frames may be dropped.

**Files and methods changed:**

- `src/main/java/com/example/demo/model/FunctionCallSnapshot.java` — `line`, `expression`, `state`, `returnValue`, `sourceExpression`.
- `src/main/java/com/example/demo/model/FrameSnapshot.java` — `calls` list with a compatibility constructor.
- `src/main/java/com/example/demo/service/CallSiteAnalyzer.java` — `analyze` lists method calls and object creations in post-order (evaluation order) with caller method/class, called method/class, display name, source text and `target`; `caller` skips lambdas/initializers; `target` only for direct assignment/declaration/return values.
- `src/main/java/com/example/demo/service/FunctionCallTracker.java` — new; `apply`, `onCall`, `onReturn`, `dropFinishedGroups`, `withCalls` keep per-depth call rows across steps.
- `src/main/java/com/example/demo/service/TraceEnricher.java` — `enrich(raw, metadata, truncated, source)` runs the tracker; the earlier single-frame `withFunctionCall` helper was removed.
- `src/main/java/com/example/demo/service/VisualizeService.java` — passes the submitted source to the enricher.
- `src/test/java/com/example/demo/service/FunctionCallTrackerTest.java` — evaluation order/targets, waiting callers, returned-state lifetime, nested calls, repeated line, constructor, quoted String/char arguments, void calls.
- `samples/FunMulti.java` and `samples/fun_multi.json` — nested-call sample.

**Verification:** Maven Central is blocked in the coding environment, so `mvn verify` was not run. The changed classes compiled with JDK 21 against a local JavaParser 3.17 jar (the project uses 3.26.3), and the real `Tracer` + `TraceEnricher` were run outside Spring on `FunMulti` and on a program covering recursion (`fact`), `add(add(1, 2), 3)`, `new Node(5)`, `n.twice()`, a void method with String/char arguments and a one-line loop; all rows matched the expected behaviour. The seven new test methods passed under a minimal JUnit stand-in. Run `mvn clean verify` locally to confirm with the real dependencies.

### 2026-10-31 — Stable frame keys for variable changes

**Technical analysis:** `TraceEnricher.variables` keyed frames by their position from the top of the stack, so every method call or return shifted all existing frames and reported each of their variables as added/out of scope (a single `fun_add` call produced 20 changes). Frames are now numbered from the bottom (`main` = 0), so only the new or removed frame's variables are reported.

**Files and methods changed:** `src/main/java/com/example/demo/service/TraceEnricher.java` — `variables`.

**Verification:** Real FunMulti trace now reports 2 added variables on the `fun_add` call, 2 out-of-scope on return, and `result` changed afterwards. The function-call tests still pass under the stand-in runner; run `mvn clean verify` locally.

### 2026-10-31 — Loop boxes in stack frames

**Technical analysis:** Each frame now carries a `loops` list describing the loops it is running, nested inner-inside-outer. `LoopSiteAnalyzer` uses JavaParser to find `for`, for-each, `while` and `do-while` loops with their condition, header/condition and body lines, and the variables each loop declares (header plus body, excluding variables owned by a nested loop, lambda or class). `LoopTracker` walks the trace per frame depth: an iteration starts when the frame's line enters the body from outside it, a run of a loop ends when the frame leaves its lines (a later re-entry, e.g. an inner loop on the next outer iteration, is a new run with a new `activationStep`), and the condition is reported with current values (`i < times` → `2 < 4`, `arr.length` resolved from the heap) plus a true/false verdict on steps where the condition is being checked, decided by where the frame goes next. For-each loops report `n of total` for arrays and collections exposing a `size` field. Caller frames keep their loop state while a callee runs. Approximations: a loop whose body is on the header line counts iterations by header visits; conditions that call methods (`list.size()`) are shown with only their variables replaced.

**Files and methods changed:**

- `src/main/java/com/example/demo/model/LoopSnapshot.java` — new loop record (kind, lines, condition, values, result, iteration, activationStep, variables, nested loops).
- `src/main/java/com/example/demo/model/FrameSnapshot.java` — `loops` component with compatibility constructors.
- `src/main/java/com/example/demo/service/LoopSiteAnalyzer.java` — new; `analyze`, `add`, `bodyLines`, `owner`.
- `src/main/java/com/example/demo/service/LoopTracker.java` — new; `apply`, `advance`, `conditionResult`, `snapshot`, `withValues`, `iterableSize`.
- `src/main/java/com/example/demo/service/CallSiteAnalyzer.java` — `caller` shared with the loop analyzer.
- `src/main/java/com/example/demo/service/TraceEnricher.java` — runs `LoopTracker` after `FunctionCallTracker`.
- `src/test/java/com/example/demo/service/LoopTrackerTest.java` — loop kinds/conditions/variables, nested iterations, condition verdicts, new inner runs, loop removal, caller loops during a call.

**Verification:** Compiled with JDK 21 against a local JavaParser 3.17 jar and run with the real `Tracer` on a program covering nested `for` loops with a call in the inner loop, `while`, `do-while`, for-each over an array and an `ArrayList`; iterations, condition values/verdicts, nesting and ownership matched expectations. The three new tests plus the seven function-call tests passed under a minimal JUnit stand-in. Run `mvn clean verify` locally.

### 2026-10-31 — Store run history per user

**Technical analysis:** The database previously held only accounts (`app_user`) and OTP challenges (`verification_challenge`); the `SPRING_SESSION` tables from migration V2 are unused leftovers. Added `visualization_run` (migration V3 for H2 and SQL Server): one row per **Run & visualize** by a signed-in user with time, outcome (`SUCCESS` only when the program finished without exception or trace limit, otherwise `FAILED`), API status, message, failure reason (`error_detail`: compiler errors line by line, `Type at line N: message` for exceptions, blocked API lines, trace limit reason, or busy/error message), step count, truncation flag, code length and duration. Source code is not stored (plan's data-minimization rule). Rows reference `app_user` with `ON DELETE CASCADE` and are indexed by user and time. `GET /api/runs` returns the caller's totals and 20 newest runs; queries are always scoped to the caller's user id. The integration test had not been updated for authentication (it posted anonymously and would get 401); it now signs in with `spring-security-test`, sends CSRF, runs against an in-memory H2 database, and verifies anonymous rejection, run counting and per-user isolation.

**Files and methods changed:**

- `src/main/resources/db/migration/h2/V3__create_visualization_run.sql`, `src/main/resources/db/migration/sqlserver/V3__create_visualization_run.sql` — new table and index.
- `src/main/java/com/example/demo/model/run/VisualizationRun.java` — new entity.
- `src/main/java/com/example/demo/repository/run/VisualizationRunRepository.java` — `countByUserId`, `countByUserIdAndOutcome`, `findTop20ByUserIdOrderByCreatedAtDesc`.
- `src/main/java/com/example/demo/service/run/RunOutcome.java` — `from` (outcome, status, message, error detail, length limits).
- `src/main/java/com/example/demo/service/run/RunHistoryService.java` — `record`, `summary`, `RunSummary`, `RunView`.
- `src/main/java/com/example/demo/controller/RunHistoryController.java` — `GET /api/runs`.
- `src/main/java/com/example/demo/controller/VisualizeController.java` — `visualize` times the run and records it for the signed-in user.
- `pom.xml` — `spring-security-test` (test scope).
- `src/test/java/com/example/demo/service/run/RunOutcomeTest.java` — new; `src/test/java/com/example/demo/integration/VisualizerIntegrationTest.java` — signed-in requests, CSRF, in-memory H2, run history checks.

**Verification:** `RunOutcome` and its five tests compiled and passed with JDK 21 (stand-in JUnit runner). The entity, repository, service and controllers were type-checked with JDK 21 against minimal stand-ins for the Spring/JPA types; they have not been compiled against real Spring, and the migrations have not been run. Run `mvn clean verify` (runs the H2 migration and the integration test) and restart the backend; Flyway applies V3 to the existing local database automatically. SQL Server/Azure SQL migration and Hibernate schema validation remain unverified.

### 2026-10-31 — Deployment readiness: user-code sandbox, proxy-aware limits, cross-site CSRF, schema cleanup

**Technical analysis:** A repository review before deployment found: (1) user programs inherited the backend's environment, so `System.getenv("DB_PASSWORD")` would print hosted secrets, and the blocklist missed `getenv`, method references (`System::exit`) and fully qualified file APIs; (2) behind Azure's ingress every request has the proxy's address, so per-client rate limits and login throttling would treat all users as one; (3) the CSRF cookie used the browser default `SameSite=Lax`, so with frontend and backend on different sites every signup/login POST would fail CSRF validation; (4) Hibernate `validate` against SQL Server could reject the `NVARCHAR`/`DATETIME2` columns at startup; (5) the Docker image's non-root user could not create the H2 `data` folder; (6) `SPRING_SESSION` tables from V2 were unused. Fixes: the tracer launches the user JVM with `-Djava.security.manager=default -Djava.security.policy==<per-run policy>` (read own classes and safe properties only); the blocklist adds `getenv` and checks method references; `ClientAddress` uses the ingress-appended `X-Forwarded-For` entry when `visualizer.trust-forwarded-for=true` (azure); visualization rate limits are keyed by the signed-in account; the CSRF cookie follows `cookie-secure`/`cookie-same-site`; SQL Server profiles use `ddl-auto=none` (Flyway remains the schema source; H2 tests still validate); Hikari waits up to 60 s for a resuming free-tier database; Flyway V4 drops the Spring Session tables; the Dockerfile caches dependencies, creates a writable `/app/data`, and passes JVM memory flags on the command line so user JVMs do not inherit them. JVM-generated lambda helper classes are no longer shown as `call` frames at line -1.

**Files and methods changed:**

- `src/main/java/com/example/demo/tracer/Tracer.java` — `Run.launch` adds the Security Manager options; new `writeSandboxPolicy` and `SANDBOX_READABLE_PROPERTIES`; `isUserClass`, `onMethodEntry`, `onMethodExit` skip `$$Lambda` helpers.
- `src/main/java/com/example/demo/service/CodeInspector.java` — `inspectCalls` checks method references; new `blockedReason` adds `getenv`.
- `src/main/java/com/example/demo/config/ClientAddress.java` — new; `of`.
- `src/main/java/com/example/demo/controller/VisualizeController.java` — per-account rate-limit key; `controller/auth/AuthController.java` — `login` uses `ClientAddress`.
- `src/main/java/com/example/demo/config/SecurityConfiguration.java` — `securityFilterChain` customizes the CSRF cookie.
- `src/main/resources/application-azure.properties`, `application-local-sqlserver.properties`, `application.properties` — hosted settings, `ddl-auto=none` for SQL Server, removed stale session comment.
- `src/main/resources/db/migration/{h2,sqlserver}/V4__drop_unused_session_tables.sql` — new.
- `Dockerfile`, `.dockerignore`, `README.md` — image and run instructions; `src/test/java/com/example/demo/service/CodeInspectorTest.java` — `blocksEnvironmentAccessAndMethodReferences`.

**Verification:** The modified tracer was compiled with JDK 21 and run outside Spring: a hostile program's `getenv`, file read, `user.home`, socket and `exec` attempts were all denied while ordinary code ran; FunMulti, recursion/constructor/loop programs and all five frontend examples produced the same output and step counts as before, including heap objects, statics and collections. `System.exit` is still permitted by the JDK for application code (it only ends the user's own JVM; direct calls are blocklisted). The inspector change was run against JavaParser 3.17. `ClientAddress` and the controllers were type-checked against stand-ins. Not verified here: `mvn clean verify` with the new test, the Docker build (first real build will run in GitHub Actions), and anything on Azure.
