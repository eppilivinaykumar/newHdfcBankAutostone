# AutoStone Flow Error Fix

## Error fixed

`MonitoringPage.jsx` previously rendered an inline `DependencyGraph` that assumed every dependency edge endpoint had a calculated position:

```js
positions.get(pid).x
positions.get(pid).y
```

When the dependency referenced a job outside the filtered/scope list, `positions.get(pid)` returned `undefined`, causing:

`TypeError: Cannot read properties of undefined (reading 'x')`

## Fix

1. Monitoring Flow now uses the backend-backed `DagCanvas` component and `GET /api/jobs/{id}/flow`.
2. Edge rendering validates both endpoints before drawing.
3. Node and edge IDs are normalized to strings to avoid number/string ID mismatches.
4. Node rendering always has a safe fallback position.
5. Flow node clicks stop event propagation so the canvas pan handler does not interfere with node interaction.
6. The old unsafe `DependencyGraph` implementation was removed from `MonitoringPage.jsx`.

## Expected result

Selecting a BOX or job in Monitoring -> Flow should no longer crash the React ErrorBoundary. Dependencies returned by the backend are rendered when both endpoints are present, while incomplete/stale edges are safely skipped instead of crashing the page.

## Important

Start the backend and frontend normally. The frontend calls:

`GET /api/jobs/{id}/flow?mode=ALL&depth=10`

The React DevTools message is only a development recommendation and is unrelated to this error.
