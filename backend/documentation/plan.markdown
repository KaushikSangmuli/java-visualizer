# Java Code Memory Visualizer — Backend Implementation Plan

The backend accepts Java source and returns bounded execution traces through the HTTP API. Frontend planning and change history are maintained separately in `frontend/documentation/`.

## Current status

- **Run history (2026-10-31): Implemented; `mvn clean verify` pending.** `visualization_run` table (V3, H2 + SQL Server) records every visualization by a signed-in user with outcome, status and failure reason; `GET /api/runs` returns the caller's totals and recent runs. Source code is not stored.
- **Phase 2 Stage 1 (database foundation): Initial migrations and profiles added; local/cloud database verification pending.** Local default uses file-backed H2; `local-sqlserver` and `azure` target SQL Server. Account/OTP tables are used by the app; authentication does not use server-side session tables.
- **Phase 2 Stage 2 (authentication): JWT/local OTP implementation added; build and runtime verification pending.** The frontend login page gates the visualizer, signup and reset use email OTP outside the local profile, and the backend issues short-lived JWTs in HttpOnly cookies with CSRF protection. Local profile accepts fixed OTP `1234`; Azure profile requires a signing secret and real email settings. SQL Server/H2, frontend-browser, and JWT flows still need local verification.
- **Local H2 startup issue:** Corrected the configured H2 mode from unsupported `MSSQL` to `MSSQLServer` after the user's Spring Boot log showed H2 2.2.224 rejecting the old mode name. Restart from Eclipse to verify database migration startup.

- **Stages 1–2: Complete.** Maven/Spring Boot setup, request validation, in-process Java compilation, separate-JVM JDI line tracing, stdout, runtime exceptions, and execution limits. The user confirmed `/health` and a successful Largest trace.
- **Stage 3: Implemented and runtime-verified with Factorial.** The response includes recursive call stack frames, visible parameters/locals, `this` support, method entry/exit events, return values, explanations, and a maximum of 50 displayed frames. The user confirmed a live Factorial response showing `fact(3) = 6`, recursive parameter values, and return values 1, 2, and 6.
- **Stage 4: Implemented; response shape verified with Factorial.** The user's live response contains a heap snapshot on every event, includes the empty `args` array, and leaves primitive variables without reference IDs. User-object fields, static fields, non-empty arrays, and collections remain unverified. Main sources compiled with JDK 21 using Java 17 source/target settings.
- **Stage 5: Implemented; build/runtime verification pending.** JavaParser supplies method/loop/branch metadata and the AST blocklist reports restricted APIs with locations.
- **Stage 6: Implemented; build/runtime verification pending.** Trace events contain line classifications, variable/heap deltas (including changed object fields and array indices with before/after values), branch/loop explanations, and a final `end` event.
- **Stage 7: Implemented; container verification pending.** Per-account/per-client limits (real client IP behind the Azure ingress), concurrent-run admission, configurable CORS, a non-root JDK 21 container image, and a Security Manager sandbox for user programs (no environment, files, network, processes). Docker build runs first in GitHub Actions.
- **Deployment readiness (2026-10-31): Done in code; Azure setup pending.** Azure profile settings, cross-site CSRF cookie, SQL Server `ddl-auto=none`, Flyway V4 removes unused session tables, CI/CD workflow and `docs/DEPLOYMENT.md`.
- **Loop boxes: Implemented (2026-10-31); local Maven/browser verification pending.** Frames list running loops (nested) with iteration, condition values/verdict and declared variables.
- **Function call boxes: Implemented (2026-10-31); local Maven/browser verification pending.** Caller frames list calls to user methods/constructors with argument values, waiting/returned state and source text; rows persist until the caller leaves the line. Verified against real JDI traces outside Spring.
- **Stage 8: Tests and documentation added; execution pending.** JUnit tests cover source/main handling, AST inspection, trace enrichment, admission controls, and Spring MVC/JDI integration. Maven is not available in the current coding shell, so these tests have not yet run.

The phase-two authentication implementation is separate from backend Stages 1–8. Do not mark it verified until its Maven build, migrations, email delivery, and browser JWT flow have been exercised. The fixed OTP is only present in the `local` and `local-sqlserver` development profiles; do not activate either in a hosted environment.
## Stage 1 — Project setup, request handling, and compilation

- Maven Spring Boot backend with Java 17 source/target, running on a full JDK 21.
- `POST /api/visualize` accepts Java source; `GET /health` reports service health.
- Validate input size and main-method presence; identify top-level or nested main class.
- Preserve source line numbers when removing package declarations.
- Compile in-process with `JavaCompiler`, debug metadata, UTF-8, and annotation processing disabled.
- Return readable compiler diagnostics and clean per-request temporary files.

## Stage 2 — JDI line tracing

- Launch each compiled program in a separate debuggee JVM through `CommandLineLaunch`.
- Record line steps, captured stdout, uncaught exception details, and trace truncation.
- Apply configured step, stdout, and execution-time limits; close debuggee stdin and clean up the process.

## Stage 3 — Call stack, parameters, locals, and return events

- Include a user-visible stack snapshot with every trace event, ordered from the active method outward to its callers.
- Capture method name, class, return type, source line, parameters, initialized visible locals, and `this` when present.
- Add method `call` and `return` events, capture return values when the debuggee VM supports them, and provide a short explanation per event.
- Display at most 50 frames per event.
- Avoid invoking methods in the debuggee to inspect values. Represent object references safely; object graph expansion belongs to Stage 4.

## Stage 4 — Heap and static fields

- Represent primitive/string/reference values with identity-linked heap objects, arrays, collection internals, user objects, cycles, and user-class static fields.
- Limit the graph to 500 objects, 100 array elements per array, and depth 8 by default; return partial-graph truncation metadata.

## Stage 5 — Code inspection and safety blocklist

- Add JavaParser-based source metadata and AST-aware detection of blocked APIs.
- Treat the blocklist as defense in depth, not as a secure sandbox.

## Stage 6 — Trace enrichment

- Enrich events with line types, branch/loop explanations, variable changes, heap changes, and a final end event.

## Stage 7 — Runtime safety and deployment hardening

- Add concurrency limits, configurable CORS, rate limiting, container hardening, non-root execution, and restrictive-runtime documentation.

## Stage 8 — Automated tests and final documentation

- Add JUnit 5 coverage for parsing, source handling, metadata, trace enrichment, and integration scenarios; complete final documentation.

## Stage 9 — Function call and loop visualization (2026-10-31)

- **Done:** `calls` on frames (waiting/returned rows, values plus source form, nested and repeated calls, constructors, every waiting caller); `loops` on frames (for, for-each, while, do-while; iteration; condition with values and true/false verdict; loop-declared variables; nested loops; new run per re-entry); stable frame keys for variable changes; `FunctionCallTrackerTest` and `LoopTrackerTest`.
- **Pending:** run `mvn clean verify` locally; browser check with the Nested function calls and Nested loops examples; merge `feature/function-box-implementation` into `main` after review.
- **Known limits:** one-line loop bodies count iterations by header visits; conditions calling methods (`list.size()`) only substitute variables; frames beyond the 50 displayed frames may lose call/loop rows.

## Response direction

The API continues to accept Java source. Trace events include stack frames, visible frame variables, method explanations/return values, and a bounded `heap` snapshot containing `objects`, `staticFields`, and heap truncation metadata. Object references carry `referenceId` values that link frame/static/field/array values to heap entries. Successful responses add AST-derived `metadata`; events include `lineType`, `variableChanges`, and `heapChanges`, and conclude with `event: end`. Blocked code has `blockedApis` entries. Rate-limit and concurrency rejections use `status: busy`.

### 2026-10-17 implementation progress

Stages 5–8 have implementation changes in place; no future product scope has been implemented. Local Maven build, JUnit execution, Docker build, and end-to-end sample runs remain pending because the current coding shell has no Maven or Docker executable. The host JDK 21 and `jdk.jdi` module are present.


## Future product scope — after the planned backend and frontend work

These are product ideas for a later release, after the current backend and frontend plans are completed. They are not part of Stages 1–8 and are not being implemented now.

1. **Striver’s DSA sheet:** Let users choose a problem by sheet/problem number, view the problem, write or load a Java solution, and inspect its execution dry run.
2. **Company-wise question collections (premium):** Offer company-oriented problem lists as a premium product capability. Define licensing, content source, and entitlement handling before implementation.
3. **LeetCode problem selection:** Let users find a problem by its LeetCode number, view its prompt, write Java code, and dry-run it in the visualizer. Define a permitted source for question content before implementation.
