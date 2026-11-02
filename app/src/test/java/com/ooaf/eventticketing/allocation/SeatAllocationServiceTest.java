package com.ooaf.eventticketing.allocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @Test
    void preservesAccessibleSeatForLaterSmallerGroupEvenWhenSplittingEarlierGroup() {
        var result = service.allocate(List.of(booking(1L, "LARGE", false), booking(2L, "LARGE", false), booking(3L, null, true)),
                List.of(seat(1L, "A", 0, 1, true, false), seat(2L, "A", 0, 2, false, false), seat(3L, "B", 1, 1, false, false)));
        assertEquals(1L, result.assignments().stream().filter(a -> a.bookingId() == 3L).findFirst().orElseThrow().seatId());
        assertEquals(1, result.splits().size());
    }

    @Test
    void allocatesThreeHundredAttendeesWithinProposalBudget() {
        var bookings = new ArrayList<AllocationBooking>();
        var seats = new ArrayList<AllocationSeat>();
        for (int index = 0; index < 300; index++) {
            bookings.add(booking((long) index + 1, "G" + index / 5, index % 20 == 0));
            seats.add(seat((long) index + 1, String.valueOf((char) ('A' + index / 20)), index / 20, index % 20 + 1, index % 20 < 2, false));
        }
        long start = System.nanoTime();
        var result = service.allocate(bookings, seats);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("ALLOCATION_300_ATTENDEES_MS=" + elapsedMs);
        assertEquals(300, result.assignments().size());
        assertEquals(300, result.assignments().stream().map(SeatAssignment::seatId).distinct().count());
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMs < 10_000, "Allocation must finish within 10 seconds on the test machine");
    }

    @Test
    void choosesExactFitInsteadOfLeavingAnIsolatedSeat() {
        var seats = new ArrayList<>(row("A", 0, 1, 3));
        seats.add(seat(4L, "B", 1, 1, false, false));
        seats.add(seat(5L, "B", 1, 2, false, false));
        var bookings = List.of(booking(1L, "PAIR", false), booking(2L, "PAIR", false));
        var result = service.allocate(bookings, seats);
        assertEquals(List.of(4L, 5L), result.assignments().stream().map(SeatAssignment::seatId).toList());
        assertEquals(0, AllocationTopology.isolatedSeats(seats, java.util.Set.of(4L, 5L)));
        Collections.reverse(seats);
        assertEquals(result, service.allocate(bookings, seats));
    }

    @Test
    void fillsFrontRowsOfEverySectionBeforeRearRows() {
        // Two eight-seat blocks either side of an aisle (rows A-H) and a 20-seat rear row J.
        var seats = new ArrayList<AllocationSeat>();
        long id = 1;
        for (int row = 0; row < 8; row++) for (int number = 1; number <= 16; number++) {
            seats.add(new AllocationSeat(id++, number <= 8 ? "Left" : "Right",
                    String.valueOf((char) ('A' + row)), row, number, false, false));
        }
        for (int number = 1; number <= 20; number++) {
            seats.add(new AllocationSeat(id++, "Rear", "J", 8, number, false, false));
        }
        var bookings = new ArrayList<AllocationBooking>();
        long bookingId = 1;
        int[] sizes = {7, 7, 7, 7, 5, 5, 3, 3};
        for (int group = 0; group < sizes.length; group++) {
            for (int member = 0; member < sizes[group]; member++) {
                bookings.add(booking(bookingId++, "G" + group, false));
            }
        }

        var result = service.allocate(bookings, seats);

        var rowsUsed = result.assignments().stream()
                .map(a -> seats.stream().filter(s -> s.seatId().equals(a.seatId())).findFirst().orElseThrow())
                .map(AllocationSeat::rowLabel).distinct().sorted().toList();
        assertEquals(List.of("A", "B", "C"), rowsUsed);
        assertTrue(result.splits().isEmpty());
    }

    @Test
    void usesStageRowWhenTheStageIsNotInFrontOfRowA() {
        // Central stage: North row B and South row C both border the stage.
        var seats = new ArrayList<AllocationSeat>();
        long id = 1;
        String[] labels = {"A", "B", "C", "D"};
        int[] stageRows = {1, 0, 0, 1};
        for (int row = 0; row < 4; row++) for (int number = 1; number <= 4; number++) {
            seats.add(new AllocationSeat(id++, row < 2 ? "North" : "South", labels[row], row, number,
                    false, false, null, false, stageRows[row]));
        }
        var bookings = new ArrayList<AllocationBooking>();
        for (long i = 1; i <= 8; i++) bookings.add(booking(i, i <= 4 ? "FIRST" : "SECOND", false));

        var result = service.allocate(bookings, seats);

        assertTrue(result.assignments().stream()
                .map(a -> seats.stream().filter(s -> s.seatId().equals(a.seatId())).findFirst().orElseThrow())
                .allMatch(seat -> seat.stageRow() == 0));
    }

    @Test
    void rejectsDuplicateOrInaccessibleFixedAssignments() {
        var seats = row("A", 0, 1, 3);
        assertThrows(AllocationException.class, () -> service.allocate(List.of(
                new AllocationBooking(1L, "PAIR", false, 1L),
                new AllocationBooking(2L, "PAIR", false, 1L)), seats));
        assertThrows(AllocationException.class, () -> service.allocate(List.of(
                new AllocationBooking(1L, null, true, 1L)), seats));
    }

    @Test
    void rejectsDuplicateBookingAndSeatIds() {
        var seats = row("A", 0, 1, 3);
        assertThrows(IllegalArgumentException.class, () -> service.allocate(List.of(
                booking(1L, null, false), booking(1L, null, false)), seats));
        var duplicateSeats = new ArrayList<>(seats);
        duplicateSeats.add(seat(1L, "B", 1, 1, false, false));
        assertThrows(IllegalArgumentException.class, () -> service.allocate(List.of(
                booking(1L, null, false)), duplicateSeats));
    }

    @Test
    void rejectsMissingInputsAndStrategy() {
        var seats = row("A", 0, 1, 3);
        var bookings = List.of(booking(1L, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.allocate(null, seats));
        assertThrows(IllegalArgumentException.class, () -> service.allocate(bookings, null));
        assertThrows(NullPointerException.class, () -> service.allocate(bookings, seats, null));
    }

    @Test
    void emptyBookingSetProducesNoAssignments() {
        var result = service.allocate(List.of(), row("A", 0, 1, 3));
        assertTrue(result.assignments().isEmpty());
        assertTrue(result.splits().isEmpty());
    }

    @Test
    void splitPlacementPrefersSameRowOverLowerDistanceAcrossRows() {
        var result = service.allocate(List.of(new AllocationBooking(1L, "PAIR", false, 1L),
                        booking(2L, "PAIR", false)),
                List.of(seat(1L, "A", 0, 1, false, false), seat(2L, "A", 0, 6, false, false),
                        seat(3L, "B", 1, 1, false, false)), AllocationStrategy.PREFER_SAME_ROW);
        assertEquals(2L, result.assignments().stream().filter(a -> a.bookingId() == 2L).findFirst().orElseThrow().seatId());
        assertEquals(1, result.splits().size()); // Same row is insufficient when there is a gap.
    }

    @Test
    void splitPlacementPrefersTwoContiguousBlocksOverThreeFragments() {
        var seats = List.of(seat(1L, "A", 0, 1, false, false), seat(2L, "A", 0, 2, false, false),
                seat(3L, "A", 0, 4, false, false), seat(4L, "B", 1, 1, false, false),
                seat(5L, "B", 1, 2, false, false), seat(6L, "B", 1, 4, false, false));
        var bookings = new ArrayList<AllocationBooking>();
        for (long i = 1; i <= 4; i++) bookings.add(booking(i, "FOUR", false));
        var result = service.allocate(bookings, seats);
        assertEquals(List.of(1L, 2L, 4L, 5L), result.assignments().stream().map(SeatAssignment::seatId).sorted().toList());
        assertEquals(1, result.splits().size());
        Collections.reverse(bookings);
        var reversedSeats = new ArrayList<>(seats); Collections.reverse(reversedSeats);
        assertEquals(result, service.allocate(bookings, reversedSeats));
    }

    @Test
    void splitPlacementUsesWeightedDistanceAfterRowAndBlockCounts() {
        var result = service.allocate(List.of(new AllocationBooking(1L, "PAIR", false, 1L),
                        booking(2L, "PAIR", false)),
                List.of(seat(1L, "A", 0, 1, false, false), seat(2L, "B", 1, 4, false, false),
                        seat(3L, "C", 2, 1, false, false)));
        assertEquals(3L, result.assignments().stream().filter(a -> a.bookingId() == 2L).findFirst().orElseThrow().seatId());
    }

    @Test
    void defaultAndSameRowStrategiesMakeDifferentChoicesButStayDeterministic() {
        var bookings = new ArrayList<>(List.of(new AllocationBooking(1L, "PAIR", false, 1L),
                booking(2L, "PAIR", false), booking(3L, null, true)));
        var seats = new ArrayList<>(List.of(seat(1L, "A", 0, 1, false, false),
                seat(2L, "A", 0, 6, false, false), seat(3L, "B", 1, 1, false, false),
                seat(4L, "C", 2, 1, true, false)));
        for (var strategy : AllocationStrategy.values()) {
            var result = service.allocate(bookings, seats, strategy);
            assertEquals(strategy == AllocationStrategy.DEFAULT ? 3L : 2L,
                    result.assignments().stream().filter(a -> a.bookingId() == 2L).findFirst().orElseThrow().seatId());
            assertEquals(1L, result.assignments().stream().filter(a -> a.bookingId() == 1L).findFirst().orElseThrow().seatId());
            assertEquals(4L, result.assignments().stream().filter(a -> a.bookingId() == 3L).findFirst().orElseThrow().seatId());
            Collections.reverse(bookings); Collections.reverse(seats);
            assertEquals(result, service.allocate(bookings, seats, strategy));
        }
    }

    @ParameterizedTest @EnumSource(AllocationStrategy.class)
    void balancesEvenAndOddGroupsAcrossRows(AllocationStrategy strategy) {
        var seats = rowsWithCapacities(4, 4);
        for (int count : List.of(5, 6)) {
            var result = service.allocate(groupOf(count), seats, strategy);
            assertEquals(count == 6 ? List.of(3L, 3L) : List.of(2L, 3L), rowSizes(result, seats));
            assertEquals(2, AllocationTopology.blockCount(result.assignments().stream()
                    .map(a -> seats.stream().filter(s -> s.seatId().equals(a.seatId())).findFirst().orElseThrow()).toList()));
        }
    }

    @Test void defaultTradesExtraRowsAgainstBalanceWhileSameRowKeepsTwoContiguousSubgroups() {
        var seats = rowsWithCapacities(4, 2, 2);
        assertEquals(List.of(2L, 2L, 2L), rowSizes(service.allocate(groupOf(6), seats, AllocationStrategy.DEFAULT), seats));
        assertEquals(List.of(2L, 4L), rowSizes(service.allocate(groupOf(6), seats, AllocationStrategy.PREFER_SAME_ROW), seats));
    }

    @Test void sameRowStrategyUsesMoreRowsWhenThatAvoidsFragmentedSubgroups() {
        var seats = new ArrayList<AllocationSeat>();
        long id = 1;
        for (int row = 0; row < 2; row++) for (int number : List.of(1, 2, 4)) {
            seats.add(seat(id++, String.valueOf((char) ('A' + row)), row, number, false, false));
        }
        seats.add(seat(id++, "C", 2, 1, false, false));
        seats.add(seat(id, "C", 2, 2, false, false));
        var result = service.allocate(groupOf(6), seats, AllocationStrategy.PREFER_SAME_ROW);
        assertEquals(List.of(2L, 2L, 2L), rowSizes(result, seats));
    }

    @ParameterizedTest @EnumSource(AllocationStrategy.class)
    void unequalSplitsAreAllowedWhenCapacityOrFixedChoicesPreventEquality(AllocationStrategy strategy) {
        var restricted = rowsWithCapacities(4, 1);
        assertEquals(List.of(1L, 4L), rowSizes(service.allocate(groupOf(5), restricted, strategy), restricted));
        var seats = rowsWithCapacities(4, 4);
        var bookings = new ArrayList<AllocationBooking>();
        for (long i = 1; i <= 6; i++) bookings.add(new AllocationBooking(i, "GROUP", false, i <= 4 ? i : null));
        var result = service.allocate(bookings, seats, strategy);
        assertEquals(List.of(2L, 4L), rowSizes(result, seats));
        for (long i = 1; i <= 4; i++) {
            long fixed = i;
            assertEquals(fixed, result.assignments().stream().filter(a -> a.bookingId() == fixed).findFirst().orElseThrow().seatId());
        }
    }

    @ParameterizedTest @EnumSource(AllocationStrategy.class)
    void balancedSplitsPreserveAccessibleSeatsForOtherBookings(AllocationStrategy strategy) {
        var seats = new ArrayList<>(rowsWithCapacities(4, 4, 1));
        for (int i : List.of(4, 5, 8)) {
            var seat = seats.get(i);
            seats.set(i, new AllocationSeat(seat.seatId(), seat.section(), seat.rowLabel(), seat.rowIndex(),
                    seat.seatNumber(), true, false));
        }
        var bookings = new ArrayList<AllocationBooking>();
        for (long i = 1; i <= 6; i++) bookings.add(booking(i, "GROUP", i <= 2));
        bookings.add(booking(7L, null, true));
        var result = service.allocate(bookings, seats, strategy);
        var groupResult = new AllocationResult(result.assignments().stream().filter(a -> a.groupCode() != null).toList(), result.splits());
        assertEquals(List.of(3L, 3L), rowSizes(groupResult, seats));
        assertEquals(9L, result.assignments().stream().filter(a -> a.bookingId() == 7L).findFirst().orElseThrow().seatId());
        for (var assignment : result.assignments()) {
            if (assignment.bookingId() <= 2 || assignment.bookingId() == 7) {
                assertTrue(seats.stream().filter(s -> s.seatId().equals(assignment.seatId())).findFirst().orElseThrow().accessible());
            }
        }
    }

    @ParameterizedTest @EnumSource(AllocationStrategy.class)
    void balancedPreferencesDoNotSplitAnAvailableWholeGroupBlock(AllocationStrategy strategy) {
        var seats = rowsWithCapacities(6, 3, 3);
        assertEquals(List.of(6L), rowSizes(service.allocate(groupOf(6), seats, strategy), seats));
    }

    private List<AllocationBooking> groupOf(int count) {
        var bookings = new ArrayList<AllocationBooking>();
        for (long i = 1; i <= count; i++) bookings.add(booking(i, "GROUP", false));
        return bookings;
    }

    private List<AllocationSeat> rowsWithCapacities(int... capacities) {
        var seats = new ArrayList<AllocationSeat>();
        long id = 1;
        for (int row = 0; row < capacities.length; row++) for (int number = 1; number <= capacities[row]; number++) {
            seats.add(seat(id++, String.valueOf((char) ('A' + row)), row, number, false, false));
        }
        return seats;
    }

    private List<Long> rowSizes(AllocationResult result, List<AllocationSeat> seats) {
        return result.assignments().stream().map(a -> seats.stream().filter(s -> s.seatId().equals(a.seatId())).findFirst().orElseThrow())
                .collect(java.util.stream.Collectors.groupingBy(AllocationTopology::physicalRowKey, java.util.stream.Collectors.counting()))
                .values().stream().sorted().toList();
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
