# AutoStone video-aligned Monitoring implementation

## What changed

### Monitoring Views
- Monitoring keeps the legacy AutoSys-style split layout: Views tree on the left and Summary / Jobs and Alerts / Flow tabs on the right.
- BOXes are now rendered recursively, so BOX -> nested BOX -> JOB trees remain visible instead of flattening after one level.
- Selecting a BOX scopes the monitoring summary and flow to the complete BOX hierarchy.
- Search, status filter, auto-refresh, refresh, right-click actions, open-job navigation and overview navigation remain wired to the backend APIs.

### Flow
- Replaced rectangular flow cards with a video-style circular node canvas.
- Every execution state has a distinct visual treatment: Running, Success, Failed, Waiting, Queued, Paused, Stopped, Halted, Timeout, Killed and Cancelled.
- Dependency edges are rendered parent -> child with condition labels and arrowheads.
- BOX boundaries are drawn around their members.
- Numeric/string IDs are normalized before layout and edges with missing endpoints are safely ignored, eliminating the previous `undefined.x` crash class.
- Large graphs remain scrollable and work with the existing zoom, pan, fit and overview controls.

### Backend flow traversal
- BOX traversal remains recursive to depth 20 from Monitoring.
- External dependency parents of BOX members are expanded so the displayed graph can represent the complete upstream path rather than stopping at the BOX boundary.
- Flow nodes continue to come from live database state and include status/host/flags.

### Database
- Added `autostone_strong_migration.sql` with foreign-key integrity, operational indexes, unique execution UUIDs and persistent View filters.
- Added `DATABASE_DESIGN.md` describing the production relationship model.
- Existing execution/audit tables remain the source of truth for commands and runtime status.

## Validation

- The uploaded 339-second reference video was inspected at multiple points from start to finish; the implementation follows its Views tree + Flow/circular-node interaction pattern.
- Maven/Node builds could not be executed in this sandbox because dependency artifacts are not available locally and outbound package downloads are unavailable. The project is therefore packaged with source and lockfiles, without the incomplete `node_modules` directory.

## Run locally

1. Backend: configure MySQL and run `database/autostone_schema.sql`, then `autostone_migration.sql`, `autostone_flow_upgrade.sql`, and `autostone_strong_migration.sql`.
2. Start Spring Boot on port 8081.
3. Frontend: run `npm install`, then `npm run dev`.
4. Open Monitoring and select a BOX to see the complete nested flow.
