# AutoStone Monitoring API

## Monitoring

- `GET /api/monitoring/jobs?rootsOnly=true&page=0&size=500` — paged top-level monitoring tree.
- `GET /api/monitoring/jobs?boxId={id}&page=0&size=1000` — lazy members of a BOX.
- `GET /api/monitoring/jobs?search={q}&status={status}` — search/filter endpoint.
- `GET /api/monitoring/timeline?jobId={id}&limit=500` — execution timeline.
- `GET /api/monitoring/alerts` — recent alerts/failures.
- `GET /api/monitoring/stream` — Server-Sent Events status stream.

## Jobs / Flow

- `GET /api/jobs`
- `GET /api/jobs/{id}`
- `GET /api/jobs/{id}/relations`
- `GET /api/jobs/{id}/flow?mode=ALL&depth=50`
- `POST /api/jobs`
- `PUT /api/jobs/{id}`
- `DELETE /api/jobs/{id}`

## Execution

- `GET /api/jobs/{jobId}/executions`
- `GET /api/executions/{executionId}`
- `GET /api/executions/{executionId}/logs`

## Operator actions

- `POST /event/start/{jobId}`
- `POST /event/stop/{jobId}`
- `POST /event/pause/{jobId}`
- `POST /event/resume/{jobId}`
- `POST /event/restart/{jobId}`
- `POST /event/force-start/{jobId}`
- `POST /event/hold/{jobId}`
- `POST /event/off-hold/{jobId}`
- `POST /event/kill/{jobId}`
- `POST /event/mark-success/{jobId}`
- `POST /event/mark-failure/{jobId}`
- `POST /event/bulk` with `{ jobIds: [], action: start|kill|restart|hold }`
- `POST /event/send-event/{jobId}`
- `POST /event/cancel-event/{jobId}`
- `POST /event/schedule/enable/{jobId}`
- `POST /event/schedule/disable/{jobId}`
