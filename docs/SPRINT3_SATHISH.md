# Sathish's Sprint 3 backend work

## Discovery

`DataSeeder` supplies 30 USYD and wider Sydney demonstration events. It adds only missing demo fixtures when the event table already contains data, and a second run does not duplicate or change existing events. `EventService.listEvents` returns published Sydney events in chronological order and combines scope, organiser type, category and date filters. It excludes past events unless `includePast=true`. Both list and detail responses attach the current caller's `likedByMe` flag and the total like count. The combined four-filter and Sydney-only cases are covered by `EventControllerTest`.

## Booking and checkout

`POST /api/events/{eventId}/mock-checkout` accepts an idempotency key, booking options and one simulated outcome: `SUCCESS`, `DECLINED` or `CANCELLED`. These outcomes are chosen in the demo interface; the API does not collect card details. `MockCheckoutService` locks the event row and runs at `READ_COMMITTED` isolation. It checks the key before creating a new attempt. A retry with the same user, event, normalised options and outcome returns the original result; changed details return `409`.

Only `SUCCESS` calls `BookingService.createBooking`. That service checks publication, future date, open registration, one booking per attendee, capacity and the event-scoped group code. A successful booking and its checkout attempt commit in the same transaction. Declines and cancellations record an attempt with no booking, so neither uses capacity or enters allocation. The legacy `POST /api/events/{eventId}/bookings` route delegates to a simulated success through the same checkout service.

The updated historical-booking path recognises a booking whose idempotency key has no checkout-attempt row. A retry by its original user for its original event returns the existing booking. It creates no new booking and invents no historical payment record. Other users, other events, declined outcomes and cancelled historical bookings receive `409`. New checkout attempts still use the stricter fingerprint rule.

The mock-checkout response for such a historical retry has `paymentRef: null`; the legacy booking route still returns its usual `BookingResponse`.

Historical bookings do not store the original request fingerprint, so their option fields cannot be compared with a retry payload. The existing booking is returned unchanged; its group and accessibility values are never rewritten.

Groups require organiser-confirmed seat identifiers. Creating and joining are mutually exclusive. Joining checks for a non-cancelled booking with the code **in the requested event**, so a code from another event cannot be used. The event-row lock serialises the capacity check and creation of the final place; the concurrent checkout test verifies one success and one conflict when capacity is one.

The organiser's close-registration action sets both the registration and event state to closed. Allocation export contains active bookings only. `BOOKINGS` stores the final `PENDING_ALLOCATION` or `ALLOCATED` state and seat assignment; `CHECKOUT_ATTEMPTS` stores the mock outcome and replay identity. The relationships and uniqueness rules are shown in [ERD.md](ERD.md).

The backend test profile uses MySQL and defaults to the dedicated `eventticketingsystemtest` database on port 3306. Override its connection with `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`. Tests recreate their tables, so use a test database rather than the application database.

## API contract

`GET /api/events` accepts optional `scope` (`ALL`, `SYDNEY`, or `USYD`), `organiserType`, `category`, `date` (`YYYY-MM-DD`), and `includePast` parameters. The filters combine. `GET /api/events/{id}` returns one published event or `404`. Both responses include `likedByMe` and `likeCount`.

`POST /api/events/{id}/mock-checkout` requires a bearer token and JSON such as:

```json
{
  "idempotencyKey": "one-unique-key-per-attempt",
  "outcome": "SUCCESS",
  "createGroup": false,
  "joinGroupCode": null,
  "accessibilityRequired": false
}
```

The response contains `paymentRef`, `outcome`, and `booking`. `booking` is `null` for `DECLINED` and `CANCELLED`; it is present for `SUCCESS`. A retry must send the same key, outcome, and booking options. Changed details, owner, or event return `409`. A new simulated payment attempt uses a new key. No card data is accepted or stored.
