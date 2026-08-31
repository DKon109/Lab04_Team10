# Tixora frontend

React (Vite) frontend for Tixora, our Sprint 1 campus-first event discovery app
(ELEC5619, Team 10). Two primary views: an authentication page (`/auth`) and a
scrollable event-discovery feed (`/events`), plus an organiser dashboard
(`/organiser`) for accounts with the `COMMITTEE_MEMBER` role.

## Prerequisites

- Node.js 18+ (developed on Node 26)
- The Spring Boot backend in `../app` (Java 17)
- MySQL on `localhost:3306` for the backend (e.g.
  `docker run -d --name tixora-mysql -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=eventticketingsystem -p 3306:3306 mysql:8`)

## Running everything

```bash
# 1. Backend (from the repo root) — seeds ~30 demo events on first run
cd app
./mvnw spring-boot:run          # serves http://localhost:8080

# 2. Frontend
cd frontend
npm install
npm run dev                     # serves http://localhost:5173
```

The Vite dev server proxies `/api/*` to `http://localhost:8080`
(see `vite.config.js`). This exists because the backend has no CORS
configuration yet — the browser only ever talks to the Vite origin.

Other commands:

```bash
npm run test      # Vitest unit/component tests
npm run build     # production build into dist/
npm run preview   # serve the production build locally
```

## Environment variables

Copy `.env.example` to `.env` to override defaults.

| Variable | Values | Default | Meaning |
|---|---|---|---|
| `VITE_LIKES_MODE` | `api` / `local` | `api` | Where likes and the For You feed come from |

### `api` likes mode (default)

Uses the real backend endpoints:

- `POST /api/events/{id}/like`, `DELETE /api/events/{id}/like`
- `GET /api/users/me/liked-events`
- `GET /api/events/for-you`

Likes persist in MySQL, `likeCount` values are genuine, and For You is ranked
server-side.

### `local` likes mode (temporary fallback)

A frontend-only integration stopgap for running against an older backend build
without the like endpoints. Likes are stored per-user in `localStorage`
(they survive refresh but live only in that browser) and For You is ranked
client-side in `src/services/recommendationService.js` with the same formula
the backend uses:

- +3 same category as a liked event
- +2 same organiser
- +1 per matching tag
- past events excluded; upcoming feed is the cold-start fallback

Local mode never shows like counts — counts are only displayed when the value
comes from the backend. Switching modes requires **no component changes**:
`src/services/likesService.js` picks the adapter
(`apiLikesAdapter` / `localLikesAdapter`) from the env var.

## Demo accounts

- **Student:** register any `@uni.sydney.edu.au` email on the Sign Up tab
  (password 8–72 chars). Registration auto-logs you in.
- **Organiser:** the backend seeds `organiser@tixora.demo` /
  `OrganiserDemo1!` (override with the `DEMO_ORGANISER_PASSWORD` backend env
  var). This is a demo-only seed — remove before any real deployment.

## Project structure

```
src/
  api/            fetch wrapper + one module per backend area
    apiClient.js  base URL, bearer token, 401 handling, error extraction
  components/
    auth/         login/signup form
    common/       protected route, image fallback, feed states
    events/       card, filter bar, details modal
    layout/       sticky top nav
  context/        AuthContext (session, login/register/logout)
  hooks/          useEvents (feed loading), useLikes (liked ids + toggle)
  pages/          AuthPage, EventsPage, OrganiserPage
  services/       likes adapters + recommendation scoring
  styles/         variables.css (theme tokens), global.css
  utils/          validation, date formatting, client-side search
  test/           Vitest suites
```

State ownership: `AuthContext` owns the session; `EventsPage` owns feed/filter
UI state; `useLikes` owns the liked set. API query construction lives only in
`src/api/eventsApi.js`; scope/organiser filters are applied server-side while
category, date and search are applied client-side so filter options stay
derived from the loaded feed.

## How the two frontend members can split work

The seams are already in place:

- **Pages/UX:** `pages/` + `components/` are plain presentational React —
  styling, layout and states can be iterated without touching data flow.
- **Data/integration:** `api/`, `services/`, `hooks/` — new endpoints get a
  module in `api/`, a hook if they hold state, and nothing else changes.

Known integration gaps and the backend contract are tracked in
[`../INTEGRATION_NOTES.md`](../INTEGRATION_NOTES.md).
