# Seating map management API

The managed seating-map API supports rows, round and square tables, areas,
decorative objects/text, ticket-type mappings, private or blocked seats,
attendee seat selection, and organiser assignment/reassignment.

See [persisted seating integration](SEATING_API.md) for the booking-to-allocation
lifecycle and [allocation and outputs](SEAT_ALLOCATION_AND_OUTPUTS.md) for
strategies, metrics, private tickets and anonymous exports. Authenticated
requests use `Authorization: Bearer <token>`; organiser routes require the
`COMMITTEE_MEMBER` role as well as event ownership.

## Endpoints

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `PUT` | `/api/organiser/events/{eventId}/seat-map` | Event owner | Create or replace a draft event's map |
| `GET` | `/api/organiser/events/{eventId}/seat-map` | Event owner | Read the map, including booking assignments |
| `DELETE` | `/api/organiser/events/{eventId}/seat-map` | Event owner | Remove a draft event's map |
| `GET` | `/api/events/{eventId}/seat-map` | Public | Read a published/closed map without booking details |
| `PUT` | `/api/bookings/{bookingId}/seat` | Booking owner | Choose or change a seat when attendee choice is enabled |
| `PUT` | `/api/organiser/events/{eventId}/bookings/{bookingId}/seat` | Event owner | Assign, move, or remove a confirmed booking's seat |

Send `{"seatCode":"A1"}` to an assignment endpoint. Send
`{"seatCode":null}` to remove the current assignment.
Removal is only allowed before batch allocation; afterwards, assign a
replacement seat instead. Successful map deletion returns `204 No Content`.

## Create or replace a map

Maps can only be changed while the event is a draft and before bookings or an
allocation run exist. Saving a managed map replaces the old grid layout.

```json
{
  "name": "Main auditorium",
  "attendeesChooseSeats": true,
  "autoMatchTicketCapacities": true,
  "identifiersConfirmed": true,
  "ticketTypeCapacities": {
    "STANDARD": 100,
    "VIP": 20
  },
  "elements": [
    {
      "key": "row-a",
      "type": "ROW",
      "label": "Row A",
      "note": null,
      "positionX": 0,
      "positionY": 20,
      "rotationDegrees": 0,
      "displayOrder": 0,
      "privateElement": false,
      "seats": [
        {
          "code": "A1",
          "label": "A1",
          "note": "Wheelchair position",
          "section": "Stalls",
          "rowLabel": "A",
          "rowIndex": 0,
          "seatNumber": 1,
          "positionX": 10,
          "positionY": 20,
          "accessible": true,
          "blocked": false,
          "privateSeat": false,
          "ticketTypes": ["STANDARD"]
        }
      ]
    },
    {
      "key": "stage",
      "type": "OBJECT",
      "label": "Stage",
      "note": null,
      "positionX": 0,
      "positionY": -20,
      "rotationDegrees": 0,
      "displayOrder": 1,
      "privateElement": false,
      "seats": []
    }
  ]
}
```

Element types are `ROW`, `ROUND_TABLE`, `SQUARE_TABLE`, `AREA`, `OBJECT`, and
`TEXT`. Objects and text cannot contain seats; all other elements must contain
at least one seat. Seat codes and element keys are unique within an event.

Seat codes and ticket-type codes are normalised to uppercase; element keys
must be unique ignoring case. Mapped ticket types must be declared in
`ticketTypeCapacities`. Maps support up to 100 elements and 1000 total seats,
with at least one public, unblocked seat. This storage limit is separate from
the proposal's 300-attendee allocation performance target.

Set `identifiersConfirmed` explicitly: omission or false sets the event to
general admission and prevents physical seat assignment. This is an organiser
attestation, not automatic venue verification. Each replacement increments
the map version. To return to a legacy grid, delete the managed map first.

When `autoMatchTicketCapacities` is true, each configured ticket type's capacity
is recalculated from its mapped public, unblocked seats. The event capacity is
always recalculated from all public, unblocked seats.

Attendee choice requires every public seat to have at least one ticket-type
mapping. Attendees without accessibility requests cannot select accessible
seats; bookings requesting accessibility cannot be assigned non-accessible
seats. Organisers can assign accessible seats to other bookings. Private
seats can only be assigned by the organiser. A private element makes all its
seats private.

Seat assignment requires a successful mock checkout. Database uniqueness plus
an event-level write lock prevents two bookings from receiving the same seat.

## Read a map

Both map endpoints return `eventId`, `name`, `attendeesChooseSeats`, `version`,
`totalSeats`, `publicCapacity`, `assignedSeats`, `ticketTypes` and `elements`.
Ticket-type entries include `configuredCapacity`, `mappedCapacity` and
`capacityMatches`, so clients can display mismatches when auto-matching is off.

Seats expose geometry, labels, accessibility, ticket-type mappings and a status:
`AVAILABLE`, `ASSIGNED`, `BLOCKED` or `PRIVATE`. Blocked/private status takes
precedence over assignment status. Public maps are available only for
published or closed events; draft events return `404`. Public map seats have
null `assignedBookingId` and `assignedBookingReference`; organiser maps include
these fields when a booking occupies the seat. Private seats remain represented
with `PRIVATE` status; privacy here restricts selection, not map visibility.

## Choose or move a seat

```http
PUT /api/bookings/{bookingId}/seat
Authorization: Bearer <booking-owner-token>
Content-Type: application/json

{"seatCode":"A1"}
```

- Attendee choice requires `attendeesChooseSeats=true`, a published event with
  open registration, confirmed physical identifiers and successful mock
  checkout. Another attendee's booking returns `404`.
- The organiser assignment endpoint requires event ownership, a non-cancelled
  booking on that event, successful mock checkout and confirmed identifiers.
  It can move seats after closure or allocation.
- Blocked seats and seats occupied by another active booking are rejected.
  Seat codes are trimmed and normalised to uppercase.
- Clearing a seat before allocation restores `PENDING_ALLOCATION`; assigning
  a seat returns `ALLOCATED`. The response contains `bookingId`,
  `bookingReference`, `seatCode` and `status`.

Batch allocation preserves valid existing assignments as fixed anchors and
excludes unassigned private seats. After allocation, organiser moves refresh
group-split explanations and evaluation metrics. They do not create a new run
or change its original timing. Download exports again after any moves.

## Errors and verification

| Status | Relevant cases |
| --- | --- |
| `400` | Invalid map, duplicate element/seat identifiers, unknown ticket mapping or exceeded map limits. |
| `401` | Missing, invalid or expired authentication. |
| `403` | Caller lacks the organiser role or does not own the event. |
| `404` | Event/map/seat not found, public request for a draft event, or booking missing/not owned/not on the requested event. |
| `409` | Map locked by lifecycle/bookings/allocation, attendee choice closed/disabled, checkout or identifiers unconfirmed, seat blocked/private/occupied, accessibility mismatch or seat removal after allocation. |

`SeatingIntegrationTest` includes managed-map validation, access restrictions,
seat selection, reassignment and allocator integration. See the
[allocation verification guide](SEAT_ALLOCATION_AND_OUTPUTS.md#8-verification-and-source-files)
for test commands and dedicated MySQL test-database requirements. Tests were
not rerun for this documentation-only update.
