package com.ooaf.eventticketing.allocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TableAllocationTest {
    private final SeatAllocationService allocator = new SeatAllocationService();

    private List<AllocationSeat> tables(int... capacities) {
        var seats = new ArrayList<AllocationSeat>();
        for (int table = 0; table < capacities.length; table++) {
            for (int i = 0; i < capacities[table]; i++) {
                int sideLength = (capacities[table] + 1) / 2;
                int side = i / sideLength;
                seats.add(new AllocationSeat((long) seats.size() + 1, "Main", side == 0 ? "N" : "S",
                        side, i % sideLength + 1, false, false, (long) table + 1, true));
            }
        }
        return seats;
    }

    private List<AllocationBooking> group(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new AllocationBooking((long) i, "G", false)).toList();
    }

    private List<AllocationSeat> assigned(AllocationResult result, List<AllocationSeat> seats) {
        var ids = result.assignments().stream().map(SeatAssignment::seatId).collect(Collectors.toSet());
        return seats.stream().filter(seat -> ids.contains(seat.seatId())).toList();
    }

    @ParameterizedTest @EnumSource(AllocationStrategy.class)
    void oppositeSidesStayAtOneTableEvenWhenAnotherTableHasFreeSeats(AllocationStrategy strategy) {
        var seats = tables(16, 16, 16);
        var result = allocator.allocate(group(12), seats, strategy);
        var selected = assigned(result, seats);
        assertEquals(1, AllocationTopology.unitsUsed(selected));
        assertEquals(2, AllocationTopology.blockCount(selected));
        assertTrue(result.splits().isEmpty());
        var metrics = AllocationEvaluation.evaluate(group(12), seats, result.assignments());
        assertEquals(100.0, metrics.percentageGroupsTogether());
        assertEquals(0.0, metrics.averageSplitDistance());
        assertTrue(metrics.averageGroupContiguityPercentage() < 100);
        assertEquals(0, metrics.duplicateAllocationCount());
    }

    @ParameterizedTest @EnumSource(AllocationStrategy.class)
    void unavoidableTableSplitsUseTwoBalancedSubgroups(AllocationStrategy strategy) {
        var seats = tables(16, 16, 16);
        var result = allocator.allocate(group(20), seats, strategy);
        var selected = assigned(result, seats);
        var sizes = selected.stream().collect(Collectors.groupingBy(AllocationSeat::elementId, Collectors.counting()));
        assertEquals(List.of(10L, 10L), sizes.values().stream().sorted().toList());
        assertEquals(1, result.splits().size());
        assertEquals(20, result.assignments().stream().map(SeatAssignment::seatId).distinct().count());
    }

    @Test void fullTableIsPreferredOverSplittingAmongSmallerTables() {
        var seats = tables(6, 16, 6);
        var result = allocator.allocate(group(14), seats);
        assertTrue(assigned(result, seats).stream().allMatch(seat -> seat.elementId() == 2L));
        assertTrue(result.splits().isEmpty());
    }

    @Test void pendingMembersJoinFixedMembersOnOppositeSides() {
        var seats = tables(16, 16);
        var bookings = new ArrayList<>(group(10));
        bookings.set(0, new AllocationBooking(1L, "G", false, 17L));
        bookings.set(1, new AllocationBooking(2L, "G", false, 25L));
        var result = allocator.allocate(bookings, seats);
        assertTrue(assigned(result, seats).stream().allMatch(seat -> seat.elementId() == 2L));
        assertEquals(17L, result.assignments().get(0).seatId());
        assertEquals(25L, result.assignments().get(1).seatId());
        assertTrue(result.splits().isEmpty());
    }

    @Test void fixedMembersAtDifferentTablesRemainSplitAndSubgroupsAreBalanced() {
        var seats = tables(16, 16, 16);
        var bookings = new ArrayList<>(group(12));
        bookings.set(0, new AllocationBooking(1L, "G", false, 1L));
        bookings.set(1, new AllocationBooking(2L, "G", false, 17L));
        var result = allocator.allocate(bookings, seats);
        var selected = assigned(result, seats);
        assertEquals(2, AllocationTopology.unitsUsed(selected));
        assertEquals(0, AllocationTopology.unitSizeImbalance(selected));
        assertEquals(1, result.splits().size());
    }

    @Test void inaccessibleAndBlockedSeatsCannotMakeATableAppearFeasible() {
        var seats = new ArrayList<>(tables(8, 8));
        var original = seats.get(7);
        seats.set(7, new AllocationSeat(original.seatId(), "Main", "S", 1, 4, false, true, 1L, true));
        original = seats.get(15);
        seats.set(15, new AllocationSeat(original.seatId(), "Main", "S", 1, 4, true, false, 2L, true));
        var bookings = new ArrayList<>(group(8));
        bookings.set(0, new AllocationBooking(1L, "G", true));
        var result = allocator.allocate(bookings, seats);
        assertTrue(assigned(result, seats).stream().allMatch(seat -> seat.elementId() == 2L));
        assertEquals(16L, result.assignments().get(0).seatId());
        assertTrue(result.splits().isEmpty());
    }

    @Test void adjacentSeatsAreUsedWhenOneSideCanFitAndResultsIgnoreInputOrder() {
        var seats = tables(16, 16);
        var bookings = group(6);
        var result = allocator.allocate(bookings, seats);
        assertTrue(AllocationTopology.contiguous(assigned(result, seats)));
        Collections.reverse(seats);
        var reversed = new ArrayList<>(bookings); Collections.reverse(reversed);
        assertEquals(result, allocator.allocate(reversed, seats));
    }

    @Test void genericAreasDoNotAcquireTableGroupingSemantics() {
        var seats = tables(4).stream().map(s -> new AllocationSeat(s.seatId(), s.section(), s.rowLabel(),
                s.rowIndex(), s.seatNumber(), false, false, s.elementId())).toList();
        assertEquals(1, allocator.allocate(group(4), seats).splits().size());
        assertThrows(IllegalArgumentException.class,
                () -> new AllocationSeat(1L, "Main", "N", 0, 1, false, false, null, true));
    }
}
