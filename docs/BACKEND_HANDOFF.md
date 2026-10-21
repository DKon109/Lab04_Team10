# Event and Venue Backend Handoff

Last updated: 21 October 2026

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

- Ryoji is implementing the campus venue catalogue using the existing
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
