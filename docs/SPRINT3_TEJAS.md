# Sprint 3 — Tejas attendee experience

Started: 12 October 2026. Team deadline: Sunday 25 October 2026.
Branch: `tejas-sprint3-experience`, based on main `2a5b515`.

## Jira-ready update

**Title:** Refresh Tixora frontend and complete the attendee booking journey (FR-04)

**Description:** Refreshed discovery, login, event details and shared styling with a consistent responsive design. Connected event details to individual/private-group booking, accessibility requests, simulated checkout and My bookings. Added backend checkout-attempt records and atomic booking confirmation with safe retries and capacity protection. Group booking is restricted to events where the organiser confirms permanent or temporary physical seat identifiers. Added organiser capacity/identifier fields, lazy-loaded booking screens, deferred search, compressed/cached production assets and dependency security updates. Seat allocation remains deferred until registration closes; this work does not claim to complete the team's allocation or venue-map integration.

**Acceptance criteria:**

- An authenticated attendee can discover an event, inspect its details and complete an individual booking.
- Create/join-group choices are available only when physical identifiers are confirmed; join codes are scoped to the event.
- Mock success records one booking; decline/cancel records an attempt without consuming capacity. No real money or card details are involved.
- Repeating the same checkout request returns the original result; changed payloads/owners cannot reuse its key.
- Closed, past, full and already-booked events cannot create another confirmed booking.
- My bookings displays only the signed-in attendee's records, group codes and honest allocation status.
- Responsive layouts, labelled controls, visible keyboard focus and reduced-motion support remain usable.
- Automated frontend/backend checks and the production build are documented before review.

**Suggested Jira status:** In review after checking the implementation and demo. Use the actual PR/commit as contribution evidence, not an unverified claim that the whole app is finished.

## Scope and proposal alignment

- Tejas FR-04: attendee event-details-to-booking integration is the principal deliverable. Existing FR-01–03 authentication/discovery flows are retained and visually refreshed.
- Backend support overlaps the team's booking/group/mock-payment workstream; coordinate this additive API with Sathish rather than implementing a second payment model on another branch.
- `PENDING_ALLOCATION` means a recorded booking awaiting allocation, not an assigned seat or a completed seat-map ticket.
- The original README contains early manual seat-selection/payment-provider plans. The submitted proposal governs: mock payments, deferred allocation, campus rooms plus constrained external templates. No real payment gateway, AR or advanced ML has been added.
- Existing records are not retrospectively relabelled as payment successes. Old events default to unconfirmed physical identifiers, so private groups are disabled until an authorised organiser creates an eligible event.

## Run in VS Code (PowerShell)

Open this repository folder. Start Docker Desktop, then:

```powershell
$env:FRONTEND_PORT = '5174'
$env:BACKEND_PORT = '8081'
docker compose -p tixora-sprint3 up --build -d
docker compose -p tixora-sprint3 ps
```

Open http://localhost:5174. On a cold start, the backend can take roughly a minute after the containers start; inspect `docker compose -p tixora-sprint3 logs --tail 30 backend` if login initially reports 502.

Demo organiser: `committee.demo@uni.sydney.edu.au` / `OrganiserDemo1!` (existing development seed, not production credentials). Society name when publishing: **USYD Coding Society**. For an attendee account, use Sign up with a demo `@uni.sydney.edu.au` email and an 8–72-character password. These demo accounts do not verify ownership of an email address.

For a private-group demo, publish a future event with capacity and the physical-identifier confirmation checked **only for a venue where you can substantiate that confirmation**. Book with one attendee using Start a group; a second attendee joins the same event using the returned code. Never assert that the application has already created a physical seat map.

Stop without deleting data:

```powershell
docker compose -p tixora-sprint3 stop
```

For frontend development, use Node 22.12+ and run `npm ci` then `npm run dev` in `frontend`. Its Vite proxy defaults to backend port 8080; use the default Compose ports for that workflow, or set `VITE_API_PROXY_TARGET=http://localhost:8081`.

## Before the team deadline

| Target | Remaining work / suggested existing owner |
|---|---|
| 15 Oct | Review and integrate this attendee slice; agree one booking/checkout contract with Sathish. |
| 18 Oct | Ryoji/Aadi: connect real venue IDs/layout versions, 3–5 mapped campus rooms and the three external template families; verify physical-identifier eligibility. |
| 21 Oct | Rafly: integrate deterministic post-close allocation, blocked/accessibility/aisle/capacity rules, contiguous groups and clear unavoidable-split explanations. |
| 23 Oct | Aadi/Rafly: live ticket text + seat map and anonymous organiser export; full end-to-end rehearsal for both venue paths. |
| 24 Oct | Freeze features, fix integration defects, measure the proposal's ≤10-second allocation target for up to 300 attendees, complete accessibility and browser checks. |
| 25 Oct | Final demo, accurate Jira contribution evidence and team retrospective. |

These are proposed coordination dates, not claims that teammates have accepted new assignments. Allocation timing cannot be measured until the allocation branch is integrated. Keyboard and responsive checks are not a WCAG conformance certification. No zero-lag guarantee is made.
