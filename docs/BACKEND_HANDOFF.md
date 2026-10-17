# Event and Venue Backend Handoff

Last updated: 18 October 2026

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
DB_URL='jdbc:mysql://localhost:3306/eventticketingsystemtest?useSSL=false&serverTimezone=UTC' \
DB_USERNAME=root \
DB_PASSWORD='' \
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

## Pending coordination

- Await Rafly's proposed USYD venue templates before adding
  competing venue templates.
- Verify the source and physical room mapping of supplied templates
  before treating them as confirmed venue layouts.
- Coordinate existing-data and schema compatibility with Sathish.
- Verify the complete organiser and attendee workflows with the
  supplied venue templates once they are integrated.