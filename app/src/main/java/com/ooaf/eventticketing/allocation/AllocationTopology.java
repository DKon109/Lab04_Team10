package com.ooaf.eventticketing.allocation;

import java.util.*;

/** Shared physical adjacency rules for allocation and evaluation. */
public final class AllocationTopology {
    private AllocationTopology() {}

    private record Row(Long element, String section, int index, String label) {
        Row(AllocationSeat seat) {
            this(seat.elementId(), seat.section().toUpperCase(Locale.ROOT), seat.rowIndex(),
                    seat.rowLabel().toUpperCase(Locale.ROOT));
        }
    }

    /** Physical row identity spans sections/aisles; those boundaries still break blocks. */
    public static boolean samePhysicalRow(AllocationSeat first, AllocationSeat second) {
        return first.rowIndex() == second.rowIndex() && first.rowLabel().equalsIgnoreCase(second.rowLabel());
    }

    public static String physicalRowKey(AllocationSeat seat) {
        return seat.rowIndex() + "\u0000" + seat.rowLabel().toUpperCase(Locale.ROOT);
    }

    public static int rowsUsed(List<AllocationSeat> seats) {
        return (int) seats.stream().map(AllocationTopology::physicalRowKey).distinct().count();
    }

    /** Spread between the largest and smallest non-empty row subgroups; zero means equal sizes. */
    public static int rowSizeImbalance(List<AllocationSeat> seats) {
        Map<String, Integer> counts = new HashMap<>();
        for (var seat : seats) counts.merge(physicalRowKey(seat), 1, Integer::sum);
        return counts.isEmpty() ? 0 : Collections.max(counts.values()) - Collections.min(counts.values());
    }

    /** Maximal consecutive runs, split at rows, gaps, aisles and elements. */
    public static List<List<AllocationSeat>> blocks(List<AllocationSeat> seats) {
        Map<Row, List<AllocationSeat>> rows = new LinkedHashMap<>();
        for (var seat : seats) rows.computeIfAbsent(new Row(seat), ignored -> new ArrayList<>()).add(seat);
        var blocks = new ArrayList<List<AllocationSeat>>();
        for (var row : rows.values()) {
            row.sort(Comparator.comparingInt(AllocationSeat::seatNumber));
            List<AllocationSeat> block = null;
            for (int i = 0; i < row.size(); i++) {
                if (i == 0 || row.get(i).seatNumber() != row.get(i - 1).seatNumber() + 1) {
                    block = new ArrayList<>(); blocks.add(block);
                }
                block.add(row.get(i));
            }
        }
        return blocks;
    }

    public static int blockCount(List<AllocationSeat> seats) {
        return blocks(seats).size();
    }

    public static boolean contiguous(List<AllocationSeat> seats) {
        return !seats.isEmpty() && blockCount(seats) == 1;
    }

    /** Table membership is independent of side, row label and physical adjacency. */
    public static boolean together(List<AllocationSeat> seats) {
        return contiguous(seats) || (!seats.isEmpty() && seats.get(0).table()
                && seats.stream().allMatch(seat -> seat.table()
                        && Objects.equals(seat.elementId(), seats.get(0).elementId())));
    }

    /** One placement unit is a table or a physical theatre row. */
    public static String placementUnitKey(AllocationSeat seat) {
        return seat.table() ? "TABLE:" + seat.elementId() : "ROW:" + physicalRowKey(seat);
    }

    public static int unitsUsed(List<AllocationSeat> seats) {
        return (int) seats.stream().map(AllocationTopology::placementUnitKey).distinct().count();
    }

    public static int unitSizeImbalance(List<AllocationSeat> seats) {
        Map<String, Integer> counts = new HashMap<>();
        seats.forEach(seat -> counts.merge(placementUnitKey(seat), 1, Integer::sum));
        return counts.isEmpty() ? 0 : Collections.max(counts.values()) - Collections.min(counts.values());
    }

    /** Percentage of possible adjacent connections retained: (members - blocks)/(members - 1). */
    public static double contiguityPercentage(List<AllocationSeat> seats) {
        if (seats.size() < 2) return 100.0;
        return 100.0 * (seats.size() - blockCount(seats)) / (seats.size() - 1);
    }

    public static double pairwiseDistance(List<AllocationSeat> seats) {
        double distance = 0;
        for (int i = 0; i < seats.size(); i++) for (int j = i + 1; j < seats.size(); j++) {
            distance += seats.get(i).distanceTo(seats.get(j));
        }
        return distance;
    }

    /** Empty usable singleton runs, including row ends; blocked/private positions are excluded. */
    public static int isolatedSeats(List<AllocationSeat> seats, Set<Long> occupied) {
        Map<Row, Set<Integer>> free = new HashMap<>();
        for (var seat : seats) if (!seat.blocked() && !occupied.contains(seat.seatId())) {
            free.computeIfAbsent(new Row(seat), ignored -> new HashSet<>()).add(seat.seatNumber());
        }
        int count = 0;
        for (var row : free.values()) for (int number : row) {
            if (!row.contains(number - 1) && !row.contains(number + 1)) count++;
        }
        return count;
    }

}
