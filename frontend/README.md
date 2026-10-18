# Java Visualizer frontend

React 19 application built with Vite. It uses the Spring Boot API in `../backend`, which provides source metadata, loop ranges, per-step variable/heap changes, and blocked API details in addition to execution snapshots.

## Requirements

- Node.js 20.19 or newer
- npm
- Spring Boot backend running at `http://localhost:8080`

## Start

```powershell
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite development server proxies `/api/*` and `/health` to port 8080, so no backend CORS setting is needed for local development.

To build static production assets, run `npm run build`. The result is written to `dist/`.

## Interaction

1. Choose a Java example or edit the source in the editor.
2. Select **Run & visualize** to call `POST /api/visualize`.
3. Use previous, play/pause, next, or the timeline slider to select a trace event.
4. Inspect the highlighted source line, current call stack, variables, heap objects, static fields, state changes, parser metadata, and program output. Turn **Full analysis** off for the simplified stack view.

The initial examples include recursion, arrays, a linked cycle, collection internals, and static fields. The reference project is retained in `../reference_for_frontend`; the UI borrows its educational layout concept, not its C++ execution service or stored trace format.

## Project documentation

See [document.markdown](documentation/document.markdown) for frontend product and technical documentation with an append-only change history, and [plan.markdown](documentation/plan.markdown) for frontend stages.
