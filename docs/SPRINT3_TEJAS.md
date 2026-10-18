# Sprint 3 — Tejas experience and seating integration

Started: 12 October 2026. Team deadline: Sunday 25 October 2026.
Branch: `tejas-sprint3-experience`, based on main `2a5b515`.

## Jira-ready update

**Title:** Complete the booking-to-seat journey and modernise the Tixora experience

**Description:** Refreshed discovery, login, event details and shared styling with a consistent responsive design. Connected individual/private-group booking, accessibility requests, simulated checkout and My bookings. Integrated the team's existing event-lifecycle and allocation branches, preserving original Git authorship. Added the missing database/API integration and live frontend for draft editing, venue configuration, publishing, closing registration, allocation, private seat-map tickets and anonymous CSV export. Added atomic checkout/retries, immutable published layouts, accessible-seat reservation and capacity protection. Improved delivery performance with lazy-loaded screens, deferred search, compressed/cached assets, dependency updates and Docker readiness checks.

**Acceptance criteria:**

- An authenticated attendee can discover an event, inspect its details and complete an individual booking.
- Create/join-group choices are available only when physical identifiers are confirmed; join codes are scoped to the event.
- Mock success records one booking; decline/cancel records an attempt without consuming capacity. No real money or card details are involved.
- Repeating the same checkout request returns the original result; changed payloads/owners cannot reuse its key.
- Closed, past, full and already-booked events cannot create another confirmed booking.
- My bookings displays only the signed-in attendee's records, group codes and honest allocation status.
- Only the event owner can edit drafts, save layouts or run allocation; published layouts are immutable.
- Registration must close before allocation. Repeated allocation returns the same persisted result, with no duplicate seat assignment.
- Attendees see their own stored seat and any group-split explanation; organisers export references without names/emails.
- Three external layout families are supported. Campus profiles are explicitly labelled as unverified demos, not completed physical maps.
- Responsive layouts, labelled controls, visible keyboard focus and reduced-motion support remain usable.
- Automated frontend/backend checks and the production build are documented before review.

**Suggested Jira status:** In review after checking the implementation and demo. Use the actual PR/commit as contribution evidence, not an unverified claim that the whole app is finished.

## Scope and proposal alignment

- Tejas FR-04: attendee event-details-to-booking integration is the principal deliverable. Existing FR-01–03 authentication/discovery flows are retained and visually refreshed.
- Backend support overlaps the team's booking/group/mock-payment workstream; review the shared contract with Sathish rather than implementing a second payment model on another branch.
- Integrated `origin/Ryoji` at `2450d87` and `origin/feat/seat-allocation` at `abfb73b` into this feature branch, not directly into main. Original contribution history is preserved. Tejas's integration adds persisted layouts/runs, ticket/export endpoints, frontend connections and an accessible-seat reservation fix.
- `PENDING_ALLOCATION` means a recorded booking awaiting allocation, not an assigned seat or a completed seat-map ticket.
- The original README contains early manual seat-selection/payment-provider plans. The submitted proposal governs: mock payments, deferred allocation, campus rooms plus constrained external templates. No real payment gateway, AR or advanced ML has been added.
- Existing records are not retrospectively relabelled as payment successes. Legacy bookings without a recorded mock-success attempt are flagged and excluded from new allocation. Old events default to unconfirmed physical identifiers.
- Three campus-named profiles are illustrative demo subsets. Physical verification and replacement with accurate room mappings remain necessary for the proposal's 3–5 mapped-campus-room requirement.

## Run in VS Code (PowerShell)

Open this repository folder. Start Docker Desktop, then:

```powershell
$env:FRONTEND_PORT = '5174'
$env:BACKEND_PORT = '8081'
docker compose -p tixora-sprint3 up --build -d
docker compose -p tixora-sprint3 ps
```

Open http://localhost:5174. Compose now waits for backend HTTP readiness before starting the frontend. A cold build/start can take several minutes; inspect `docker compose -p tixora-sprint3 logs --tail 30 backend` if needed. Both application ports are bound to localhost for this development demo.

Demo organiser: `committee.demo@uni.sydney.edu.au` / `OrganiserDemo1!` (existing development seed, not production credentials). Society name when publishing: **USYD Coding Society**. For an attendee account, use Sign up with a demo `@uni.sydney.edu.au` email and an 8–72-character password. These demo accounts do not verify ownership of an email address.

Prepared demo attendees (created by the smoke script): `sprint3.attendee@uni.sydney.edu.au` and `sprint3.friend@uni.sydney.edu.au`, password `TixoraDemo3!` for both. These are local synthetic accounts only.

Demo sequence: organiser dashboard → Create event → save draft → configure external template → save layout → publish. Attendee → discover event → group/individual booking → mock success → My bookings. Organiser → Manage event → confirm close registration → Run allocation → anonymous CSV. Attendee → View ticket & seat map. The saved layout includes blocked seats, accessible spaces and an aisle.

Confirm physical identifiers **only for a venue where that is substantiated**, or an explicitly labelled synthetic demo fixture. Campus demo profiles are not verified real room maps. To create a fresh synthetic end-to-end demo and check its APIs:

```powershell
.\scripts\smoke-sprint3.ps1
```

The script prints the new ticket URL and verifies draft/layout/publish, checkout outcomes, group joining, retries, closure, allocation, ticket privacy and anonymous export. Each run creates a new labelled demo event and retains it for inspection.

Stop without deleting data:

```powershell
docker compose -p tixora-sprint3 stop
```

For frontend development, use Node 22.12+ and run `npm ci` then `npm run dev` in `frontend`. Its Vite proxy defaults to backend port 8080; use the default Compose ports for that workflow, or set `VITE_API_PROXY_TARGET=http://localhost:8081`.

## Before the team deadline

| Target | Remaining work / suggested existing owner |
|---|---|
| 15 Oct | Team review of the integrated feature branch and shared booking/layout contracts; merge after review. |
| 18 Oct | Replace campus demo subsets with 3–5 physically verified room maps, accurate capacities and identifiers. |
| 21 Oct | Review allocation policies and group-split explanations with the allocation owner; rehearse blocked/accessibility/aisle edge cases. |
| 23 Oct | Full end-to-end rehearsal for both venue paths; reconcile any pre-existing unverified bookings and verify export privacy. |
| 24 Oct | Freeze features, fix integration defects, measure the proposal's ≤10-second allocation target for up to 300 attendees, complete accessibility and browser checks. |
| 25 Oct | Final demo, accurate Jira contribution evidence and team retrospective. |

These are proposed coordination dates, not claims that teammates have accepted new assignments. Keyboard and responsive checks are not a WCAG conformance certification. No zero-lag guarantee is made. Campus data verification, production hardening and final team acceptance remain outstanding.

## Verification evidence (12 October)

- Frontend: 73 tests across 11 files; production build succeeds.
- Backend: 120 tests, including ownership, checkout retries, concurrent capacity, layout locking, ticket privacy, general admission and allocation rollback.
- Controlled 300-attendee benchmark (final run): 78 ms for the algorithm, 1,147 ms for allocation plus database persistence on this development machine. Fixture setup is excluded; these are not network latency or production SLA measurements.
- Live Docker smoke walkthrough passed: draft/layout/publish, decline/cancel/success, safe retries, private group, close/allocate, private seat ticket and anonymous CSV. The latest local demo ticket is `/bookings/5/ticket` (event 33); generated IDs will differ on other databases.
- Main JavaScript approximately 70 KB gzip; booking, seating and ticket pages are separate lazy-loaded chunks. Production gzip and immutable hashed-asset caching enabled.
- Frontend dependency audit: zero reported vulnerabilities after upgrading the toolchain/router. Node 22.12+ required.
- Original Downloads checkout remains untouched; this branch uses its own worktree, Compose project, database volume and dedicated test database.
