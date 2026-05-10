# Frontend

React + Vite frontend for bounswe2026group1.

## Stack

- **React 19** — UI
- **Vite 8** — build tool & dev server
- **Tailwind CSS v4** — utility-first styling
- **React Router v7** — client-side routing
- **TanStack Query v5** — server state management

## Getting started

```bash
npm install
npm run dev        # http://localhost:5173
```

## Environment

Copy `.env` and adjust as needed:

```
VITE_API_URL=http://localhost:8080/api/v1
```

## Project structure

```
src/
  assets/       # static assets
  components/   # shared UI components
  hooks/        # custom React hooks
  pages/        # route-level page components
  services/     # API helpers (apiFetch)
  App.jsx       # route definitions
  main.jsx      # app entry point
```

## API calls

Use `apiFetch` from `src/services/api.js`:

```js
import { apiFetch } from '../services/api'

const data = await apiFetch('/posts')
```

## Available scripts

| Command | Description |
|---|---|
| `npm run dev` | Start dev server |
| `npm run build` | Production build |
| `npm run preview` | Preview production build |
| `npm run lint` | Run ESLint |
| `npm test` | Run Vitest in watch mode |
| `npm run test:run` | Run Vitest once (CI / full suite) |

## Testing

Tests use **Vitest** (Jest-compatible API), **React Testing Library**, and **user-event**, configured in `vite.config.js` with `src/test/setup.js`. Spec files live next to sources (`*.test.jsx`, `*.test.js`). Components that call the API are tested with `vi.mock` on hooks or services so UI behavior (loading, success, validation errors) does not depend on a running backend. Coverage includes custom hooks, services, shared utils, presentational components, and key pages (with navigation and context mocked as needed).

To generate a **coverage** report after installing the optional dev dependency `@vitest/coverage-v8` (same major as `vitest`), add a `coverage` block under `test` in `vite.config.js` and run e.g. `npx vitest run --coverage`.
