package com.ooaf.eventticketing.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatAllocationServiceTest {

    private final SeatAllocationService service = new SeatAllocationService();

    @Test
    void allocatesLargestGroupTogetherBeforeSoloBookings() {
        List<AllocationBooking> bookings = List.of(
                booking(4L, null, false),
                booking(1L, "FRIENDS", false),
                booking(3L, "FRIENDS", false),
                booking(2L, "FRIENDS", false)
        );

        AllocationResult result = service.allocate(bookings, row("A", 0, 1, 4));

        assertEquals(List.of(1L, 2L, 3L), result.assignments().stream()
                .filter(assignment -> "FRIENDS".equals(assignment.groupCode()))
                .map(SeatAssignment::seatId)
                .sorted()
                .toList());
        assertTrue(result.splits().isEmpty());
        assertTrue(result.assignments().stream().noneMatch(SeatAssignment::split));
    }

    @Test
    void reportsSplitWhenNoRowCanFitTheWholeGroup() {
        List<AllocationBooking> bookings = List.of(
                booking(1L, "TEAM", false),
                booking(2L, "TEAM", false),
                booking(3L, "TEAM", false)
        );
        List<AllocationSeat> seats = List.of(
                seat(1L, "A", 0, 1, false, false),
                seat(2L, "A", 0, 2, false, false),
                seat(3L, "B", 1, 1, false, false)
        );

        AllocationResult result = service.allocate(bookings, seats);

        assertEquals(1, result.splits().size());
        assertEquals("TEAM", result.splits().get(0).groupCode());
        assertEquals(List.of(1L, 2L, 3L), result.splits().get(0).seatIds());
        assertTrue(result.assignments().stream().allMatch(SeatAssignment::split));
    }

    @Test
    void reservesAccessibleSeatForBookingThatRequiresIt() {
        List<AllocationBooking> bookings = List.of(
                booking(1L, "PAIR", false),
                booking(2L, "PAIR", true)
        );
        List<AllocationSeat> seats = List.of(
                seat(10L, "A", 0, 1, false, false),
                seat(11L, "A", 0, 2, true, false)
        );

        AllocationResult result = service.allocate(bookings, seats);

        assertEquals(11L, result.assignments().stream()
                .filter(assignment -> assignment.bookingId().equals(2L))
                .findFirst()
                .orElseThrow()
                .seatId());
    }

    @Test
    void ignoresBlockedSeatsAndFailsWhenCapacityIsInsufficient() {
        List<AllocationBooking> bookings = List.of(
                booking(1L, null, false),
                booking(2L, null, false)
        );
        List<AllocationSeat> seats = List.of(
                seat(1L, "A", 0, 1, false, false),
                seat(2L, "A", 0, 2, false, true)
        );

        AllocationException exception = assertThrows(
                AllocationException.class,
                () -> service.allocate(bookings, seats)
        );

        assertEquals("Not enough unblocked seats for all bookings", exception.getMessage());
    }

    @Test
    void canSplitAccessibleBookingsAcrossSeparatedRows() {
        List<AllocationBooking> bookings = List.of(
                booking(1L, "ACCESS", true),
                booking(2L, "ACCESS", true)
        );
        List<AllocationSeat> seats = List.of(
                seat(1L, "A", 0, 1, true, false),
                seat(2L, "A", 0, 2, false, false),
                seat(3L, "B", 1, 1, false, false),
                seat(4L, "B", 1, 2, true, false)
        );

        AllocationResult result = service.allocate(bookings, seats);

        assertEquals(List.of(1L, 4L), result.assignments().stream()
                .map(SeatAssignment::seatId)
                .sorted()
                .toList());
        assertEquals(1, result.splits().size());
    }

    @Test
    void producesTheSameAssignmentsRegardlessOfInputOrder() {
        List<AllocationBooking> bookings = new ArrayList<>(List.of(
                booking(1L, "GROUP", false),
                booking(2L, "GROUP", false),
                booking(3L, null, false)
        ));
        List<AllocationSeat> seats = new ArrayList<>(row("A", 0, 1, 3));

        AllocationResult expected = service.allocate(bookings, seats);
        Collections.reverse(bookings);
        Collections.reverse(seats);
        AllocationResult actual = service.allocate(bookings, seats);

        assertEquals(expected, actual);
        assertFalse(actual.assignments().isEmpty());
    }

    private AllocationBooking booking(Long id, String groupCode, boolean accessibilityRequired) {
        return new AllocationBooking(id, groupCode, accessibilityRequired);
    }

    private List<AllocationSeat> row(String rowLabel, int rowIndex, int firstSeat, int count) {
        List<AllocationSeat> seats = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            int seatNumber = firstSeat + offset;
            seats.add(seat((long) seatNumber, rowLabel, rowIndex, seatNumber, false, false));
        }
        return seats;
    }

    private AllocationSeat seat(
            Long id,
            String rowLabel,
            int rowIndex,
            int seatNumber,
            boolean accessible,
            boolean blocked
    ) {
        return new AllocationSeat(
                id,
                "MAIN",
                rowLabel,
                rowIndex,
                seatNumber,
                accessible,
                blocked
        );
    }
}
