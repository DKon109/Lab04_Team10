# Event and Venue Backend Handoff

Last updated: 17 October 2026

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

## Pending coordination

- Await Rafly's managed seating implementation and proposed USYD
  venue templates before adding competing layout formats or templates.
- Verify the source and physical room mapping of supplied templates
  before treating them as confirmed venue layouts.
- Resolve managed-map ticket display and post-allocation seat removal
  behaviour raised in PR #14 before relying on that integration.
- Coordinate existing-data and schema compatibility with Sathish.
- Verify event ownership, publication, booking, allocation and ticket
  retrieval against the agreed seating model after integration.