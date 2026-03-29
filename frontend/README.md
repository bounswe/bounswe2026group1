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
