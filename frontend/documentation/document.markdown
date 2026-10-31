# Java Code Memory Visualizer — Frontend Project Document

## 1. Product Documentation

### Product overview

The frontend is the learner-facing interface for Java Code Memory Visualizer. Visitors first see the login page, with signup available as a clear next step; only authenticated users enter the Java editor. It provides a walkthrough of the Spring Boot trace including metadata, state deltas, heap/stack snapshots, and output, plus signup, email verification, and password reset. It takes interaction and layout inspiration from `reference_for_frontend/`; it does not reuse the reference's C++ execution logic or stored trace format.

### Product goal and learner flow

A learner selects an example or edits Java source, submits it, and moves through the resulting trace. The interface connects each selected event to the source line, active call stack and variables, return value, line category, variable/heap changes, heap/static fields, and console output. It also presents compilation/runtime/blocked API outcomes, trace truncation, parser metadata, and the raw response for inspection.

### Current scope

**Layout as of 2026-10-31:** the right column shows Call stack and Heap & statics first, then the Focused box. The step controls (previous/play/next and a timeline labelled `STEP n / total`) float above the page once a trace exists: the learner drags them by the ⋮⋮ grip anywhere in the window, they stay there while stepping and after reloads (remembered in this browser), are kept inside the window on resize, and reset to bottom centre with a double-click or Home key on the grip. The Execution trace header and the step context block (event badge, source line, explanation, line type, return value) and the Loop status panel are commented out in `App.jsx`. State changes sit under the heap (Full analysis) or under the stack (Normal mode). Inside each frame the order is Parameters → function calls made outside loops → Local variables → running loop boxes → Other (e.g. `this`). Function calls show waiting/returned rows with values and a hover tooltip with the source form. Loop boxes show kind, line, iteration, the ending condition in the top-right corner (source small, values larger, ✓/✗ when checked), the loop's own variables, calls made inside the loop, and nested loops; double-clicking a loop variable shows its value per iteration in the Focused box (also available in Normal mode while something is focused). Normal-mode variable cards are compact and grouped; function name and return type are bold.

Earlier scope: the UI includes Java samples, editable source with line numbers, run action, trace navigation and playback, a darker auto-scrolling active-line highlight, a Full analysis ON/OFF switch, compact function/variable frames in Normal mode, grouped parameters/locals and heap/static inspection in Full analysis mode, simplified Java type names, shape-coded values and indexed arrays, color-linked references, a double-click Focused box, backend loop ranges with frontend fallback in both modes, per-step variable/heap changes including field and array-index before/after values, source metadata, blocked API locations, stdout/errors, truncation state, and raw JSON. Normal mode shows captured array elements, nested multidimensional arrays, and object or collection fields directly inside the corresponding stack variable card; Full analysis mode continues to show references on stack and object contents in the heap. State-change details start collapsed and expand on demand. The account panel supports email/password signup, OTP verification/resend, sign-in/out, and password reset. Java remains the input language; the C++ example is only a product-shape reference.

## 2. Technical Flow and Configuration

### Frontend flow

1. `src/main.jsx` mounts the React application.
2. `src/App.jsx` owns source, sample selection, backend availability, API response, trace index, focused entity, Full analysis mode, account state, and CSRF token. It sends `{ "code": "..." }` to authenticated `POST /api/visualize`, renders the mode-specific inspector, and keeps loop status visible in either mode.
3. `src/examples.js` holds bundled Java examples. `src/components/CodeEditor.jsx` renders source with line numbers and current-line highlighting.
4. `src/components/TracePanels.jsx` renders `FunctionCalls` rows and recursive `LoopBox` components inside each frame (helpers in `src/loopHistory.js`: `loopChain`, `innermostLoopAt`, `loopVariableHistory`), and simple Normal-mode function/variable frames or the richer Full analysis stack and heap views; in Normal mode it resolves reference IDs against the selected step heap snapshot and recursively embeds indexed array values or object/collection fields in the stack variable card, with a depth limit and cycle detection. It also renders the Focused box, loop status, and linked heap values. `App.jsx` displays backend line classifications, variable/heap deltas, parser metadata, and blocked API diagnostics. `src/loopAnalysis.js` prefers backend JavaParser loop ranges and falls back to source scanning for older responses.
5. `src/components/AuthPanel.jsx` presents login first and links to signup, email verification, resend, and password reset. `App.jsx` waits for `/api/auth/me`; an unauthenticated visitor sees the account screen, and the visualizer appears after successful login. Mutating requests send the CSRF token and browser credentials; the backend's HttpOnly JWT cookie is not exposed to JavaScript. Trace submission also sends CSRF and cookie credentials. `vite.config.js` proxies `/api` and `/health` to `http://localhost:8080` during development. Production builds can set `VITE_API_BASE_URL`; configure backend CORS with the exact frontend origin.
6. `src/styles.css` controls the responsive dashboard, compact stack/heap sizing, value shapes, and editor typography. The production build is emitted to `dist/`.

### Configuration and commands

- React 19 and Vite 8 are declared in `package.json`; Node.js 20.19+ is required by the installed Vite toolchain.
- Install packages and start the development server from `frontend/` with `pnpm install` and `pnpm run dev` (or use npm if installed).
- Open `http://localhost:5173`; keep the backend running on port 8080.
- Create a production bundle with `pnpm run build`; output is in `dist/`.
- `node_modules/` is local generated dependency content and is excluded from version control. `pnpm-lock.yaml` records the resolved dependency graph.
- Set `VITE_API_BASE_URL` at build time to the backend origin for cloud deployment; local Vite proxying needs no value.

### API dependency

The frontend relies on `GET /health` and `POST /api/visualize` from `backend/`. The request is JSON `{ "code": "<Java source>" }`; the UI reads the camelCase response including `status`, `metadata`, `blockedApis`, `trace`, `frames`, `heap`, `lineType`, `variableChanges`, `heapChanges`, `stdout`, `exception`, `truncated`, and `truncatedReason`. During development, Vite proxies the calls locally. Backend JavaParser loop ranges are preferred; the UI retains heuristic parsing for older backend responses.

### Verification state

**Status as of 2026-10-31:** function-call boxes, the stack-first layout and loop boxes are implemented on branch `feature/function-box-implementation`. They were bundled with esbuild and React 19 and rendered in headless Chromium against real traces; `pnpm run build` and a live browser check on the developer machine are pending.

Earlier UI revisions passed `pnpm run build` with Vite 8.3.4 and the user manually ran multiple Java programs. For the latest login-first JWT UI, the production build passed after using Vite's runner config loader and an output directory in the writable temporary workspace (21 modules transformed). The ordinary project output path is still blocked by Windows permissions, and the standard command also received `EPERM` while writing its temporary config under `node_modules/.vite-temp`. Live browser integration with the changed backend remains pending because Java and Maven are unavailable in the current coding shell. The browser UI review could not be performed because Windows computer-use access to the user's Chrome window was denied; accessibility was checked in code, not with a screen reader.

The frontend is wired to the backend JWT flow but has not been verified end-to-end. The local H2 and `local-sqlserver` profiles accept OTP `1234` for signup and password reset without email; hosted profiles require Brevo credentials and must not enable that local OTP.

## 3. Changes Made (Append Only)

### 2026-10-18 — Make login the entry page and connect JWT authentication

**Technical analysis:** Replaced the sign-in modal as the unauthenticated entry point with a login-first screen and a signup link. After account verification and login, the app opens the visualizer. The frontend restores auth state through `/api/auth/me`, sends CSRF headers and cookies with requests, and the backend now requires authentication for visualization requests. Login issues a short-lived HS256 JWT as an HttpOnly cookie; Spring Security is stateless and uses a JWT cookie filter. The local Spring profile accepts fixed OTP `1234` for signup and password reset and skips email delivery. The `azure` profile has no fixed OTP and requires a deployment-supplied JWT signing secret and email configuration.

**Files and methods changed:**

- `frontend/src/App.jsx` — `App`, `getCsrfToken`, `authRequest`, `runCode`, and auth initialization gate the visualizer and send authenticated, CSRF-protected requests.
- `frontend/src/components/AuthPanel.jsx` — `AuthPanel` shows login first, exposes signup, and accepts four-digit local or six-digit emailed OTPs.
- `frontend/src/styles.css` — `.auth-backdrop.standalone` and `.auth-loading` style the page-level login screen.
- `backend/src/main/java/com/example/demo/config/SecurityConfiguration.java` — `securityFilterChain` enables stateless JWT-cookie auth, protects `/api/visualize`, and retains CSRF enforcement.
- `backend/src/main/java/com/example/demo/config/JwtConfiguration.java` — configures HS256 `JwtEncoder` and `JwtDecoder` with a minimum 256-bit secret.
- `backend/src/main/java/com/example/demo/config/JwtCookieAuthenticationFilter.java` — `doFilterInternal` restores request authentication from the HttpOnly JWT cookie.
- `backend/src/main/java/com/example/demo/service/auth/JwtTokenService.java` — `writeAuthCookie`, `clearAuthCookie`, and `cookie` issue and clear expiring JWT cookies.
- `backend/src/main/java/com/example/demo/controller/auth/AuthController.java` — `signup`, `login`, `logout`, and reset messaging use local OTP configuration and JWT cookies; OTP DTO validation accepts local four-digit or emailed six-digit values.
- `backend/src/main/java/com/example/demo/service/auth/AuthService.java` — `sendChallenge` persists a hash of local `1234` and skips email only when the local profile enables it.
- `backend/pom.xml`, `backend/src/main/resources/application.properties`, `application-local.properties`, `application-local-sqlserver.properties`, and `application-azure.properties` — add JWT support and profile-specific OTP/signing/cookie configuration.
- Backend and frontend `documentation/document.markdown` and `documentation/plan.markdown` — update product and technical flows, phase-two status, and this append-only record.

**Verification:** Frontend production build passed to a temporary output directory with Vite 8.3.4. Maven compilation, backend startup, migrations, and browser auth flow have not yet been run in this turn. The hosted profile requires `VISUALIZER_JWT_SECRET` (at least 32 UTF-8 bytes); the fixed OTP exists only in the local profiles.

### 2026-10-18 — Refresh authentication verification state

**Technical analysis:** Updated the current product and technical status to distinguish source integration from verified behavior. The UI performs login-first gating and sends cookie/CSRF credentials, but frontend bundle output, backend compilation, and live authentication still need local verification.

**Files and components changed:**

- `frontend/documentation/document.markdown` — current verification state and this append-only entry.
- `frontend/documentation/plan.markdown` — phase-two login-first status and Stage 8 verification status.
- Root `README.md` — authentication verification statement.

**Verification:** The production bundle passed with Vite 8.3.4 (21 modules transformed) when written to the permitted temporary workspace. Writing to the usual project `dist` path was denied. Java, `javac`, and Maven are unavailable in this shell. No backend compile or live sign-in result is claimed.

### 2026-10-18 — Verify frontend bundle with a permitted output directory

**Technical analysis:** Retried the production build using Vite's runner config loader and an output folder in the permitted temporary workspace. This verifies the current React application bundles, while the ordinary `dist/` output path remains blocked by workspace filesystem permissions. It does not verify API integration or backend authentication.

**Files and components changed:**

- `frontend/documentation/document.markdown` — appended the build verification result and updated current verification state.
- `frontend/documentation/plan.markdown` — updated Stage 8 build status.
- Root `README.md` and `plan.markdown` — clarified build and backend verification state.

**Verification:** `pnpm.cmd exec vite build --configLoader runner --outDir "C:\Users\Kaushik Sangmuli\.codex\visualizations\2026\10\17\01a14c32-da34-7df1-9c01-59ea069f3067\java-visualizer-frontend-build"` passed with Vite 8.3.4 (21 modules transformed). The temporary output directory was removed afterward. Backend Maven compilation and end-to-end authentication remain unverified.

### 2026-10-18 — Add account access screens and authenticated API wiring

**Technical analysis:** Added signup, email verification, verification-code resend, login, logout, and password-reset views. The frontend fetches the server-issued CSRF token, sends it on account mutations, includes session credentials, and supports a configured backend origin for cloud builds. Anonymous trace submission is unchanged.

**Files and components changed:**

- `src/components/AuthPanel.jsx` — `AuthPanel`, `submit`, and `resendCode` render and submit account flows.
- `src/App.jsx` — `API_BASE`, `apiUrl`, `authRequest`, `logout`, session/CSRF initialization, and account controls.
- `src/styles.css` — `.topbar-actions`, `.account-button`, `.auth-*` UI and responsive styles.
- `frontend/documentation/plan.markdown` — records phase-two account UI and verification.

**Verification:** `pnpm.cmd run build` passed with Vite 8.3.4 (21 modules transformed). Backend compilation and browser/auth integration remain pending because Java/Maven are unavailable in this shell and the database/Brevo settings are not configured.

### 2026-10-17 — Initial frontend project and documentation split

**Technical analysis:** Added an independent React/Vite application that consumes the existing Java API. The editor and trace panels are organized by UI responsibility; sample Java sources provide starter inputs. Vite forwards the API paths to the backend during development, so this frontend integration required no changes to backend Java code or CORS configuration. Split project planning and change documentation into backend-specific and frontend-specific folders.

**Files and components changed:**

- `src/App.jsx` — `App` manages source, selected sample, health, request lifecycle, trace selection, playback, and response presentation.
- `src/components/CodeEditor.jsx` — editor component renders line numbers, editable Java source, and active-line indication.
- `src/components/TracePanels.jsx` — `StackPanel`, `HeapPanel`, `ConsolePanel`, and related rendering display the selected event's state.
- `src/examples.js` — starter examples for recursion, arrays, object references/cycles, collections, and static fields.
- `src/main.jsx`, `src/styles.css`, `index.html`, `vite.config.js`, `package.json`, `pnpm-lock.yaml`, and `README.md` — app bootstrap, styling, proxy, dependencies, lockfile, and setup instructions.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — frontend product/technical documentation and stage plan.
- `backend/documentation/document.markdown` and `backend/documentation/plan.markdown` — backend-only documentation.

**Verification:** `pnpm install` completed. `pnpm run build` completed successfully with Vite. The local browser rendered the UI and backend health state; sample submission and full trace rendering remain unverified because the browser check was interrupted.


### 2026-10-17 — Record user’s local UI verification

**Technical analysis:** The user reports submitting several different Java programs through the frontend and seeing the visualizer work. This advances confidence in the request/display flow; the planned full sample matrix, unusual response states, and accessibility checks are still outstanding. No application code changed in this verification update.

**Files changed:**

- frontend/documentation/plan.markdown — updated Stage 3 and Stage 8 verification status.
- frontend/documentation/document.markdown — appended this verification record.

**Verification:** Based on the user’s manual report; no new local browser session or build was run for this update.

### 2026-10-17 — Improve stack, memory, and source-line visualization

**Technical analysis:** Restyled each stack frame as a function container with a parameter sub-container and separate local-variable boxes. Primitive values use square value cells, strings and arrays use rectangular cells, object references use circular markers, and array elements use square cells. Reference IDs select deterministic colors shared by stack references and heap nodes, helping users follow the link. The stack displays a caller line when a parent frame is available. The editor highlight is darker, active trace lines scroll into view when outside the viewport, and editor code/gutter text is slightly larger. No backend response fields changed.

**Files and components changed:**

- `frontend/src/components/TracePanels.jsx` — `colorForId`, `valueShape`, `Value`, `VariableBox`, and `StackPanel` implement identity color mapping, shape-coded values, grouped variables, and caller-line labels; `HeapPanel` renders matching heap colors and square array elements.
- `frontend/src/components/CodeEditor.jsx` — active-line `useEffect` scrolls the source textarea and synchronizes gutter/backdrop when the selected trace line is outside the viewport, keyed to the selected trace step as well as line number.
- `frontend/src/App.jsx` — `App` passes the selected trace index to `CodeEditor` to keep the active source location in view when navigating steps.
- `frontend/src/styles.css` — frame, variable, value-shape, array, reference-color, active-line highlight, and editor type styles.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — updated current UI/technical flow and appended this implementation entry.

**Verification:** The sandboxed build attempt received EPERM while writing Vite temporary configuration under frontend/node_modules/.vite-temp. The approved elevated retry then completed successfully: 19 modules transformed and production assets written to dist/. Browser visual inspection remains outstanding.
### 2026-10-17 — Compact the visualizer and add focused inspection

**Technical analysis:** Reduced editor type size, stack/heap heights, frame padding, variable boxes, and internal margins. Java type labels now omit `java.lang.` and display nested-class `$` separators as dots, while JVM launch and backend class names remain unchanged. Double-clicking a stack variable, static field, or heap object opens its value and available object contents in a dedicated Focused box. The timeline shows the step number under the pointer in a hover tooltip. Heap arrays have larger element cells labeled with indices (`[0]`, `[1]`, etc.), so values no longer stand in for element identity.

**Files and components changed:**

- `frontend/src/components/TracePanels.jsx` — `displayType` simplifies type labels; `StackPanel`/`VariableBox` trigger focus on double-click; `HeapPanel` focuses heap/static entities and renders indexed array cells; `FocusedPanel` displays selected values and shallow object contents.
- `frontend/src/App.jsx` — `App` tracks focused state, renders Focused box, resets focus on new/edit source, and provides a step-number tooltip on the timeline.
- `frontend/src/styles.css` — reduced editor font, panel heights, stack/heap padding, and variable-cell sizes; added focused-panel and larger indexed-array-cell styling.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — updated current product/technical flow and appended this change record.

**Verification:** The sandboxed build attempt received EPERM while writing Vite temporary configuration under frontend/node_modules/.vite-temp. The approved elevated retry then completed successfully: 19 modules transformed and production assets written to dist/. Browser visual inspection remains outstanding.
### 2026-10-17 — Add Normal mode and loop status

**Technical analysis:** Added a Full analysis switch, defaulting ON. ON retains the detailed stack, heap, and Focused box. OFF simplifies stack frames to the requested green function panels with pink variable cards and hides detailed heap/focus panels. Double-clicking a value in the Focused box clears it. A loop status panel appears in both modes; `loopAnalysis.js` masks comments and literals, finds common `for`, `while`, and braced `do` source ranges, and estimates iteration count from matching trace-line visits. This is a frontend heuristic; the backend response contract and Java code were not changed.

**Files and components changed:**

- `frontend/src/App.jsx` — `App` adds the mode switch, conditionally renders Full analysis panels, and computes the current loop status for the selected trace event.
- `frontend/src/components/TracePanels.jsx` — `StackPanel` renders simple Normal-mode function/variable cards; `FocusedPanel` clears the selection on double-click; `LoopStatus` presents loop location and estimated iteration.
- `frontend/src/loopAnalysis.js` — `maskCommentsAndLiterals`, `matchingDelimiter`, `bodyRange`, `detectLoops`, and `loopAtTraceStep` identify loop scopes and estimate the active iteration from trace history.
- `frontend/src/styles.css` — styles for the mode switch, screenshot-inspired Normal frames/variable cards, and loop status.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — updated product/technical flow and appended this change analysis.

**Verification:** `pnpm run build` completed successfully with Vite 8.3.4 (20 modules transformed). Browser visual inspection remains outstanding. Loop detection is a frontend source/trace heuristic, not backend-provided semantic loop metadata.

### 2026-10-17 — Display backend metadata and state changes

**Technical analysis:** Connected the UI to the additive backend Stage 5–6 response fields. Backend loop ranges now take precedence, while existing frontend detection remains for older responses. The selected step displays its AST-derived line category, variable/heap changes (including named changed fields and array indices with before/after values), source metadata, and blocked API locations; keyboard focus/action support was added for variable focus, Focused box clearing, heap-object focus, and the mode switch.

**Files and components changed:**

- `frontend/src/App.jsx` — `App` reads response metadata/blocked APIs, highlights their source lines, renders line types and state deltas, and passes parser loop ranges to loop status.
- `frontend/src/loopAnalysis.js` — `loopAtTraceStep` accepts backend loop ranges and retains source scanning fallback.
- `frontend/src/components/TracePanels.jsx` — `VariableBox`, `FocusedPanel`, and `HeapPanel` add keyboard-accessible focus/removal actions.
- `frontend/src/styles.css` — added compact styles for line categories, change rows, and metadata details.
- `frontend/README.md`, `frontend/documentation/plan.markdown`, and `frontend/documentation/document.markdown` — updated frontend/back-end integration descriptions and stage status.

**Verification:** `pnpm run build` completed successfully with Vite 8.3.4 (20 modules transformed). Live API integration and screen-reader/browser review remain pending while the updated backend build is unverified; Chrome UI control was denied by the desktop computer-use tool.

### 2026-10-17 — Show field-level heap deltas

**Technical analysis:** The backend now sends named heap value changes for object fields, array indices, and static fields. The selected trace event displays those names and before/after values under each heap change, so learners can distinguish which part of an object changed.

**Files and components changed:**

- `frontend/src/App.jsx` — `App` renders the `values` list on each heap-change event.
- `frontend/src/styles.css` — `.state-change-detail` styles nested heap value deltas.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — updated current scope and appended this UI change.

**Verification:** `pnpm run build` completed successfully with Vite 8.3.4 (20 modules transformed). Backend response behavior remains pending Maven build/test verification.

### 2026-10-18 — Show array values inside Normal-mode stack cards

**Technical analysis:** Normal mode previously displayed an array variable name, type, and reference ID, while hiding the heap panel. The stack renderer now matches array variables to the selected trace step heap snapshot by reference ID and displays each captured element inside that variable card with its numeric index. Full analysis behavior is unchanged: stack references remain visible and element values remain in the heap view. If an array snapshot is missing, Normal mode falls back to the original reference display.

**Files and methods changed:**

- `frontend/src/App.jsx` — `App` passes the current step heap snapshot into `StackPanel`.
- `frontend/src/components/TracePanels.jsx` — `StackPanel` resolves Normal-mode array references; `VariableBox` renders indexed inline elements and preserves reference fallback behavior for nested reference elements.
- `frontend/src/styles.css` — `.normal-frame .variable-box.inline-array`, `.inline-array-elements`, and `.inline-array-element` style embedded array values.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — updated Normal-mode behavior and recorded this change.

**Verification:** Pending production build.
### 2026-10-18 — Expand Normal-mode nested heap values and collapse state changes

**Technical analysis:** Normal mode now resolves each stack reference against the current step heap snapshot and recursively displays array indices and object/collection fields inline. Nested arrays therefore render as nested indexed cells. A visited-reference set prevents cycles, and a depth limit prevents unbounded visual expansion; unresolved or repeated references retain the reference display. Full analysis still uses its existing stack-reference and heap panels. State-change disclosures no longer carry the HTML `open` attribute, so details are hidden initially and shown after a user expands the disclosure.

**Files and methods changed:**

- `frontend/src/components/TracePanels.jsx` — new `InlineEntity` recursively renders arrays, object fields, and collection fields with cycle/depth guards; `VariableBox` resolves heap entities in Normal mode; `StackPanel` supplies each variable the selected heap snapshot.
- `frontend/src/App.jsx` — `App` initializes state-change `<details>` collapsed by omitting `open`.
- `frontend/src/styles.css` — nested inline entity layout and compact field/index styles.
- `frontend/documentation/document.markdown` and `frontend/documentation/plan.markdown` — updated current behavior and appended this change analysis.

**Verification:** Pending production build.

### 2026-10-31 — Function call box UI

**Technical analysis:** Each stack frame (Normal and Full analysis modes) renders the backend `calls` list below its variables, one row per call in execution order. A yellow row ("Waiting for return") shows the call with argument values, e.g. `result = fun_add(0, 4)`; a green row ("Returned") shows `result = 4 <= fun_add(0, 4)`. Hovering or keyboard-focusing a row shows the call as written in the source (`result = fun_add(result, number)`). The heading reads "Function calls" when there is more than one row. Variable cards are unchanged; a declared `result` variable keeps its own card.

**Files and components changed:**

- `src/components/TracePanels.jsx` — `FunctionCalls` renders rows with state label, value expression, source tooltip and an accessible label; used by `StackPanel` in both modes.
- `src/styles.css` — `.function-calls`, `.function-call-state` (evaluating/returned), `.function-call-source` hover/focus tooltip.
- `src/examples.js` — "Nested function calls and loop" example (`fun_multi` / `fun_add`), added 2026-10-25.

**Verification:** `pnpm install`/`pnpm run build` could not run (npm registry blocked in the coding environment). All source files passed an esbuild JSX parse, and `StackPanel` was bundled with React 19 and rendered in headless Chromium for both modes, multiple rows and the hover tooltip. Run `pnpm run build` and check the "Nested function calls and loop" example in the browser locally.

### 2026-10-31 — Stack-first trace layout and compact Normal-mode variables

**Technical analysis:** The right column now starts with the Call stack and Heap & statics panels, followed by the step controls (previous/play/next and timeline), Loop status and Focused box. The Execution trace header (title, finished status, step counter) and the step context (event badge, source line, explanation, line-type chip, return value) are commented out in `App.jsx` rather than deleted, so they can be restored; the editor already highlights the active line and the call boxes show return values. The timeline's left label now reads `STEP n / total` in place of `START`. Placeholder, request-error, compile-error and blocked-API messages still render in the same card before a trace exists. State changes moved to the bottom of the Heap & statics panel (Full analysis) or below the Call stack (Normal mode). In each frame the order is now Parameters → Function call box → Local variables → Other (e.g. `this`), in both modes; Normal mode shows small group labels. Normal-mode variable cards are compact (smaller padding, 9px name, 14px value instead of 80×78px cards with a 23px value). Function name and return type are larger and bold in both modes. The stack/heap scroll height increased from 320px to 460px.

**Files and components changed:**

- `src/App.jsx` — `App` layout order, commented header/step context, `stateChanges` element placed under heap or stack, timeline step label.
- `src/components/TracePanels.jsx` — `StackPanel` groups variables (parameters, locals, other) and places `FunctionCalls` after parameters in both modes.
- `src/styles.css` — `.trace-card.controls-only`, `.heap-state-changes`, `.normal-state-changes`, `.normal-variable-group`, `.normal-group-label`, `.other-group`, compact `.normal-frame .variable-box`, bold `.normal-frame-heading` / `.frame-signature`, `.inspect-scroll` height.

**Verification:** The full app was bundled with esbuild and React 19 and rendered in headless Chromium against a mocked backend that returned a real FunMulti trace (generated by the actual tracer); both modes, state changes and the controls were inspected in screenshots. `pnpm run build` and a live browser check remain to be run locally.

### 2026-10-31 — Loop boxes and per-iteration history

**Technical analysis:** Each running loop is drawn as a box inside its frame (both modes) after the frame's local variables. The box title shows the loop kind, line and iteration (`starting` before the first iteration); the top-right corner shows the ending condition with the source small (`i < times`) and the current values larger (`2 < 4`), plus ✓/✗ when the condition is being checked. Variables declared by the loop appear only inside its box; variables declared before the loop stay in Local variables. Function calls whose line lies inside a running loop are shown in the innermost such loop box instead of at frame level. A nested loop is drawn inside its parent's box. Double-clicking a loop variable opens the Focused box (now also shown in Normal mode while something is focused) with its value at the end of each iteration of the current run up to the selected step, the current iteration highlighted; it updates as the user steps. The separate Loop status panel is commented out. Added a "Nested loops with a function call" example.

**Files and components changed:**

- `src/loopHistory.js` — new; `loopChain`, `innermostLoopAt`, `loopVariableHistory`, `loopKindLabel`.
- `src/components/TracePanels.jsx` — new `LoopBox`; `StackPanel` splits loop variables and in-loop calls into loop boxes; `FocusedPanel` renders loop-variable history.
- `src/App.jsx` — Loop status commented out; Focused box shown in Normal mode when an item is focused and receives the trace and step.
- `src/styles.css` — `.loop-box`, `.loop-condition`, `.loop-variable-grid`, `.loop-history*`, no text selection on variable double-click.
- `src/examples.js` — Nested loops example.

**Verification:** Bundled with esbuild and React 19 and rendered in headless Chromium against a mocked backend returning a real trace of the new example: nested boxes, in-loop call row, condition values and the `row` history (iter 1: 1, iter 2: 2) were checked in both modes. Run `pnpm run build` locally.

### 2026-10-31 — Compact variables, collapsible frames, independent heap objects, fixed controls

**Technical analysis:** Array elements no longer repeat a type per index: in the heap, the Focused box and nested inline arrays, a reference element shows only its colour-coded id (`#463`); the array's type is shown once in its header. Variable boxes are smaller in both modes (padding, minimum sizes, value cells, fonts). Each function frame has a collapse/expand button (expanded by default); the collapsed state is remembered per stack depth and method while stepping. Heap objects open and close independently (previously one shared "selected" id made opening a second object close both); following a reference also opens its target. The Call stack and Heap areas have a fixed height and the per-step State changes moved inside the heap scroll area (Full analysis) or below the controls (Normal mode), so the step controls stay at the same position on every step.

**Files and components changed:**

- `src/components/TracePanels.jsx` — `Value` compact reference mode; `InlineEntity`, `HeapPanel` array elements and `FocusedPanel` elements use it; `StackPanel` collapse state and button; `HeapPanel` keeps its own set of open objects and accepts a `footer`.
- `src/App.jsx` — state changes passed as the heap footer; Normal-mode state changes placed below the controls.
- `src/styles.css` — compact `.variable-box`, value shapes and `.array-element`; `.frame-collapse`; `.value-shape.compact`; fixed `.inspect-scroll` height.

**Verification:** Headless Chromium with a real trace of a program with `int[]`, `int[][]` and `String[]`: the controls stayed at the same y-position on all 21 steps in both modes; three heap objects stayed open together and closing one left two open; a collapsed frame stayed collapsed after stepping. Run `pnpm run build` locally.

### 2026-10-31 — Static Web Apps configuration and CI build

**Technical analysis:** Added `public/staticwebapp.config.json` (copied into `dist/` by Vite) with an SPA navigation fallback to `index.html` and basic security headers for Azure Static Web Apps. The CI/CD workflow builds the frontend with `pnpm install --frozen-lockfile` and `pnpm run build`, setting `VITE_API_BASE_URL` from the repository variable `BACKEND_URL`, and uploads `dist/` to Static Web Apps on pushes to `main` once Azure is configured. No component code changed.

**Files and components changed:** `public/staticwebapp.config.json` (new); `../.github/workflows/ci-cd.yml` (frontend job and Static Web Apps deploy); `../docs/DEPLOYMENT.md`.

**Verification:** JSON validated locally; the CI build and the Static Web Apps upload have not run yet (first run happens on the pull request / push to `main`).

### 2026-10-31 — Verification status refresh

**Technical analysis:** The project owner checked the current UI (function-call boxes, loop boxes, layout, collapsible frames, compact variables, heap fixes) in the browser and declared the frontend complete. Statuses in the frontend plan, root plan and README were updated from "pending" to verified. The first `pnpm run build` on a clean machine will run in the CI workflow.

**Files changed:** `documentation/plan.markdown`, this document, root `plan.markdown`, `README.md`.

**Verification:** Documentation-only change; verification facts come from the owner's report.

### 2026-10-31 — Floating, draggable step controls

**Technical analysis:** The step controls (previous, play/pause, next, timeline and `STEP n / total`) moved out of the trace card into a floating panel above the page. It appears when a trace exists, starts at the bottom centre of the window, and can be dragged anywhere by the ⋮⋮ grip (pointer events with pointer capture, so mouse, pen and touch work and the buttons/slider are not affected). The position stays fixed while stepping, playing or re-running, is remembered across reloads in this browser (`localStorage` key `java-visualizer.step-controls.position`, wrapped in try/catch so private windows still work), is clamped back inside the window on resize, and resets to the default with a double-click or Home on the grip; arrow keys move it (Shift for larger steps). The trace card now appears only before a trace exists (placeholder, connection, compile and blocked-API messages). The page gets extra bottom padding so the panel never permanently covers the last content.

**Files and components changed:**

- `src/components/FloatingPanel.jsx` — new; `FloatingPanel` (`clamp`, `defaultPosition`, pointer/keyboard handlers, persisted position).
- `src/App.jsx` — `App` renders the step controls inside `FloatingPanel`; trace card only without a trace; `has-floating-controls` class on the shell.
- `src/styles.css` — `.floating-panel`, `.floating-grip`, `.floating-panel-body`, `.has-floating-controls` padding; removed `.trace-card.controls-only`.

**Verification:** Bundled with esbuild and React 19 and exercised in headless Chromium with a real trace: no panel before running; default bottom centre (380,830 in a 1400×900 window); dragged to (144,209); unchanged after six Next clicks and a slider change; unchanged after page reload and a new run; pulled inside the window when resized to 700×600; double-click reset to bottom centre; arrow keys moved it. `pnpm run build` and a check in the owner's browser are pending.
