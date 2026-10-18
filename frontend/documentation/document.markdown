# Java Code Memory Visualizer — Frontend Project Document

## 1. Product Documentation

### Product overview

The frontend is the learner-facing interface for Java Code Memory Visualizer. It provides an editor and a visual walkthrough of the execution trace returned by the sibling Spring Boot backend, including AST metadata, source categories, per-step state deltas, blocked API findings, heap and stack snapshots, and output. It takes interaction and layout inspiration from `reference_for_frontend/`; it does not reuse the reference's C++ execution logic or stored trace format.

### Product goal and learner flow

A learner selects an example or edits Java source, submits it, and moves through the resulting trace. The interface connects each selected event to the source line, active call stack and variables, return value, line category, variable/heap changes, heap/static fields, and console output. It also presents compilation/runtime/blocked API outcomes, trace truncation, parser metadata, and the raw response for inspection.

### Current scope

The current UI includes Java samples, editable source with line numbers, run action, trace navigation and playback, a darker auto-scrolling active-line highlight, a Full analysis ON/OFF switch, compact function/variable frames in Normal mode, grouped parameters/locals and heap/static inspection in Full analysis mode, simplified Java type names, shape-coded values and indexed arrays, color-linked references, a double-click Focused box, backend loop ranges with frontend fallback in both modes, per-step variable/heap changes including field and array-index before/after values, source metadata, blocked API locations, stdout/errors, truncation state, and raw JSON. Normal mode shows captured array elements, nested multidimensional arrays, and object or collection fields directly inside the corresponding stack variable card; Full analysis mode continues to show references on stack and object contents in the heap. State-change details start collapsed and expand on demand. Java remains the input language; the C++ example is only a product-shape reference.

## 2. Technical Flow and Configuration

### Frontend flow

1. `src/main.jsx` mounts the React application.
2. `src/App.jsx` owns source, sample selection, backend availability, API response, trace index, focused entity, and Full analysis mode. It sends `{ "code": "..." }` to `POST /api/visualize`, renders the mode-specific inspector, and keeps loop status visible in either mode.
3. `src/examples.js` holds bundled Java examples. `src/components/CodeEditor.jsx` renders source with line numbers and current-line highlighting.
4. `src/components/TracePanels.jsx` renders simple Normal-mode function/variable frames or the richer Full analysis stack and heap views; in Normal mode it resolves reference IDs against the selected step heap snapshot and recursively embeds indexed array values or object/collection fields in the stack variable card, with a depth limit and cycle detection. It also renders the Focused box, loop status, and linked heap values. `App.jsx` displays backend line classifications, variable/heap deltas, parser metadata, and blocked API diagnostics. `src/loopAnalysis.js` prefers backend JavaParser loop ranges and falls back to source scanning for older responses.
5. `vite.config.js` proxies `/api` and `/health` to `http://localhost:8080` during development. This keeps local browser requests same-origin and avoids requiring a backend CORS change for development.
6. `src/styles.css` controls the responsive dashboard, compact stack/heap sizing, value shapes, and editor typography. The production build is emitted to `dist/`.

### Configuration and commands

- React 19 and Vite 8 are declared in `package.json`; Node.js 20.19+ is required by the installed Vite toolchain.
- Install packages and start the development server from `frontend/` with `pnpm install` and `pnpm run dev` (or use npm if installed).
- Open `http://localhost:5173`; keep the backend running on port 8080.
- Create a production bundle with `pnpm run build`; output is in `dist/`.
- `node_modules/` is local generated dependency content and is excluded from version control. `pnpm-lock.yaml` records the resolved dependency graph.

### API dependency

The frontend relies on `GET /health` and `POST /api/visualize` from `backend/`. The request is JSON `{ "code": "<Java source>" }`; the UI reads the camelCase response including `status`, `metadata`, `blockedApis`, `trace`, `frames`, `heap`, `lineType`, `variableChanges`, `heapChanges`, `stdout`, `exception`, `truncated`, and `truncatedReason`. During development, Vite proxies the calls locally. Backend JavaParser loop ranges are preferred; the UI retains heuristic parsing for older backend responses.

### Verification state

Earlier UI revisions passed `pnpm run build` with Vite 8.3.4 and the user manually ran multiple Java programs. The latest response-driven UI changes also passed `pnpm run build` with Vite 8.3.4 (20 modules transformed). Live browser integration with the changed backend is pending because Maven is unavailable in the current coding shell. The browser UI review could not be performed because Windows computer-use access to the user's Chrome window was denied; accessibility was checked in code, not with a screen reader.

## 3. Changes Made (Append Only)

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