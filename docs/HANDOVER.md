# Project handover — start here

This file lets anyone (you, a teammate, or a new AI session) pick the project up without the chat history. Read it first, then [`plan.markdown`](../plan.markdown) (status table, stages, change history) and [`DEPLOYMENT.md`](DEPLOYMENT.md) (exact Azure steps). Working rules for every change are in [`CLAUDE.md`](../CLAUDE.md).

*Last updated: 2026-10-31.*

---

## 1. What the project is

**Java Code Memory Visualizer** — "Python Tutor for Java", a CDAC final project. A user signs in, pastes a Java program, clicks **Run & visualize**, and steps through it line by line: call stack, parameters/locals, function calls a frame is waiting on, running loops, heap objects, static fields and console output.

| Part | Technology | Folder |
|---|---|---|
| Backend API | Spring Boot 3.2, Java 21 (targets Java 17), JDI tracer, JavaParser, Spring Security JWT, JPA, Flyway | `backend/` |
| Frontend | React 19 + Vite 8 | `frontend/` |
| Database | H2 file locally; Azure SQL in the cloud | migrations in `backend/src/main/resources/db/migration/` |
| Deployment | Docker image (GHCR) → Azure Container Apps; React build → Azure Static Web Apps; GitHub Actions | `backend/Dockerfile`, `.github/workflows/ci-cd.yml` |

GitHub: `KaushikSangmuli/java-visualizer` (public for now; the owner may make it private later).

## 2. How a run works (backend pipeline)

1. `POST /api/visualize` (signed-in) → `VisualizeController` → `VisualizeService`.
2. Validate size and `main` → `CodeInspector` (JavaParser metadata + API blocklist) → write source to a temp folder → `CompilerService` (in-process `javac`, `-g`, `--release 17`).
3. `Tracer` launches the program in a **separate JVM** through JDI, sandboxed by a Java Security Manager policy, and records a step per line/call/return with stack frames and a bounded heap snapshot.
4. `TraceEnricher` adds line types, state changes, **function-call rows** (`FunctionCallTracker`) and **loop boxes** (`LoopTracker`), plus a final `end` step.
5. `RunHistoryService` stores one `visualization_run` row (outcome, status, failure reason — never the code).
6. The frontend renders the steps (`App.jsx`, `components/TracePanels.jsx`).

## 3. Timeline — what was done

**2026-10-17 → 10-18 (built with ChatGPT Codex, commits `2e3640b`, `2741fe8`)**
- Backend stages 1–8: compile, JDI tracing, stack/variables, heap & statics, JavaParser metadata + blocklist, trace enrichment, rate/concurrency limits, tests.
- Frontend stages 1–7: editor, examples, step controls, stack/heap views, Normal vs Full analysis, Focused box, console.
- Phase 2 accounts: H2/SQL Server profiles, Flyway V1–V2, signup + email OTP (local OTP `1234`), JWT HttpOnly cookie login, CSRF, password reset; login-first UI.
- Codex could not run Java/Maven, so much of this was unverified at the time.

**2026-10-25 (Codex, unfinished)** — started the function-call box; stopped when Codex tokens ran out. Saved as commit `62657f1` on branch `feature/function-box-implementation`.

**2026-10-31 (with Claude, same branch)**

| Commit | What |
|---|---|
| `b849780` | Function-call boxes finished: every waiting caller keeps its row; returned rows stay until the line changes; nested/repeated calls; constructors; values with source on hover. Tests. |
| `f5e2539` | Stack-first layout (stack + heap on top, controls below); trace header, step context commented out; state changes moved; grouped variables; bug fix: a call no longer reported every outer variable as changed. |
| `4113477`, `b18c0a1` | Loop boxes: for / for-each / while / do-while with iteration, ending condition with live values (✓/✗), loop-declared variables, in-loop calls, nested loops, per-iteration history in the Focused box. Loop status panel commented out. Docs + README rewrite. |
| `865e09c`, `5a5f53a` | Compact variable boxes, collapsible function frames, heap objects open independently (bug fix), fixed-position step controls, no per-index array types. Added `CLAUDE.md` documentation rules. |
| `5590aa3` | Run history in the database (`visualization_run`, Flyway V3), `GET /api/runs`; integration test fixed for authentication. |
| `1c08d1d` | Deployment readiness: user-code sandbox (Security Manager), blocklist gaps (`getenv`, `System::exit`), real client IP behind Azure ingress and per-account rate limits, cross-site CSRF cookie, `ddl-auto=none` on SQL Server, Flyway V4 drops unused Spring Session tables, Dockerfile fixes, CI/CD workflow, Static Web Apps config, `docs/DEPLOYMENT.md`. |
| `e5e023b`, `40206a2`, `79ae3b5` | Status refresh (owner verified `mvn clean verify` and the UI), handover and deployment runbook. |
| `8149050` | Floating, draggable step controls that stay where dropped (remembered per browser). |
| `faf2801` | Fix false logouts: `/error` permitted, JSON 403 for CSRF, sliding JWT session, frontend CSRF retry. |
| `9d57737` | Security review of that fix (no bypass); sliding session capped at 12 hours after login; `JwtTokenServiceTest`. |
| `ec0031d` | DSA views for Java collections (list, linked list nodes with addresses, stack, queue, priority queue, set, map; size); stack/queue from code usage; Collections example. |
| (next commit, 2026-11-01) | Tree diagrams: user binary trees (variable highlights) and TreeMap/TreeSet red-black trees; BST example. |

## 4. Key decisions and why

| Decision | Why |
|---|---|
| Work on `feature/function-box-implementation`, merge to `main` by pull request | `main` deploys automatically; CI must be green first. |
| JWT in an HttpOnly cookie + CSRF token | Token not readable by JavaScript; CSRF protects cookie-based requests. |
| Sliding 30-minute session, hard cap 12 hours after login | Active users are not logged out mid-session; a stolen cookie cannot be kept alive indefinitely. |
| Local OTP `1234` only in `local` / `local-sqlserver` profiles | Develop without email; hosted `azure` profile sends real codes via Brevo. |
| H2 locally, Azure SQL in the cloud, Flyway owns the schema | Same code, different profile; migrations run automatically on startup; never edit an applied migration — add a new version. |
| `ddl-auto=none` on SQL Server, `validate` on H2 | Hibernate's type check could reject `NVARCHAR`/`DATETIME2`; tests still validate on H2. |
| Run history stores outcome and failure reason, **not the source code** | Data-minimisation rule in the plan (code can be private/academic). |
| User code runs under a Java Security Manager policy; JDK 21 pinned | Stops user programs reading secrets (env vars), files, network, processes. Security Manager was removed in JDK 24, so the image stays on 21. |
| Hosting split: Static Web Apps Free + Container Apps (owner's choice, matches plan) | All free tiers. Known limitation: Safari/iOS block the cross-site login cookie until a custom domain is added. |
| Image registry: GitHub Packages (`ghcr.io`) | Free, built into GitHub Actions. |
| Container App max replicas 1, min 0 | Rate limits live in memory; scale-to-zero keeps cost inside the free grant. |
| Deploy jobs skip until Azure variables exist | Merging before Azure is ready never fails. |

## 5. Run it locally

```powershell
# backend (or run VisualizerApplication in Eclipse)
cd backend
mvn spring-boot:run
# frontend
cd frontend
pnpm install
pnpm run dev
```

Open `http://localhost:5173`, sign up, use OTP `1234`. Tests: `mvn clean verify` in `backend/`. Eclipse tips: if you get `ClassNotFoundException`, do Refresh → Maven → Update Project (Force Update) → Project → Clean. Migrations run automatically on start.

## 6. Where things stand (2026-10-31)

- **Verified on the owner's machine:** `mvn clean verify` passes (4 warnings, not yet reviewed) up to commit `5590aa3`; the UI works end to end locally; frontend declared complete.
- **Not yet built by Maven:** commit `1c08d1d` and later (sandbox, V4, CSRF, CI/CD). First build = CI on the pull request.
- **Collection views and tree diagrams (2026-11-01):** verified with the real tracer and in headless Chromium; `mvn clean verify` and owner browser check pending.
- **Not yet checked in the owner's browser:** floating step controls (tested in headless Chromium); login-session fix from 2026-11-01 (owner hit a false "Authentication required" after running custom code).
- **Open pre-deployment items (proposed, awaiting owner's go-ahead):** Azure SQL date/time mapping fix (`hibernate.type.preferred_instant_jdbc_type=TIMESTAMP` for SQL Server profiles, `-Duser.timezone=UTC` in the image); optional signup allowlist for the private beta.
- **Not started:** Azure resources (see the runbook in `plan.markdown` section 11).

## 7. What to do next

1. Open a pull request `feature/function-box-implementation` → `main`; wait for CI (backend tests, frontend build, Docker build). Fix anything red.
2. Merge. The push builds and publishes the backend image to GHCR.
3. Follow the deployment runbook (`plan.markdown` §11, details in `DEPLOYMENT.md`) step by step, ticking the checklist as you go.
4. Smoke-test in Chrome/Edge; set the budget alert.
5. Later: review the 4 Maven warnings; decide whether to delete the commented-out frontend code; custom domain (fixes Safari); production-readiness review before public launch; account deletion; optional "My runs" UI.

## 8. Known limitations

- Safari / iPhone block the login cookie with the default split URLs (fix: custom domain).
- User programs are sandboxed for secrets/files/network/processes, but CPU/memory are shared within one replica; treat the deployment as a private beta.
- A loop body written on the header line counts iterations approximately; conditions that call methods (`list.size()`) show variables substituted only.
- Frames deeper than 50 (very deep recursion) may lose call/loop rows.
- Priority queues with a custom comparator show heap-array order (first element is still next out); `EnumSet`/`EnumMap`, `IdentityHashMap`, `WeakHashMap`, skip-list and blocking collections still show raw fields; user-written binary trees are drawn as trees, but user-written linked-list `Node` classes are not yet drawn as node boxes (natural next step); very deep trees beyond the heap depth limit (8 references from a variable) are cut off.
- `System.exit` inside user code is allowed by the JDK (it only ends the user's own JVM; direct calls are blocklisted).

## 9. Resuming with a new AI session

Attach the repository and say: *"Read `CLAUDE.md`, `docs/HANDOVER.md` and `plan.markdown` first, then continue from the next unchecked item in the deployment runbook."* Every change must follow `CLAUDE.md` (change history, plans, statuses, README in the same commit).
