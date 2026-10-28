# Monitoring Implementation

The Monitoring page is implemented as an enhancement to the existing AutoStone application, not a replacement application.

### Views

1. Summary Dashboard
2. Tree View
3. Graph View
4. Dependency View
5. BOX View
6. Execution Timeline
7. Job Status Dashboard
8. Search & Filter

The left Views tree is lazy for BOX members. Right-click opens status-aware operator actions. Selecting a BOX or child drives the end-to-end graph. Graph nodes use the requested monitoring colors.

### Action behavior

Single-job and BOX controls route through the existing EventService. BOX Start/Stop/Kill/Restart operate on the complete run. Bulk actions use a dedicated endpoint and report partial failures instead of silently discarding them.

### Verification limitation

This environment has the uploaded source and videos, but Maven/npm dependency artifacts are not fully cached. A final local build therefore requires the project's normal `npm install` and Maven dependency resolution. The code was statically inspected and the uploaded video was sampled end-to-end to align the Monitoring layout and graph behavior.

## Graph library and CSS standard

The Monitoring Graph View uses **@xyflow/react (React Flow / XYFlow)** as the graph library. It provides standard React nodes, edges, pan/zoom, minimap, controls, node dragging, selection and scalable graph interaction. Bootstrap 5 CSS is loaded globally from `bootstrap/dist/css/bootstrap.min.css`; application-specific monitoring styles remain in `src/styles.css` so Bootstrap can be upgraded independently.

The graph consumes the existing `/api/jobs/{id}/flow` backend contract and does not use dummy graph data. Right-clicking a graph node opens the same Monitoring context-menu/action path used by the hierarchy view.

The event API now also exposes `POST /event/skip/{jobId}` and BOX hold/unhold recursively propagates to all nested descendants.
