# Mock checkout API — Sprint 3

Authenticated `POST /api/events/{eventId}/mock-checkout`:

```json
{
  "createGroup": false,
  "joinGroupCode": null,
  "accessibilityRequired": false,
  "idempotencyKey": "a-client-generated-uuid",
  "outcome": "SUCCESS"
}
```

Outcomes: `SUCCESS`, `DECLINED`, `CANCELLED`. These are deliberately user-selectable **simulation** outcomes, not proof of a real financial transaction. No price/card/payment-provider data is collected.

Response (HTTP 200): `paymentRef`, `outcome`, `booking` (null for decline/cancel). A successful booking uses the existing `BookingResponse` and `PENDING_ALLOCATION` status.

- A unique checkout key identifies one immutable request. Retries must preserve event, user, group/accessibility choices and outcome. Change the key for a genuinely new attempt, not for an ambiguous network retry.
- The event row is locked. Under READ_COMMITTED isolation, retry lookup, capacity/duplicate checks, booking creation and checkout-attempt persistence occur in one transaction. There is no partial successful attempt without its booking.
- Group codes are normalised; create and join are mutually exclusive. Group success requires `seatIdentifiersConfirmed=true` and joining requires an active group on the same event.
- Success is rejected for closed/past/full/already-booked events. Decline/cancel can be recorded without reserving capacity.
- The previous `POST /api/events/{id}/bookings` remains compatible (201 + BookingResponse), but now records an implicit mock-success attempt through the same service. It is not a real payment bypass.
- Existing bookings pre-dating this change are preserved; no fabricated historical payment attempts are backfilled.
- Errors: 400 invalid request, 401 unauthenticated, 404 event/group not found, 409 business conflict or key reuse. Key conflicts never return someone else's booking.
- Frontend retries preserve the request while the booking screen remains mounted. After reload, use My bookings to resolve an uncertain outcome; backend duplicate-booking protection remains effective.

Event creation adds optional `capacity` (1–300, default 100) and `seatIdentifiersConfirmed` (default false). Event responses expose that boolean. The boolean is an organiser attestation, not a substitute for the team's pending venue-map/layout-version model.

`GET /api/auth/me/bookings` is the existing private booking list. Existing close-registration/export routes remain unchanged; no allocation algorithm is implemented by this change.

For final deployment: disable development seeds, configure secrets, replace snapshot framework dependencies with an agreed stable version, and replace automatic schema update with reviewed migrations. Demo configuration is not production hardening.
