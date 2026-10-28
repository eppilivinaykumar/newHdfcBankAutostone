# Backend implementation notes

## Major fixes

1. Fixed duplicate `JobService.get(Long)` method compilation error by keeping the public `get` and using `findJob` internally.
2. Added a real `ExecutionCoordinatorService` so QUEUE concurrency creates a separate queued execution instead of returning the currently running execution.
3. Added FIFO dispatch of queued executions after a terminal execution.
4. Added callback secret validation.
5. Removed callback secrets from remote process command arguments; remote wrapper reads `/etc/autostone/callback-secret` by default.
6. Fixed wrapper completion handling so the supervisor owns and waits for the command it starts.
7. Added PID/process-group signaling and timeout exit code 124.
8. Added retry attempt history without overwriting previous attempts.
9. Added large-log tail retrieval to avoid loading unlimited logs.
10. Added cron timezone, schedule start/end and misfire policy.
11. Added backend cycle validation for manual dependencies and JIL imports.
12. Added job indexes and execution indexes.
13. Added dashboard summary API.
14. Moved database/SSH/callback configuration to environment-backed properties.
15. Added complete Job Inventory and enterprise job creation UI.
16. Added `/jobs` route so JIL import redirects to a real page.
17. Added actual search/status/type filtering to monitoring and job inventory.
