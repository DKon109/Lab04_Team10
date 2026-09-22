# Tixora — frontend/backend integration notes

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

The like/for-you/create endpoints were added in Sprint 1 on top of the
existing auth + events code (new: `EventLike` entity, `EventLikeRepository`,
`LikeService`, `CreateEventRequest`, `UserController`; extended:
`EventService`, `EventController`, `SecurityConfig`, `DataSeeder`).
`EventService`'s old "Member 5" TODO is resolved — list responses now carry
real `likedByMe`/`likeCount`.

## Remaining work and integration gaps

- The booking backend exists, but the current OrganiserPage Attendees
  panel is still a placeholder.
- Organiser self-service accounts are not implemented. Registration
  creates STUDENT users; organiser access uses configured committee accounts.
- Owner-only draft editing is implemented in the backend. Connecting
  the organiser screens to the event management APIs remains frontend work.
- Event deletion is not implemented.

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

## Sprint 2: Event management APIs (SCRUM-46 and SCRUM-47)

These backend APIs are implemented. Frontend integration is pending.

All management endpoints require a bearer token and the
COMMITTEE_MEMBER role.

| Endpoint | Behaviour |
|---|---|
| `POST /api/events` | Creates a draft owned by the authenticated organiser. |
| `GET /api/organiser/events` | Lists only the authenticated organiser's events. |
| `GET /api/organiser/events?status=draft` | Filters the organiser's events by status. Accepted values are draft, published, and closed. Omit the parameter to list all statuses. |
| `PUT /api/events/{id}` | Updates an owned draft using the complete CreateEventRequest body. |
| `PATCH /api/events/{id}/status` | Publishes or closes an owned event. |

### Ownership and society validation

The backend assigns the owner from the authenticated user.
Clients cannot select the owner.

Creating and editing events requires organiserName to match the
committee member's assigned society, ignoring case and surrounding
whitespace. Another member of the same society cannot edit or change
the status of an event they do not own.

### Event lifecycle

New events have status `draft`.

The permitted transitions are:

- `draft` to `published`
- `published` to `closed`

Publishing requires a future event date.
Editing is permitted only while the event is a draft.
Invalid transitions and editing a non-draft event return HTTP 409.

Example status request body:

    {"status": "published"}

Use `{"status": "closed"}` to close a published event.

### Public access and bookings

Public event lists, details, recommendations, and liked-event lists
expose only published events.

Non-published event details and new like requests return HTTP 404.
An existing like can still be removed after an event is closed.

New bookings require a published event and open registration.
An idempotent retry may return an existing booking.

Event status and registrationStatus are separate fields.
Changing event status to closed does not update registrationStatus,
but new bookings are rejected because the event is no longer published.

### Frontend handoff

Aadi owns the organiser screen integration.
Event creation saves a draft; it does not publish the event.
Use the organiser endpoint to retrieve drafts and the status endpoint
to publish or close them.
