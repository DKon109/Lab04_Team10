# Event and Venue Backend Handoff

Last updated: 13 October 2026

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

## Pending coordination

- Await the seating implementation and specification being prepared
  by team members before changing venue data or layout formats.
- Confirm physical room mappings before replacing campus demo profiles.
- Coordinate existing-data and schema compatibility with Sathish.
- Verify the integrated organiser workflow and persisted state
  after the shared changes are available.
