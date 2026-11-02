# Persisted seating integration

The seating APIs connect saved layouts, confirmed bookings, allocation, private
tickets and organiser outputs. Rafly's Member 5 backend scope is allocation,
split handling, ticket data, metrics and exports (FR-17–20). Layout and event
lifecycle APIs are integration points with the other backend responsibilities.

See [seat allocation and outputs](SEAT_ALLOCATION_AND_OUTPUTS.md) for the
requirement map and metric definitions, [managed seating maps](SEATING_MAP_MANAGEMENT_API.md)
for rows/tables and manual assignments, and [mock checkout](MOCK_CHECKOUT_API.md)
for booking confirmation and retry rules.

Authenticated requests use `Authorization: Bearer <token>`. Organiser routes
require the `COMMITTEE_MEMBER` role; event-specific management also requires
ownership of that event.

## Routes

| Method / route | Access / behaviour |
|---|---|
| GET `/api/organiser/events` | Owner's event list; optional draft/published/closed filter |
| PUT `/api/events/{id}` | Owner-only draft details editing |
| PATCH `/api/events/{id}/status` | Owner-only draft → published → closed |
| GET `/api/organiser/seating-profiles` | Three explicitly unverified campus demo profiles |
| GET `/api/organiser/events/{id}/seating` | Owner-only event/layout/run/confirmed-booking summary |
| PUT `/api/organiser/events/{id}/seating` | Owner-only layout configuration while draft and without bookings |
| POST `/api/organiser/events/{id}/allocate` | Owner-only, after closure; optional `strategy`; returns existing run on retry |
| GET `/api/organiser/events/{id}/allocation/metrics` | Owner-only evaluation of current assignments plus original runtime; allocation required |
| GET `/api/bookings/{id}/ticket` | Booking owner only; wrong owner receives 404 |
| GET `/api/organiser/events/{id}/allocation/export` | Owner-only CSV after allocation; no names, emails or user IDs |
| GET `/api/organiser/events/{id}/allocation/print` | Owner-only printable HTML/SVG seating plan after allocation |

## Configure a layout

Save the layout while the event is a draft and before active bookings or an
allocation run exist. The `GET` and `PUT` seating endpoints return `event`,
`layout`, `allocation`, `confirmedBookings`, `unverifiedLegacyBookings` and
`managedMap`. The `layout` field represents the legacy grid; `managedMap`
contains the owner's saved managed geometry when present. This lets the
management page preview the same persisted map used for allocation rather than
offering an incompatible legacy grid editor. The separate `/seat-map` endpoint
remains available. Booking responses also expose `generalAdmission` so attendee
status text never promises a physical seat for a seatless event.

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

`identifiersConfirmed` is the organiser's confirmation of physical seat labels.
Without it, allocation finalises general admission with null seat codes. A demo
profile is not evidence that a room has been physically verified. Delete an
existing managed map before switching back to the legacy grid API.

## Run allocation

Close registration before requesting allocation. Only active bookings linked
to a recorded mock-checkout `SUCCESS` participate; a `PENDING_ALLOCATION`
booking is confirmed and awaiting seating. Declined/cancelled attempts and
unverified legacy bookings are excluded.

```http
POST /api/organiser/events/{id}/allocate?strategy=PREFER_SAME_ROW
Authorization: Bearer <event-owner-token>
```

| Strategy | Behaviour |
| --- | --- |
| `DEFAULT` | Keep complete groups together where feasible; fallback balances contiguous subgroups and penalises additional placement units. |
| `PREFER_SAME_ROW` | Keep complete groups together where feasible; fallback favours one physical row, then fewer placement units and balanced subgroups. |

Omitting `strategy` selects `DEFAULT` for a new run. Both strategies respect
blocked seats, accessibility, aisles and fixed managed assignments. Members
at one table count as together, including across table sides. Otherwise,
togetherness requires one consecutive block in the same element, section and
row. Splits receive a stored `splitReason` exposed by tickets and exports.

The response is the persisted allocation run: `eventId`, `layoutVersion`,
`bookingCount`, `splitGroupCount`, `strategy`, `completedAt`, `elapsedMillis`
and evaluation fields. An identical retry returns that run. Omitting the
strategy on retry accepts the saved strategy; explicitly choosing a different
strategy returns `409`. Historical runs can have a null strategy.

## Retrieve tickets, metrics and exports

| Output | Contract |
| --- | --- |
| Private ticket | Booking-owner-only JSON containing `booking`, `eventDate`, `venue`, `paymentStatus`, `seatCode`, `splitReason`, `generalAdmission`, `layout` and `managedMap`. |
| Metrics | `groupCount`, `splitGroupCount`, `percentageGroupsTogether`, `averageSplitDistance`, `averageGroupContiguityPercentage`, `isolatedSeatCount`, `duplicateAllocationCount` and `elapsedMillis`. |
| CSV | Attachment `event-{id}-allocation.csv`; columns `booking_reference,group_reference,seat,split_reason`. General admission uses `GENERAL_ADMISSION`. |
| Printable plan | Attachment `event-{id}-seating-plan.html`, with self-contained SVG diagrams, assignment references and print styling. Open locally and use Print or Save as PDF. The server returns HTML. |

Tickets can be retrieved before allocation. Inspect `booking.status` and
`paymentStatus` before displaying a final ticket: `MOCK_SUCCESS` identifies a
recorded simulated success and `UNVERIFIED_LEGACY` does not confirm payment.
General admission has no physical seat label even after finalisation. The
ticket endpoint supplies data; it does not implement QR validation or check-in.

Metrics and both exports require a saved allocation run. Metrics evaluate
current confirmed assignments, so organiser seat moves can change the values;
`elapsedMillis` remains the original allocation timing. General-admission
seat/group metrics are null, duplicate count is zero and runtime is available.
Percentages are also null when there are no evaluated groups; clients should
show an appropriate not-applicable state.

Exports exclude attendee names, email addresses and user IDs. CSV retains the
stored group code in `group_reference`; printable plans replace it with local
aliases such as `G1` and omit free-form map notes. Exports are snapshots and
must be downloaded again after seat changes. Authenticated browser downloads
must send the bearer token.

## Errors

| Status | Relevant cases |
| --- | --- |
| `400` | Invalid layout input or unknown allocation strategy. |
| `401` | Missing, invalid or expired authentication. |
| `403` | Caller lacks the organiser role or does not own the event. |
| `404` | Event/booking not found; a private ticket belongs to another attendee. |
| `409` | Layout is locked; registration is open; no confirmed bookings; allocation is infeasible; strategy differs from the saved run; metrics/export requested before allocation. |

## Invariants

- Layout/status/checkout/allocation writes serialise on the event row. Allocation writes seat assignments and its run record atomically.
- Only active bookings linked to a recorded mock SUCCESS attempt participate. Unverified legacy bookings remain visibly flagged; they are not silently considered paid.
- A database uniqueness constraint protects `(event_id, seat_code)`; general admission has null seat labels.
- An aisle separates allocator sections, so a contiguous block cannot cross it. Blocked seats are excluded. Accessible capacity is reserved for later groups even when an earlier group would otherwise prefer a contiguous block using it.
- The allocator is deterministic and uses a size-first heuristic. It fills rows nearest the stage across all sections first, and moves a group back at most two rows to avoid leaving a single empty seat. See [stage-first seat order](SEAT_ALLOCATION_AND_OUTPUTS.md#stage-first-seat-order). A split explanation means no suitable contiguous block remained at that stage; it is not a proof of globally optimal placement across every possible group ordering.
- On failure, no run or partial assignments are committed. Successful repeated allocation returns the original run; no reshuffling is offered.
- General-admission tickets can be finalised after closure without inventing physical seat numbers.
- Stored timestamps are truncated to MySQL microsecond precision so replayed results do not differ only because of database rounding.
- Maps are schematic, not a surveying tool. Campus profiles remain demo data pending physical verification.

## Validation

`SeatingIntegrationTest` covers the lifecycle, owner restrictions, private tickets,
rollback, template/profile variants, general admission, exports and persisted
assignments. `SeatAllocationServiceTest`, `TableAllocationTest` and
`AllocationEvaluationTest` cover algorithm constraints, table placement and
metrics. Printable output is covered by `PrintableSeatingPlanIntegrationTest`
and `AnonymousSeatingPlanRendererTest`; `AllocationBenchmarkTest` covers timed
persisted scenarios. `scripts/smoke-sprint3.ps1` exercises Docker HTTP endpoints
in a localhost-only synthetic demo.

See [verification commands and database prerequisites](SEAT_ALLOCATION_AND_OUTPUTS.md#8-verification-and-source-files)
before running integration tests. These coverage descriptions are not a claim
that the tests were rerun for this documentation update.
