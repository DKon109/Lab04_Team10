package com.ooaf.eventticketing.allocation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AllocationEvaluationTest {
    private AllocationSeat seat(long id, int row, int number, Long element, boolean blocked) {
        return new AllocationSeat(id, "Main", "R" + row, row, number, false, blocked, element);
    }

    @Test void computesGroupPercentageDistanceAndDuplicatesFromActualAssignments() {
        var seats = List.of(seat(1, 0, 1, 1L, false), seat(2, 0, 2, 1L, false),
                seat(3, 1, 1, 2L, false), seat(4, 1, 3, 2L, false));
        var bookings = List.of(new AllocationBooking(1L, "A", false), new AllocationBooking(2L, "A", false),
                new AllocationBooking(3L, "B", false), new AllocationBooking(4L, "B", false));
        var assignments = List.of(new SeatAssignment(1L, 1L, "A", false), new SeatAssignment(2L, 2L, "A", false),
                new SeatAssignment(3L, 3L, "B", true), new SeatAssignment(4L, 4L, "B", true));
        var result = AllocationEvaluation.evaluate(bookings, seats, assignments);
        assertEquals(2, result.groupCount()); assertEquals(1, result.splitGroupCount());
        assertEquals(50.0, result.percentageGroupsTogether());
        assertEquals(50.0, result.averageGroupContiguityPercentage());
        assertEquals(2.0, result.averageSplitDistance());
        assertEquals(0, result.duplicateAllocationCount());
        var duplicateSeat = List.of(new SeatAssignment(1L, 1L, "A", false), new SeatAssignment(2L, 1L, "A", false));
        assertEquals(1, AllocationEvaluation.evaluate(bookings.subList(0, 2), seats, duplicateSeat).duplicateAllocationCount());
    }

    @Test void singletonGroupsHaveNoTogetherPercentageAndBoundariesBreakContiguity() {
        var seats = List.of(seat(1, 0, 1, 1L, false), seat(2, 0, 2, 2L, false));
        assertFalse(AllocationTopology.contiguous(seats));
        var result = AllocationEvaluation.evaluate(List.of(new AllocationBooking(1L, "ALONE", false)), seats,
                List.of(new SeatAssignment(1L, 1L, "ALONE", false)));
        assertEquals(0, result.groupCount()); assertNull(result.percentageGroupsTogether());
        assertNull(result.averageGroupContiguityPercentage());
        assertEquals(0.0, result.averageSplitDistance());
    }

    @Test void isolationCountsRowEndsButExcludesBlockedSeatsAndCrossElementNeighbours() {
        var seats = List.of(seat(1, 0, 1, 1L, false), seat(2, 0, 2, 1L, true),
                seat(3, 0, 3, 1L, false), seat(4, 0, 4, 2L, false));
        assertEquals(3, AllocationTopology.isolatedSeats(seats, Set.of()));
        assertEquals(2, AllocationTopology.isolatedSeats(seats, Set.of(1L)));
    }
    @Test void partialContiguityMeasuresAdjacentConnectionsAndRespectsBoundaries() {
        var together = List.of(seat(1, 0, 1, 1L, false), seat(2, 0, 2, 1L, false), seat(3, 0, 3, 1L, false));
        var split = new java.util.ArrayList<>(together);
        split.add(seat(4, 1, 1, 2L, false)); split.add(seat(5, 1, 2, 2L, false));
        assertEquals(100.0, AllocationTopology.contiguityPercentage(together));
        assertEquals(2, AllocationTopology.blockCount(split));
        assertEquals(75.0, AllocationTopology.contiguityPercentage(split));
        var bookings = split.stream().map(s -> new AllocationBooking(s.seatId(), "GROUP", false)).toList();
        var assignments = split.stream().map(s -> new SeatAssignment(s.seatId(), s.seatId(), "GROUP", true)).toList();
        assertEquals(75.0, AllocationEvaluation.evaluate(bookings, split, assignments).averageGroupContiguityPercentage());
        assertEquals(0.0, AllocationTopology.contiguityPercentage(List.of(together.get(0), split.get(3))));
        assertEquals(0.0, AllocationTopology.contiguityPercentage(List.of(together.get(0), seat(6, 0, 2, 3L, false))));
    }

    @Test void distanceUsesOnePerColumnAndOnePointFivePerRow() {
        var origin = seat(1, 0, 1, null, false);
        assertEquals(1.0, origin.distanceTo(seat(2, 0, 2, null, false)));
        assertEquals(1.5, origin.distanceTo(seat(3, 1, 1, null, false)));
        assertEquals(4.5, origin.distanceTo(seat(4, 1, 4, null, false)));
    }

}
