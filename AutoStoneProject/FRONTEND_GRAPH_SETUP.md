# Frontend graph standard

## Libraries

- React 19
- React Router 7
- **@xyflow/react** for Monitoring Graph/Flow View
- **Bootstrap 5** for layout and CSS utility classes
- Application-specific monitoring styling remains in `AutoStoneFrontend/src/styles.css`

## Install

```bash
cd AutoStoneFrontend
npm install
```

`package.json` declares `@xyflow/react` and `bootstrap`. If your CI uses `npm ci`, regenerate/refresh `package-lock.json` once in an environment with registry access so the lock contains the newly declared packages.

## Graph behavior

The graph uses live data from `/api/jobs/{id}/flow` and renders:

- circular job nodes
- rectangular BOX nodes
- dependency arrows and condition labels
- status-specific borders/colors
- node dragging
- pan/zoom
- minimap
- controls
- selection
- right-click action menu integration

No graph data is hard-coded.
