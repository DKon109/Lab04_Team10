# Tixora Sprint 1 — frontend/backend integration notes

Snapshot of where integration stands so the backend and frontend halves of the
team know exactly what is wired up and what remains.

See [Authentication API](AUTH_API.md) for registration, login,
current-user requests and responses, authentication errors, and CORS settings.

## Endpoints confirmed working in this repo (all verified with tests)

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /api/auth/register` | none | `@uni.sydney.edu.au` only, password 8–72, 409 on duplicate |
| `POST /api/auth/login` | none | returns bearer token, 3600s expiry |
| `GET /api/auth/me` | bearer | |
| `GET /api/events` | optional | `scope`, `organiserType`, `category`, `date`, `includePast` params; `likedByMe`/`likeCount` filled for authenticated callers |
| `GET /api/events/{id}` | optional | |
| `POST /api/events/{id}/like` | bearer | idempotent, 204 |
| `DELETE /api/events/{id}/like` | bearer | idempotent, 204 |
| `GET /api/users/me/liked-events` | bearer | newest-liked first |
| `GET /api/events/for-you` | bearer | ranked +3 category / +2 organiser / +1 tag, excludes past & already-liked, cold-start = upcoming feed |
| `POST /api/events` | bearer + `COMMITTEE_MEMBER` role | organiser event posting; validated `CreateEventRequest` |

The like/for-you/create endpoints were added this sprint on top of the
existing auth + events code (new: `EventLike` entity, `EventLikeRepository`,
`LikeService`, `CreateEventRequest`, `UserController`; extended:
`EventService`, `EventController`, `SecurityConfig`, `DataSeeder`).
`EventService`'s old "Member 5" TODO is resolved — list responses now carry
real `likedByMe`/`likeCount`.

## Not yet implemented (future sprints)

- Booking/attendance — the organiser dashboard's Attendees panel is a
  placeholder until this exists.
- Organiser self-service accounts — registration only creates `STUDENT`
  users; organiser access uses the seeded demo account below.
- Event editing/deletion for organisers.

## Blockers for the backend team

1. **Demo organiser seed** — `committee.demo@uni.sydney.edu.au` / `OrganiserDemo1!`
   (override via `DEMO_ORGANISER_PASSWORD` env var) is seeded by `DataSeeder`
   so the organiser dashboard is demoable. Decide the real
   organiser-onboarding story and remove this seed before anything public.
2. **Committee society setup** — organiser accounts must have
   `users.society_name` set to the exact society/club name they manage. Booking
   management and event creation compare this value with `events.organiser_name`
   and return `403` when it is missing or different. Existing local databases
   created before this field was introduced need a one-time backfill, for
   example:

   ```sql
   UPDATE users
   SET society_name = 'USYD Coding Society'
   WHERE email = 'committee.demo@uni.sydney.edu.au'
     AND role = 'COMMITTEE_MEMBER';
   ```

   Repeat the update for each committee account with its real society name.
3. **Deployment origin configuration** — CORS is implemented. For a frontend
    that calls the backend directly from a different origin, set
    `TIXORA_CORS_ALLOWED_ORIGIN` to that frontend origin.
4. `spring.jpa.hibernate.ddl-auto=update` creates the new `event_likes`
   table and nullable `users.society_name` column automatically; if you manage
   schema manually, mirror them (`users.society_name varchar(200)`, plus
   `event_likes` unique `(user_id, event_id)`, FKs to `users`/`events`,
   `created_at`).

## Switching the frontend between local and backend likes

The frontend has two interchangeable likes adapters behind
`frontend/src/services/likesService.js`:

1. `VITE_LIKES_MODE=api` (default) — real endpoints, shared persistent likes,
   server-ranked For You.
2. `VITE_LIKES_MODE=local` — temporary browser-only mode (localStorage +
   client-side ranking with the same formula). Only needed when running
   against an older backend build without the like endpoints.

To switch: set the value in `frontend/.env`, restart `npm run dev`. No
component changes are required in either direction.
