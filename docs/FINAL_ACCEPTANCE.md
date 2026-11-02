# Final integration acceptance — 2 November 2026

Owner of this integration pass: Tejas Singh. Branch: `tejas-final-completion`.
Teammate PRs #24 and #25 were integrated locally without conflicts, reviewed
against the combined tree, and merged to main in that order. Their original
authors and commits remain in Git history. #25 was retargeted from the stacked
test branch to main after #24 merged.

## Changes in this final pass

- Owner management now returns and displays the saved `managedMap`, including
  campus geometry, instead of claiming no map exists or offering an incompatible
  legacy-grid editor.
- Booking responses expose `generalAdmission`; checkout, My bookings and private
  tickets distinguish seatless admission from pending assigned seating.
- External event creation passes aisle, blocked-seat and accessible-seat settings
  through to the saved layout. Invalid template dimensions clear the selection
  rather than silently retaining an older valid layout. Limits remain 20 rows,
  20 seats per row and 300 positions for external grids.
- Draft details can be edited when a saved campus map has more than 300 seats.
  Map-derived capacity/identifiers are locked in the details form and capacity is
  omitted from that update, matching the backend's existing ownership of them.
- `/organiser` leads to the dashboard. Ticket navigation clears old ticket data
  while a different booking loads.
- The allocation search caches immutable contiguous-candidate scores, deduplicates
  identical candidate placements before evaluation, and skips enumeration when
  one group must occupy every remaining valid seat. Existing deterministic ranking,
  physical constraints, fixed assignments and split semantics remain in place.
- Updated the Vitest lockfile to 4.1.12, resolving the two reported moderate
  development-tool advisories without changing production dependencies.

## Proposal traceability

| Proposal requirements | Implementation / evidence |
| --- | --- |
| FR-01–02 account/session flows | AuthPage, AuthContext, auth API; registration/login/current-user/protected-route tests |
| FR-03–04 discovery, details, booking entry | EventsPage, useEvents/useLikes, EventDetailsModal, BookingPage; event/filter/likes/recommendation and attendee tests |
| FR-05–08 organiser and attendee interfaces | OrganiserDashboard, five-stage EventCreationWizard, VenuePicker, EventSeatingPage, MyBookingsPage, TicketAndSeatMapView; wizard, seating journey and draft-map editing tests |
| FR-09–10 roles, ownership and event lifecycle | SecurityConfig, EventService; access, event, publication, closure and concurrency tests |
| FR-11–12 campus catalogue and external layouts | CampusVenueTemplateService, external-template services, SeatingMapManagementService and legacy grid; three-campus simulations and template/persistence tests |
| FR-13–16 catalogue, seeded data, filters and liked states | DataSeeder, EventService, EventLike repositories, recommendation service; seeder, event and likes API tests |
| FR-17–20 allocation, split reasons, tickets, metrics and exports | SeatAllocationService, SeatingService, AllocationEvaluation, AnonymousSeatingPlanRenderer; allocation, benchmark, export, ticket/privacy and uniqueness tests |

## Fresh validation

- Backend full suite: **311 tests, zero failures/errors/skips**, Maven BUILD SUCCESS.
  Java 21.0.9 on Windows 11 amd64, 8 reported processors; MySQL 8.4 in a dedicated
  disposable test container, localhost port 3308. Tests use `create-drop` only on
  `tixora_sprint3_test`, never the application database.
- Frontend full suite: **95 tests across 15 files, all passed**. Production Vite
  build passed (main JavaScript about 81.44 KB gzip); npm audit reports zero
  vulnerabilities. Node 22.12+ is required.
- All **20 benchmark cases passed the ten-second threshold**, ranging from
  **242 to 7,469 ms** for validated inputs through flushed persisted assignments.
  Fixtures cover FRONT, FAN, CENTRAL, CAMPUS_DEMO, MANAGED and TABLES, both
  allocation strategies, varied groups and fixed positions, up to 300 attendees.
  Setup, HTTP delivery and final transaction commit are outside that timer.
- All **12 campus full/half-house simulations passed correctness checks** for
  Everest, Reginald and York, with both strategies. These larger fixtures include
  505/781-attendee full houses; the ten-second guarantee does not extend to them.
  Recorded campus allocation times ranged from 338 to 18,386 ms in this run.
- Initial tests under competing Docker builds and memory pressure exceeded the
  target; that run was interrupted. The passing run includes the search changes
  above and uses the dedicated test database while the demo containers are stopped.
  Do not claim the earlier failures never occurred or attribute all improvement
  to code alone.
- Surefire XML and campus simulation CSV/HTML artifacts are generated under
  `app/target/surefire-reports` and `app/target/allocation-simulation`. These are
  local generated evidence, excluded from Git. Reproduce them with the full suite.

Backend reproduction (PowerShell; test database must already be running):

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3308/tixora_sprint3_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:TEST_DB_USERNAME = 'root'
$env:TEST_DB_PASSWORD = 'sprint3-test-only'
cd app
.\mvnw.cmd test -B -ntp
```

Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build`, `npm audit`.

## Live application

From the repository root with Docker Desktop running:

```powershell
$env:FRONTEND_PORT = '5174'
$env:BACKEND_PORT = '8081'
docker compose -p tixora-sprint3 up --build -d
.\scripts\smoke-final.ps1
```

Open http://localhost:5174. The smoke script creates labelled synthetic records,
retains them for inspection, and prints ticket URLs. It exercises the original
checkout/retry/privacy journey plus three campus maps, all three external families,
general admission, closure, tickets and CSV/print output through HTTP and MySQL.
Demo organiser: `committee.demo@uni.sydney.edu.au` / `OrganiserDemo1!`.
Attendees: `sprint3.attendee@uni.sydney.edu.au` and
`sprint3.friend@uni.sydney.edu.au`, both `TixoraDemo3!`.

## Acceptance limits and report handoff

- Physical venue geometry, accessibility positions and installed identifiers need
  verification by someone with venue access. Published-plan-derived templates and
  synthetic confirmations do not establish this real-world condition.
- Computer Use stopped because it could not identify Chrome's current URL
  confidently enough for policy checks. No fresh final browser screenshots or
  keyboard walkthrough are claimed in this pass. Record those from the running
  final build before submission; older Sprint 2 screenshots are historical only.
- WCAG 2.2 AA is a target; the application has labelled controls, focus styles and
  textual seat information, but this pass is not a conformance certification.
- The proposal's conceptual QR-code uniqueness/check-in and separate domain
  entities differ from the delivered reference-based private ticket model. There
  is no implemented door-scanning/check-in workflow. Real payments/refunds and
  university/USU integration remain outside the stated MVP scope.
- `docs/FINAL_TEJAS_REPORT.md` contains Tejas's Section 1 and evidence index. The
  Introduction is inserted into the supplied report, with Aadi's review pending.
  Section 3 was blank when inspected; Tejas's review of Aadi's prose is awaiting
  that draft. No teammate approval or identity confirmation has been fabricated.
- Relevant shared Jira context: SCRUM-45, SCRUM-64, SCRUM-67 and SCRUM-68 in
  `docs/BACKEND_HANDOFF.md`. This pass does not change their live statuses.

## Jira-ready completion note

Integrated and reviewed PRs #24/#25; fixed managed-map preview, general-admission
states, mapped-draft editing and external accessibility/blocked-seat configuration.
Optimised repeated allocation scoring while retaining constraints and deterministic
results. Fresh validation: 311 backend tests, 95 frontend tests, production build
and zero frontend audit findings; all 20 proposal-scale allocation benchmarks
passed within ten seconds. Added final HTTP acceptance script and proposal/evidence
matrix. Prepared Section 1 Introduction; Aadi review and final browser/physical venue
acceptance remain pending. Link the final integration PR as contribution evidence.
