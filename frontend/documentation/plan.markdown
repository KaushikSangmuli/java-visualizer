# Frontend Implementation Plan

The frontend is an independent React/Vite application that accepts Java source and presents the trace produced by the Java backend. The backend roadmap is maintained in `backend/documentation/plan.markdown`.

## Current status

- **Phase 2 account UI: Login-first flow working locally (owner, 2026-10-31); hosted cross-site login pending first deploy.** New visitors see login first with a signup link; successful authentication reveals the visualizer. Signup, verification/resend, logout, and password-reset screens are wired to the auth API. Local signup/reset shows the development OTP supplied by the backend. The frontend production bundle passes when written to the permitted temporary workspace.

- **Stage 1 — Frontend foundation: Implemented and build-verified.** React/Vite project, app bootstrap, dependency lockfile, and development configuration are present. `pnpm install` and `pnpm run build` succeeded.
- **Stage 2 — Source editor and examples: Implemented.** Editable line-numbered source editor and bundled Java examples are present.
- **Stage 3 — Backend connection and submission: Implemented and manually exercised by the user.** Health status, API submission, and trace display work for several Java programs in the user’s local browser; Vite proxies to port 8080. The full six-sample acceptance set has not been formally checked.
- **Stage 4 — Trace navigation and source focus: Implemented; response-driven behavior not fully verified.** Step controls, playback, timeline selection, and active source line are present.
- **Stage 5 — Stack and variables: Implemented; two display modes added.** Full analysis ON shows grouped parameter/local boxes and detailed stack/heap/focus views. OFF shows screenshot-inspired function panels and variable cards; array cards show recursively nested indexed values, and object/collection cards show captured fields resolved from the current heap snapshot. Expansion is cycle-safe and depth-limited. State-change details start collapsed. Double-clicking focused content clears the Focused box.
- **Stage 6 — Heap and static state: Implemented; detailed view is shown when Full analysis is ON.** Heap nodes share identity colors with stack references; indexed array cells, fields, static fields, and reference navigation remain supported. Double-clicking a heap object opens it in the Focused box.
- **Stage 7 — Output, errors, and responsive dashboard: Implemented; browser QA incomplete.** Output, handled failures, truncation, raw JSON, responsive styling, a darker auto-scrolling current-line highlight, smaller editor text, and reduced stack/heap padding are present. The timeline tooltip shows the step number under the pointer. A loop status panel is present in both modes and estimates the active iteration from source ranges and trace line visits.
- **Loop boxes: Done (2026-10-31); owner checked in the browser.** Loop boxes with condition corner, nested loops, in-loop calls and per-iteration history in the Focused box; Loop status panel hidden.
- **Function call box: Done (2026-10-31); owner checked in the browser.** Stack frames show waiting/returned call rows in both modes with a source-text tooltip.
- **Stage 8 — Integration QA and delivery: In progress.** The UI now renders backend metadata, line categories, state deltas, and blocked-API locations. The production bundle passed when Vite wrote to the permitted temporary workspace; writing to the project `dist` path remains blocked. Live browser/API integration with the updated backend remains pending because Java and Maven are unavailable in the current coding shell. Chrome visual review was blocked by the desktop computer-use tool.

## Stage 1 — Frontend foundation

- Keep React/Vite under `frontend/`, independent from the Maven backend.
- Configure scripts, dependency lockfile, entry point, and local development proxy.
- Keep generated dependencies and build output out of version control.

## Stage 2 — Editor and sample selection

- Provide an editable Java source area with line numbers and active execution line.
- Include samples demonstrating recursion, arrays, objects, cycles, collections, and static state.
- Allow selecting and editing an example before submission.

## Stage 3 — Backend connection and submission

- Read backend health state from `GET /health`.
- Send source as JSON to `POST /api/visualize` and expose loading/error states.
- Keep the API origin configurable through the Vite proxy for local development.

## Stage 4 — Trace navigation and source focus

- Support previous/next, play/pause, and timeline selection.
- Synchronize selected event with highlighted source line and event index.
- Handle empty traces and truncated traces.

## Stage 5 — Call stack and variables

- Show active method and caller frames, class/method metadata, parameters, locals, `this`, and return value where present.
- Preserve correct selection of frame and event as the user navigates.

## Stage 6 — Heap and static state

- Display object/array nodes, fields, elements, static fields, and IDs.
- Link references from variables/fields to heap nodes and represent cycles safely.
- Reflect backend heap truncation metadata.

## Stage 7 — Console, errors, and usability

- Display stdout, compiler diagnostics, runtime exceptions, response status, and truncation reason.
- Keep raw response available for debugging.
- Review responsive layout, keyboard interaction, empty/loading/error states, and accessible labels.

## Stage 8 — Integration verification and delivery

- Run the frontend build and backend together locally.
- Submit all six backend sample requests from the UI and compare status, lines, output, errors, truncation, frame, and heap data with backend results.
- Record supported browsers and limitations; document production hosting/API origin configuration.
- Do not claim integration completion until the sample-driven browser checks pass.

## Phase 2 — Account UI

- Provide signup, email verification/resend, sign-in/out, and password-reset flows.
- Send CSRF tokens and cookie credentials to the backend; accept the backend's HttpOnly JWT cookie and support a configured production API origin.
- **Exit criteria:** frontend build succeeds and flows are manually verified against the configured backend, database, and transactional email.
## Stage 9 — Function calls, loops and layout (2026-10-31)

- **Done:** function-call rows in frames (both modes, hover source); stack-first layout with controls below; trace header, step context and Loop status commented out; state changes under heap/stack; parameters → calls → locals → loops → other ordering; compact Normal-mode cards and bold headings; loop boxes with condition corner, own variables, in-loop calls and nested loops; per-iteration history in the Focused box; Nested loops example.
- **Done (later on 2026-10-31):** no per-index types in arrays; smaller variable boxes everywhere; collapsible function frames; heap objects open independently (bug fix); fixed-height stack/heap so the step controls never move.
- **Done (2026-11-01): tree diagrams** for user binary trees (with variable highlights) and TreeMap/TreeSet. Owner check pending.
- **Done (2026-11-01): collection views** in both modes (list, linked list with node addresses, stack, queue, priority queue, set, map; size everywhere). Owner check pending.
- **Done (2026-11-01): CSRF retry and no logout on server errors** (owner re-test pending).
- **Done (2026-10-31): floating, draggable step controls** that stay where dropped (remembered per browser, double-click to reset).
- **Done (deployment prep):** Static Web Apps config (`public/staticwebapp.config.json`) and CI build/deploy job; frontend declared complete by the project owner.
- **Verified:** owner checked the UI in the browser and declared the frontend complete (2026-10-31).
- **Pending:** first CI `pnpm run build`; decide whether the commented-out header/step context/Loop status code should be deleted; merge the feature branch after review.

### Frontend visual update

- Stack frames now show parameters and locals in nested variable boxes, with caller line labels when available.
- Values use shape cues: primitive squares, string rectangles, object-reference circles, rectangular arrays, and square array elements.
- Reference IDs map to consistent colors in stack and heap panels.
- The active source line is darker, scrolls into view as trace selection changes, and uses a slightly larger code font.

### Normal mode and loop indicator

- Full analysis defaults ON; switching it OFF shows simplified function and variable panels while retaining source/trace navigation and loop status.
- Loop scopes are inferred in the frontend from Java source and trace line visits. Iteration counts are approximate and do not add backend loop metadata.
- Double-clicking the current focused content clears the Focused box.

### Backend metadata and state-change display

- Prefer backend AST loop ranges for the loop panel; retain frontend source scanning as a compatibility fallback.
- Display per-step line classifications and variable/heap deltas, source method/loop/branch metadata, and blocked API locations.
- Expand heap deltas to show changed field names, array indices, and before/after values.
- Preserve the existing trace controls, call stack, heap views, Full analysis switch, and raw JSON panel.
