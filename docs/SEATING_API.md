# Persisted seating integration

The original allocator from `feat/seat-allocation` is reused, not presented as new Tejas-only work. Ryoji's draft/owner/publish/close APIs are also integrated. This slice supplies the missing persistence, HTTP and frontend connections.

## Routes

| Method / route | Access / behaviour |
|---|---|
| GET `/api/organiser/events` | Owner's event list; optional draft/published/closed filter |
| PUT `/api/events/{id}` | Owner-only draft details editing |
| PATCH `/api/events/{id}/status` | Owner-only draft → published → closed |
| GET `/api/organiser/seating-profiles` | Three explicitly unverified campus demo profiles |
| GET `/api/organiser/events/{id}/seating` | Owner-only event/layout/run/confirmed-booking summary |
| PUT `/api/organiser/events/{id}/seating` | Owner-only layout configuration while draft and without bookings |
| POST `/api/organiser/events/{id}/allocate` | Owner-only, after closure; returns existing run on retry |
| GET `/api/bookings/{id}/ticket` | Booking owner only; wrong owner receives 404 |
| GET `/api/organiser/events/{id}/allocation/export` | Owner-only CSV after allocation; no names, emails or user IDs |

Layout request example:

```json
{
  "source": "EXTERNAL",
  "template": "FRONT",
  "rows": 3,
  "seatsPerRow": 4,
  "aisleAfter": 2,
  "blockedCodes": ["B4"],
  "accessibleCodes": ["A1"],
  "identifiersConfirmed": true
}
```

Templates: FRONT, FAN, CENTRAL. Up to 20 rows, 20 seats per row and 300 total positions. Aisle 0 means none. All supplied codes must exist; blocked and accessible sets cannot overlap. Usable-seat count becomes event capacity. Saving increments the layout version; publication locks it. For CAMPUS_DEMO supply a returned `roomId`; server-side preset dimensions override client dimensions.

## Invariants

- Layout/status/checkout/allocation writes serialise on the event row. Allocation writes seat assignments and its run record atomically.
- Only active bookings linked to a recorded mock SUCCESS attempt participate. Unverified legacy bookings remain visibly flagged; they are not silently considered paid.
- A database uniqueness constraint protects `(event_id, seat_code)`; general admission has null seat labels.
- An aisle separates allocator sections, so a contiguous block cannot cross it. Blocked seats are excluded. Accessible capacity is reserved for later groups even when an earlier group would otherwise prefer a contiguous block using it.
- The allocator is deterministic and uses the existing size-first heuristic. A split explanation means no suitable contiguous block remained at that stage; it is not a proof of globally optimal placement across every possible group ordering.
- On failure, no run or partial assignments are committed. Successful repeated allocation returns the original run; no reshuffling is offered.
- General-admission tickets can be finalised after closure without inventing physical seat numbers.
- Stored timestamps are truncated to MySQL microsecond precision so replayed results do not differ only because of database rounding.
- Maps are schematic, not a surveying tool. Campus profiles remain demo data pending physical verification.

## Validation

`SeatingIntegrationTest` covers the full lifecycle, owner restrictions, private tickets, locking, rollback, all template/profile variants, general admission, anonymised export and 300 persisted assignments. `SeatAllocationServiceTest` covers deterministic/contiguous/split results, accessible reservation and the 300-attendee algorithm benchmark. `scripts/smoke-sprint3.ps1` exercises the real Docker HTTP endpoints in a localhost-only synthetic demo.
