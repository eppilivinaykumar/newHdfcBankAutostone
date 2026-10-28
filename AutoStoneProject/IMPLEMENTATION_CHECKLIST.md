# Monitoring implementation checklist

- [x] Existing application preserved; no new application scaffolded
- [x] React Flow / XYFlow added for Graph View
- [x] Bootstrap 5 added for CSS utilities/layout
- [x] Live `/api/jobs/{id}/flow` data used by graph
- [x] Circular job nodes and rectangular BOX nodes
- [x] Dependency arrows and condition labels
- [x] Node dragging, pan/zoom, minimap, controls, selection
- [x] Graph right-click routed into existing Monitoring action menu
- [x] SSE status stream retained with polling fallback
- [x] BOX hold/unhold propagation made recursive
- [x] Skip action added end-to-end (frontend API -> controller -> service -> execution history)
- [x] Sample 20-job JIL included under `examples/`
- [x] Architecture and frontend setup documentation updated

## Build note

The environment used to prepare this source package does not contain the newly requested npm graph/bootstrap packages in its local dependency cache and registry access timed out. The dependency declarations are present in `package.json`; run `npm install` in the frontend before `npm run build` (or refresh the lockfile in CI before `npm ci`).
- [x] Single authoritative backend execution-state transition service with allowed transitions and durable state-change events
- [x] Recursive BOX state derivation with RUNNING precedence over WAITING and terminal-child failure handling
- [x] BOX graph status now uses derived state rather than the stale BOX execution row status
- [x] Terminal state events release downstream dependencies after transaction commit, including nested BOX completion
- [x] Nested BOX execution uses shared workflow `run_id` plus `box_run_id` parent execution linkage
- [x] Corrected database design so `run_id` is not globally unique (all executions in one workflow share it)
- [x] Added parent BOX execution foreign-key/index support
- [x] Added AutoSys `auto_hold` parsing and runtime behavior for in-BOX jobs/BOXes
- [x] KILLED/HALTED/CANCELLED visual state mapped to orange consistently with the stated requirement
- [x] Full screenshot-derived JIL example added under `examples/TEST_CBSGB_MONTHLY_EOD_B_FULL.jil`
- [x] Parent BOX navigation button added to Graph View
- [x] ConditionEvaluator regression tests added
