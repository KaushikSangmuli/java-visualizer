# Working rules for this repository

Before starting, read `docs/HANDOVER.md` (project summary, decisions, status, next steps) and the status table and section 11 (deployment runbook) of `plan.markdown`. Keep `docs/HANDOVER.md` sections 3 (timeline), 6 (status) and 7 (next steps) current when work is completed.

## Documentation must be updated with every change

Every code change is committed together with its documentation. Do not leave documentation for later.

For each change:

1. **Change history (append):**
   - Backend changes: append an entry to `backend/documentation/document.markdown` → "Changes Made (Append Only)".
   - Frontend changes: append an entry to `frontend/documentation/document.markdown` → "Changes Made (Append Only)".
   - Each entry: `### YYYY-MM-DD — Title`, then **Technical analysis**, **Files and methods/components changed**, **Verification** (state exactly what was and was not verified).
2. **Current-state sections:** if behaviour, API fields or layout changed, also update the product/technical sections at the top of the same document (they describe the project as it is now).
3. **Plans and statuses:**
   - `backend/documentation/plan.markdown` / `frontend/documentation/plan.markdown`: update the "Current status" bullets and the relevant stage (Done / Pending / Known limits).
   - Root `plan.markdown`: update the "Overall project status" table and add a newest-first entry under "10. Change History".
4. **README.md:** update the Features and Status sections when user-visible features or statuses change.

Never claim something is verified unless it was actually run; say what remains pending (for example `mvn clean verify`, `pnpm run build`, browser check).

## Workflow

- Work on a feature branch (currently `feature/function-box-implementation`); never commit directly to `main`.
- The project owner runs builds locally (Eclipse/Maven for `backend/`, pnpm for `frontend/`); report exactly what to run and what to check.
- Do not commit `reference_for_frontend/` (contains third-party credentials) or the stale `bin/` copies.
