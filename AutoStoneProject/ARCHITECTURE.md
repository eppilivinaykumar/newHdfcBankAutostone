# AutoStone Enterprise Monitoring Architecture

## Runtime flow

```text
React Monitoring Workspace
  |-- REST: paged tree/search/summary/history/logs/actions
  |-- SSE: /api/monitoring/stream (status deltas)
  v
Spring Boot API
  |-- MonitoringService      -> paged hierarchy, timeline, summary
  |-- FlowService            -> recursive BOX + dependency graph
  |-- EventService            -> Start/Stop/Kill/Restart/Hold/Mark/Bulk
  |-- DependencyResolver      -> SUCCESS/FAILURE/conditional gates
  |-- ExecutionCoordinator    -> queue/concurrency/dispatch
  |-- QuartzSchedulerService  -> CRON triggers
  |-- RemoteExecutionService  -> SSH/local execution transport
  v
MySQL
  |-- job definitions + self-referencing BOX hierarchy
  |-- dependency edges
  |-- execution history/logs/events
  |-- schedules/alerts/views
  |-- enterprise_monitoring_schema.sql for normalized deployment
```

## Hierarchy

The legacy operational table `t_as_job_definition.box_code` is a self-reference, so a BOX can contain another BOX. The API traverses this relation recursively. Circular BOX containment is rejected on create/update.

## Monitoring flow

The graph API starts at any selected job. For a job inside a BOX, the containing BOX chain is included, the top-level BOX subtree is recursively expanded, and external dependency parents are included. This prevents the flow from stopping at the selected leaf and reproduces the end-to-end monitoring behavior demonstrated in the supplied video.

## Status model

UI presentation is deliberately normalized:

| Operational state | Monitoring color |
|---|---|
| RUNNING | Green |
| SUCCESS | Blue |
| FAILED / TIMEOUT | Red |
| KILLED / HALTED / CANCELLED | Dark red |
| PAUSED / HOLD | Orange |
| WAITING / QUEUED | Yellow |
| CREATED / STOPPED / SKIPPED | Gray |
| RETRY_WAIT / RESTARTED | Purple |

## Scale strategy

- Root hierarchy is paged; child BOX members are lazy-loaded.
- Monitoring tree endpoints cap a single response at 1,000 objects.
- Timeline is bounded (default 500, maximum 5,000).
- Flow traversal has a safe operational depth ceiling to prevent cyclic/broken data from exhausting the JVM; database hierarchy itself is not depth-limited.
- Composite indexes are included for parent/child lookup, dependency traversal, execution status/time and event queues.
- `enterprise_monitoring_schema.sql` includes an archive partitioning strategy because MySQL InnoDB foreign-key constraints are incompatible with partitioned OLTP tables.

## Real-time behavior

The UI uses SSE when available and keeps 5-second REST polling as a resilience fallback. The SSE stream sends only job definitions updated since the previous tick or jobs with new execution rows, rather than repeatedly sending the entire 100k-job workload.

## Monitoring frontend standard

Monitoring Graph View is implemented with `@xyflow/react`; Bootstrap 5 provides standard responsive layout/utilities. The graph is a presentation layer over the existing Flow REST API and shares the same action/context-menu path as Tree View. Live status changes are delivered by the existing SSE stream with polling fallback.
