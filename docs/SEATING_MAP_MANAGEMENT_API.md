# Seating map management API

The managed seating-map API supports rows, round and square tables, areas,
decorative objects/text, ticket-type mappings, private or blocked seats,
attendee seat selection, and organiser assignment/reassignment.

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

## Create or replace a map

Maps can only be changed while the event is a draft and before bookings or an
allocation run exist. Saving a managed map replaces the old grid layout.

```json
{
  "name": "Main auditorium",
  "attendeesChooseSeats": true,
  "autoMatchTicketCapacities": true,
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

When `autoMatchTicketCapacities` is true, each configured ticket type's capacity
is recalculated from its mapped public, unblocked seats. The event capacity is
always recalculated from all public, unblocked seats.

Attendee choice requires every public seat to have at least one ticket-type
mapping. Accessible seats are reserved for bookings that requested accessible
seating, while non-accessible seats are rejected for those bookings. Private
seats can only be assigned by the organiser.

Seat assignment requires a successful mock checkout. Database uniqueness plus
an event-level write lock prevents two bookings from receiving the same seat.
