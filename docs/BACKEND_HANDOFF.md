# Event and Venue Backend Handoff

Last updated: 19 October 2026

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
- The initial template is `usyd-seymour-everest`:
  Seymour Centre - Everest Theatre, with 505 unique seat codes.
- `sourceUrl` identifies the official seating plan used for the
  seat numbering. Display coordinates are schematic, not measured
  room dimensions.
- Pass the selected template's `map` object as the request body to
  `PUT /api/organiser/events/{eventId}/seat-map`.
  Existing ownership and draft editing restrictions apply.
- The template starts with `identifiersConfirmed: false` and
  `attendeesChooseSeats: false`. Do not automatically change these
  flags when selecting or saving the template.
- Accessible seat identifiers have not been verified. The current
  `accessible: false` values do not establish that those seats are
  unsuitable for accessibility needs.
- Physical layout and accessibility verification remain required
  before treating the template as a confirmed seating layout.
- Tests cover seat count, unique codes, missing seats and rows,
  organiser-only access, and database reload after saving.

## Pending coordination

- Verify the Everest Theatre physical layout and accessible seat
  identifiers before marking the template as confirmed.
- Coordinate catalogue selection and verification messaging with
  Aadi, reusing the existing managed seating editor.
- Coordinate allocation compatibility with Rafly and existing-data
  and schema compatibility with Sathish.
- Verify the complete organiser and attendee workflows once the
  template selection interface is integrated.
