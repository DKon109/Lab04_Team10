# Event and Venue Backend Handoff

Last updated: 26 October 2026

## Responsibilities

- Ryoji: event ownership, organiser event management, and backend
  venue data validation and integration.
- Rafly: seat allocation algorithms.
- Aadi: organiser and seating interfaces.
- Tejas: attendee interfaces and overall integration.
- Sathish: bookings, mock payments and database coordination.

Reuse existing team implementations and coordinate changes to
shared DTOs, entities and services before editing them.

## API references

- [Event management and integration notes](../INTEGRATION_NOTES.md)
- [Authentication API](../AUTH_API.md)
- [Seating API](SEATING_API.md)
- [Mock checkout API](MOCK_CHECKOUT_API.md)
- [Managed seating map API](SEATING_MAP_MANAGEMENT_API.md)

## Integration rules

- The backend derives event ownership from the authenticated user.
  Clients must not supply an owner ID.
- Event creation saves a draft. Publishing is a separate operation.
- Only the event owner with the organiser role can edit or change
  the event status.
- Closing an event also closes registration.
- Follow the existing seating API contract for layout configuration.
- Campus demo profiles must not be treated as verified physical layouts.

## Persistence regression test

Run from the `app` directory against a dedicated test database:

```bash
TEST_DB_URL='jdbc:mysql://localhost:3306/eventticketingsystemtest?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
TEST_DB_USERNAME=root \
TEST_DB_PASSWORD='' \
./mvnw -Dtest=GeneralAdmissionPersistenceTest test
```

Use your local test database credentials. The test profile uses
`create-drop`, so do not point it at a development or production database.

`GeneralAdmissionPersistenceTest` verifies that a general admission
booking, mock payment confirmation and allocation result can be retrieved
in a separate transaction after commit. Retrying allocation preserves
the original completion timestamp and does not create another booking.

The test removes its committed fixture data in a `finally` block.
It does not verify application restart or physical seating layouts.

## Managed seating integration

- PR #14 is merged. Reuse the managed seating implementation rather
  than introducing another map format.
- Ticket responses include `managedMap` for managed seating maps.
  The ticket frontend supports both managed maps and legacy layouts.
- Seat removal after allocation is rejected. Moving a booking to a
  valid replacement seat remains supported.
- Managed maps require explicit `identifiersConfirmed: true` before
  physical seats can be assigned.
- Regression coverage checks that another organiser cannot delete
  a managed map and that published maps cannot be replaced or deleted.
- The managed-map ticket test flushes and clears the persistence
  context before checking ticket retrieval and booking privacy.
  This verifies database reload within one transaction, not an
  application restart.

## Campus venue templates

- Ryoji's campus venue catalogue uses the existing
  managed seating map format.
- `GET /api/organiser/venue-templates` requires a bearer token with
  the `COMMITTEE_MEMBER` role.
- The response is an array containing `id`, `name`, `sourceUrl`
  and `map`.
- Available templates:
  - `usyd-seymour-everest`: Seymour Centre - Everest Theatre,
    with 505 unique seat codes.
  - `usyd-seymour-reginald`: Seymour Centre - Reginald Theatre,
    with 148 unique seat codes.
  - `usyd-seymour-york`: Seymour Centre - York Theatre,
    with 781 unique seat codes.
- `sourceUrl` identifies the official seating plan used for the
  seat numbering. Display coordinates are schematic, not measured
  room dimensions.
- Pass the selected template's `map` object as the request body to
  `PUT /api/organiser/events/{eventId}/seat-map`.
  Existing ownership and draft editing restrictions apply.
- The template starts with `identifiersConfirmed: false` and
  `attendeesChooseSeats: false`. Do not automatically change these
  flags when selecting or saving the template.
- In the Everest template, A12 and A26 are assumed accessible
  positions for coursework demonstration only, not verified
  wheelchair spaces.
  Both have `accessible: true` and a note stating this assumption.
- Other seats retain `accessible: false`; this is not a verified
  assessment of their accessibility.
- The 505-seat count is unchanged. This demo assumption does not
  model seat removal or capacity changes for wheelchair spaces.
- Physical layout and accessibility verification remain required
  before treating the template as a confirmed seating layout.
- Tests cover seat count, unique codes, missing seats and rows,
  organiser-only access, and database reload after saving.

### Reginald layout details

- Seat numbering follows the official Reginald seating plan:
  rows A-H contain seats 1-16, and row J contains seats 1-20.
  There is no row I.
- Rows A-H are split between seats 8 and 9 to preserve the central
  aisle. Row J remains a single continuous block.
  The template contains 17 row elements and 148 seats.
- Left, Right and Rear are application section names, not official
  venue section labels. Display coordinates are schematic.
- All seats currently have `accessible: false`; accessibility has
  not been verified. This does not mean the venue is inaccessible.
- `identifiersConfirmed` remains false after saving. Template
  selection must not imply physical verification.
- Tests verify API retrieval and preservation of seat codes,
  sections, rows, coordinates and accessibility flags after a
  persistence-context reload within the same transaction.
- SeatingIntegrationTest and CampusVenueTemplateServiceTest passed
  together: 61 tests, no failures or errors, on 21 October 2026.

### York layout details

- The template contains 781 seats across 12 rows:
  A-H and J-M. There is no row I.
- Each row contains five separate blocks, producing 60 row elements.
  Seat-number ranges vary by row; gaps must not be filled automatically.
- Block1-Block5 are application section names, not official venue labels.
  Blocks preserve aisle separation, but display coordinates do not
  reproduce the theatre's angles, dimensions or surrounding-stage shape.
- Row-specific boundaries include A63, C66, D67 and K88.
  A64, C67, D68 and K89 are absent; L89 and M89 are present.
- All seats currently have `accessible: false`; accessibility remains
  unverified. This is not a determination that the venue is inaccessible.
- `identifiersConfirmed` remains false after saving.
  Catalogue availability does not imply physical verification.
- Tests cover unique seat codes, row/block structure, API retrieval,
  and preservation of seat properties after a persistence-context
  reload within the same transaction.

### Latest catalogue regression results

- SeatingIntegrationTest: 58 passed.
- CampusVenueTemplateServiceTest: 6 passed.
- Total: 64 tests, no failures or errors, on 21 October 2026.

## Front-stage external template API

- `POST /api/organiser/venue-templates/front-stage` requires a bearer
  token with the `COMMITTEE_MEMBER` role.
- Generates a reusable managed seat map without saving it to an event.
- Example request:

```json
{
  "name": "Front-stage demo",
  "rows": 2,
  "seatsPerRow": 6,
  "aisleAfter": 3,
  "blockedCodes": ["B6"],
  "accessibleCodes": ["A1"]
}
```

- Rows and seats per row must each be between 1 and 20.
  The total number of seats before excluding blocked seats must not
  exceed 300.
- `aisleAfter: 0` means no aisle. Otherwise, the aisle must fall
  between seats within the row.
- Seat codes are generated deterministically, starting at A1.
  Each row is split into separate elements at the aisle boundary
  so allocation does not treat seats across the aisle as contiguous.
- Blocked and accessible codes must refer to generated seats.
  Duplicate codes, overlapping blocked/accessibility flags and
  layouts with no available seats are rejected.
- The example produces 12 seats, four row elements and a STANDARD
  ticket capacity of 11. Display coordinates are schematic.
- Accessibility flags are supplied by the organiser; the generator
  does not verify physical venue accessibility.
- The response is a `SeatMapRequest` object, not a catalogue wrapper.
  Pass the complete response body to
  `PUT /api/organiser/events/{eventId}/seat-map` to save it.
  Existing ownership and draft editing restrictions apply.
- Generated maps start with `attendeesChooseSeats: false`,
  `autoMatchTicketCapacities: true` and `identifiersConfirmed: false`.
  Generation and saving do not confirm that physical seat identifiers
  are installed or verified.
- Integration tests cover organiser access, invalid API input,
  saving and reloading, and a two-person group allocated to adjacent
  seats on one side of an aisle.
- This implements the front-stage subset of SCRUM-49.
  Fan/semicircle and central-stage generation in the common managed
  map format remain outside this scope.

## External venue template families

- `POST /api/organiser/venue-templates/external` requires the
  `COMMITTEE_MEMBER` role.
- Uses the same request fields as `/front-stage`, with an additional
  `template` field: `FRONT`, `FAN` or `CENTRAL`.
- FRONT preserves the existing front-stage layout.
- FAN arranges rows in schematic arcs and preserves aisle separation.
- CENTRAL places rows on the North and South sides of the stage.
  It requires at least two rows; an odd extra row goes to the North.
- All formats return the existing `SeatMapRequest` representation.
  Generation does not save data. Save the response through
  `PUT /api/organiser/events/{eventId}/seat-map`.
- Seat codes, row information, sections, blocked seats and accessibility
  flags are preserved. Coordinates are schematic.
- Generated maps retain `identifiersConfirmed: false`.
  Accessibility flags are organiser-provided, not physically verified.
- Tests cover organiser-only access, invalid requests, database reload
  within one transaction, and accessible group allocation without
  crossing an aisle.
- Related regression tests passed on 23 October 2026:
  SeatingIntegrationTest (68), ExternalVenueTemplateServiceTest (5),
  FrontStageTemplateServiceTest (4), CampusVenueTemplateServiceTest (6).
  Total: 83 tests, no failures or errors.
- Frontend preview and template-selection integration still require
  coordination with Aadi; these backend tests do not verify the UI.

## Venue persistence guarantees

- Duplicate seat codes, including case-only differences such as
  A1 and a1, are rejected when replacing a managed map.
- Regression coverage verifies that this rejection preserves the
  saved map, its version, seat data, ticket capacities, event capacity
  and physical-identifier confirmation flag.
- `VenueTemplatePersistenceTest` verifies Everest, Reginald, York,
  FRONT, FAN and CENTRAL after the saving transaction commits.
- A separate transaction reloads each map and checks that its saved
  contents and event capacity are unchanged.
- External-template checks also verify accessible seat A1, blocked
  seat C4 and the resulting usable capacity.
- Templates remain unconfirmed after saving and reloading.
- These checks verify persistence across committed transactions,
  not application restart, physical venue accuracy or frontend preview.
- The test removes its committed fixture data in a finally block.

Validation on 26 October 2026:

- Seating and template regression suite: 84 tests passed.
- `VenueTemplatePersistenceTest`: 6 tests passed in a separate run.

Run the persistence checks from the app directory using the dedicated
test database configuration:

    ./mvnw -o -Dtest=VenueTemplatePersistenceTest test

Frontend template selection, preview consistency and the complete
organiser-to-attendee journey still require integration verification.

## Registration closure and mock checkout

- Closing an event through the event status service also sets
  registration to CLOSED.
- Event closure and mock checkout acquire a database write lock
  on the same event, serialising these operations.
- If closure commits first, a new successful checkout request is
  rejected with HTTP 409 and "Registration is closed for this event".
  No booking or checkout attempt is created.
- If checkout commits first, closure preserves the successful
  booking and its checkout record. The booking remains
  PENDING_ALLOCATION until seat allocation.
- Retrying a successful checkout after closure with the same
  idempotency key and unchanged request returns the existing result
  without creating another booking or checkout record.
- PENDING_ALLOCATION means a confirmed booking awaiting allocation,
  not a payment awaiting confirmation.
- The current mock checkout creates a booking on SUCCESS.
  These tests do not cover a real payment provider or asynchronous
  payment callbacks.

Regression coverage:

- RegistrationClosureConcurrencyTest covers both transaction orders.
- MockCheckoutControllerTest covers successful retries after closure.
- These tests and BookingControllerTest passed together:
  27 tests, no failures or errors, on 21 October 2026.

## Pending coordination

- Verify the Everest Theatre physical layout and accessible seat
  identifiers before marking the template as confirmed.
- Coordinate catalogue selection and verification messaging with
  Aadi, reusing the existing managed seating editor.
- Coordinate allocation compatibility with Rafly and existing-data
  and schema compatibility with Sathish.
- Verify the complete organiser and attendee workflows once the
  template selection interface is integrated.
- Confirm with Sathish that the documented closure and retry
    behaviour matches the agreed mock-payment rules.
- Verify Aadi's create/edit/preview/publish/close workflow against
  the backend, including display of server validation errors.

## Template integration contract and examples

The following describes the implemented contract on Ryoji at `94cff70`,
including merged main commit `5a1488c`. It is not evidence that all
downstream consumers have signed off on this contract.

All three template endpoints require a COMMITTEE_MEMBER bearer token.
They return HTTP 200 and do not save or publish an event:

| Method and endpoint | Request | Response |
| --- | --- | --- |
| GET `/api/organiser/venue-templates` | No body | Array of `{id, name, sourceUrl, map}` catalogue entries |
| POST `/api/organiser/venue-templates/front-stage` | Front-stage example above | A `SeatMapRequest` object |
| POST `/api/organiser/venue-templates/external` | Example below | A `SeatMapRequest` object |

```json
{
  "name": "External integration fixture",
  "template": "FAN",
  "rows": 3,
  "seatsPerRow": 4,
  "aisleAfter": 2,
  "blockedCodes": ["C4"],
  "accessibleCodes": ["A1"]
}
```

Expected generated response properties for this fixture are 12 seats,
six row elements, STANDARD capacity 11, accessible A1, blocked C4,
`identifiersConfirmed: false`, `attendeesChooseSeats: false` and
`autoMatchTicketCapacities: true`. FRONT and CENTRAL accept the same
fixture with the corresponding template value; their coordinates differ.
Use the full generated object, not just these properties, when saving.

For a campus selection, save the catalogue entry's `map`; for an external
selection, save the generated response directly. Both use PUT
`/api/organiser/events/{eventId}/seat-map`. Its response is a `SeatMapView`
with the saved version, elements, seat status and ticket capacities.
See [the complete managed-map request example and response fields](SEATING_MAP_MANAGEMENT_API.md).
Do not interchange this object with the legacy `/seating` grid payload.
Do not infer physical confirmation from successful generation or saving.

| Result | Handling |
| --- | --- |
| 400 | Correct invalid input; external generators reject unknown/duplicate seat codes, overlapping flags, invalid aisle positions and unusable layouts. |
| 401 | Obtain valid authentication before retrying. |
| 403 | Require the organiser role; saving additionally requires event ownership. |
| 404 | Check the event/map identifier when accessing a saved map. |
| 409 | Resolve lifecycle or booking/allocation locks before editing a map. Do not silently overwrite or create another event. |

Event lifecycle and ownership errors are documented in
[event management integration notes](../INTEGRATION_NOTES.md).
Publishing uses PATCH `/api/events/{id}/status` with
`{"status":"published"}`; closing uses `{"status":"closed"}`.
Publishing requires a future event date and a saved layout when physical
identifiers are confirmed. Closing also closes registration.

## Data setup and shared-file ownership

- Campus templates are generated from code; these template changes add
  no database entity or separate migration. Saving uses the existing
  managed-map tables shown in [the shared ERD](ERD.md).
- Development uses DB_URL, DB_USERNAME and DB_PASSWORD and currently
  sets Hibernate `ddl-auto=update`. This is the repository configuration,
  not a claim that an existing-data migration has been reviewed.
- Tests use TEST_DB_URL, TEST_DB_USERNAME and TEST_DB_PASSWORD and
  `ddl-auto=create-drop`. Use only the dedicated disposable test database.
- Committee accounts need a society_name matching the event organiser.
  See the existing-data setup notes in [INTEGRATION_NOTES.md](../INTEGRATION_NOTES.md).
- Ryoji maintains template controllers, generators, validation evidence
  and this handoff. Aadi owns organiser UI and preview consumption;
  Tejas owns attendee UI and end-to-end coordination; Rafly owns
  allocation, metrics and outputs; Sathish owns booking/payment and
  database/schema coordination. Coordinate shared DTO/entity/service
  changes with the affected owner rather than adding parallel formats.

## Review and verification references

| Evidence | Scope and result |
| --- | --- |
| [PR #20](https://github.sydney.edu.au/2026S2-Object-Oriented-App-Frameworks/Lab04_Team10/pull/20) | Merged event/venue backend work, campus and external templates and earlier integration coverage. |
| [PR #16](https://github.sydney.edu.au/2026S2-Object-Oriented-App-Frameworks/Lab04_Team10/pull/16) | Merged organiser UI; does not establish that the managed template endpoints are connected. |
| [PR #21](https://github.sydney.edu.au/2026S2-Object-Oriented-App-Frameworks/Lab04_Team10/pull/21) | Merged runtime metrics and anonymous printable seating output. See [allocation/output contract](SEAT_ALLOCATION_AND_OUTPUTS.md). |
| [e5cfe9a](https://github.sydney.edu.au/2026S2-Object-Oriented-App-Frameworks/Lab04_Team10/commit/e5cfe9a) | Invalid managed-map replacement preserves the original map and event settings. Related template/seating suite: 84 passed on 26 October. |
| [66bbc49](https://github.sydney.edu.au/2026S2-Object-Oriented-App-Frameworks/Lab04_Team10/commit/66bbc49) | Six committed-transaction persistence cases passed on 26 October; campus IDs and external fixture are defined in VenueTemplatePersistenceTest. |
| [94cff70](https://github.sydney.edu.au/2026S2-Object-Oriented-App-Frameworks/Lab04_Team10/commit/94cff70) | Documents those persistence guarantees and their limits. |

The three follow-up commits above are on Ryoji; their review/merge is
separate from the already merged PR #20. The 84-case result and six-case
result are separate runs, not a claimed single full-suite execution.
Reproduce the related regression run from `app` with:

```bash
./mvnw -o -Dtest=SeatingIntegrationTest,ExternalVenueTemplateServiceTest,FrontStageTemplateServiceTest,CampusVenueTemplateServiceTest test
./mvnw -o -Dtest=VenueTemplatePersistenceTest test
```

## Outstanding decisions and completion boundary

| Owner / coordination | Outstanding item | Impact |
| --- | --- | --- |
| Aadi, with Ryoji | Connect the managed campus/external template endpoints to selection/preview and confirm response/error handling. The current wizard still saves through legacy `/seating`. | Managed template preview/save consistency is not yet demonstrated in the UI. |
| Ryoji, with the team | Resolve physical layout/accessibility evidence or explicitly agree a revised demonstration scope. Assumed Everest accessible positions remain labelled assumptions. | Do not claim verified physical campus journeys from the current demo data. |
| Sathish, with Ryoji | Confirm existing-data setup and the documented closure/retry rules; record any required migration decision. | Configuration and tests alone do not prove team agreement or compatibility with every existing database. |
| Rafly, with Ryoji | Confirm the representative venue fixtures used for the persisted benchmark and link the corresponding results. | Do not infer a measured campus/external benchmark from unit tests or runtime fields alone. |
| Tejas and Aadi, with Ryoji/Sathish/Rafly | Record the integrated campus/external and general-admission journeys against an identified shared commit. | SCRUM-67 end-to-end completion remains unverified. |
| Ryoji and affected reviewers | Review this handoff and the follow-up commits; record API/data decisions in the PR or linked issues. | SCRUM-68's final agreed/reviewed handoff condition is pending. |

The table is a handoff of owned dependencies, not a transfer of frontend,
allocation or payment implementation to Ryoji. Missing required Sprint 3
integration and physical-evidence conditions must not be relabelled as
Final hardening. Additional Final work can include broader browser and
environment coverage, repeatability checks and presentation polish after
the required journeys are established. This handoff is ready for review;
it does not assert that SCRUM-67 or SCRUM-68 is Done.

## SCRUM-66 publication and ownership verification

Implementation reference: Ryoji commit `1223693`.

| Acceptance area | Implementation and regression evidence |
| --- | --- |
| Protected event/layout changes | SecurityConfig restricts organiser routes to committee members. Services check event ownership. Tests cover unauthenticated callers, students, other organisers and managed-map deletion by a non-owner. |
| Publication prerequisites | Publishing requires a future date, a nonblank venue, positive capacity and a saved layout when physical identifiers are confirmed. Regression tests include eventWithZeroCapacityCannotBePublished, eventWithBlankVenueCannotBePublished and assignedSeatingEventCannotBePublishedWithoutLayout. |
| General admission versus assigned seating | Unconfirmed maps remain general admission. Seat assignment and group checkout require explicit physical identifier confirmation. Tests include unconfirmedManagedMapRemainsGeneralAdmissionAndRejectsSeatAssignments and groupRequiresConfirmedPhysicalIdentifiers. |
| Changes after publication/bookings/allocation | Event editing is draft-only. Layout replacement is blocked after publication or when bookings/allocation exist. Existing tests cover published/closed event editing, published-map replacement/deletion and allocated-seat removal. |
| Closure and existing bookings | Closing an event closes registration and rejects new bookings. Tests cover existing-booking preservation, successful checkout retries and both closure/checkout transaction orders. |

Validation on 26 October 2026:
138 tests passed, with no failures, errors or skipped tests.

Run from the app directory against the dedicated test database:

    ./mvnw -o -Dtest=SeatingIntegrationTest,LikesAndEventCreationApiTest,BookingControllerTest,MockCheckoutControllerTest,RegistrationClosureConcurrencyTest test

The zero-capacity and blank-venue cases failed before their respective
publication checks were added and passed after the fixes.

These results verify backend behaviour. Physical identifier confirmation
is an organiser assertion, not independent verification of a real venue.
Existing demo accessibility assumptions remain explicitly unverified.
Team agreement on shared lifecycle/database rules and UI error handling
remains a review item; this evidence does not claim that agreement.

## Local general-admission walkthrough — 27 October 2026

Reference checkout: Ryoji `a723284`, local development backend and Safari
frontend. This is a manual UI observation, not a new automated test run.
Fixture: event `31`, `SCRUM-50 UI Check`, 1 November 2026 at 18:00,
`Integration Test room`, Woolley demo subset (60 places), general admission.
The demo room is illustrative, not a verified physical venue.

Observed sequence:

1. The organiser wizard previewed the event and published it. The dashboard
   showed Published; management showed General Admission and locked venue
   configuration. This wizard creates, saves the layout and publishes in
   one submission; a separate draft-only save was not exercised.
2. The attendee booking screen allowed only an individual booking and
   explained that group seating requires confirmed physical identifiers.
3. Mock checkout succeeded without a real charge. Booking `1`, reference
   `BKG-D2F7867260BCD0C2`, appeared in My bookings and its private ticket page.
4. The organiser management screen showed 1 / 60 confirmed bookings.
   Confirming registration closure changed the event to Closed and retained
   that count. The attendee could still open the same booking's ticket.
5. A second attendee, `sprint3.friend@uni.sydney.edu.au`, directly opened
   `/events/31/book` after closure. The page displayed `Event not found: 31`
   and no booking form. This matches the public event lookup's restriction
   to Published events and demonstrates that the UI blocks this booking path.
6. The same second attendee directly opened `/bookings/1/ticket`, which
   belongs to the first attendee. The page displayed `Booking not found`
   without the event details or booking reference. The owner had previously
   been able to view this ticket after closure; the second attendee could not.

### UI defects to coordinate

| Owner | Reproduction and observed result | Expected behaviour |
| --- | --- | --- |
| Tejas | For this general-admission booking, checkout confirmation says seat allocation is pending; My bookings says AWAITING SEAT ALLOCATION; the ticket says to wait for closure/allocation while also stating that no physical seats are assigned. The message remains after closure. | Use general-admission-specific confirmation/status text consistently. Check BookingPage.jsx, MyBookingsPage.jsx and TicketAndSeatMapView.jsx. |
| Aadi | Closing this general-admission event shows Ready for allocation and a Run allocation button. The button was not exercised. | Make the general-admission next step explicit and avoid implying that physical seat assignment is required. Check EventSeatingPage.jsx with the agreed allocation contract. |

No frontend changes were made as part of this walkthrough. These are
handoff findings; they have not yet been sent to the owners or resolved.
The earlier observation of `/events` did not establish a redirect; step 5
records the subsequently verified direct navigation. No checkout POST was
submitted in that step and no HTTP status was captured. Backend rejection
of booking requests after closure is covered by the separate automated
regression evidence above. Live publication-validation error handling,
assigned-seat journeys and managed template consumption still require their
own UI evidence; this walkthrough does not establish full SCRUM-50/51/67
completion.
