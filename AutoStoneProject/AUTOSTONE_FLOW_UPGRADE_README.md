# AutoStone Flow Upgrade

This package updates the supplied AutoStone project to make workflow/Flow data a backend responsibility instead of reconstructing dependencies only in React.

## Main changes

### Backend
- Added `GET /api/jobs/{id}/flow?mode=ALL&depth=10`.
- Added `FlowService` with upstream/downstream traversal, BOX expansion, external dependency inclusion, dependency edge conditions, cycle-safe level calculation, and job status information.
- Added Flow DTOs: `FlowResponse`, `FlowNodeResponse`, `FlowEdgeResponse`.
- Explicitly mapped `Job.createdAt` / `updatedAt` to the supplied `create_time` / `update_time` database columns.

### Frontend
- Added `jobApi.flow()`.
- Reworked `DagCanvas` to consume the backend Flow API and render nodes + dependency edges.
- Workflow BOX pages now request the complete flow from the backend.
- Added styling for Flow cards, dependency lines, condition labels, status borders, and the Flow API indicator.

### Database
Added `database/autostone_flow_upgrade.sql` containing:
- `t_as_view`
- `t_as_view_job`
- `t_as_machine_health`
- `t_as_job_run_event`
- durable event queue lifecycle fields

## Run

Backend:

```bash
cd BackendAutoStone
./mvnw spring-boot:run
```

Windows:

```bat
cd BackendAutoStone
mvnw.cmd spring-boot:run
```

Frontend:

```bash
cd AutoStoneFrontend
npm install
npm run dev
```

Run the database scripts in this order:

1. `database/autostone_schema.sql`
2. `database/autostone_migration.sql`
3. `database/autostone_flow_upgrade.sql`

The Flow endpoint is designed to preserve dependencies outside the selected BOX/job scope so a selected job does not lose its upstream or downstream relationships.
