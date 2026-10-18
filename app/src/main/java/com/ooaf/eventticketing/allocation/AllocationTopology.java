package com.ooaf.eventticketing.allocation;

import java.util.Comparator;
import java.util.List;

/** Shared physical adjacency rules for allocation and evaluation. */
public final class AllocationTopology {
    private AllocationTopology() {}

    public static boolean contiguous(List<AllocationSeat> seats) {
        if (seats.isEmpty()) return false;
        var ordered = seats.stream().sorted(Comparator.comparingInt(AllocationSeat::seatNumber)).toList();
        for (int i = 1; i < ordered.size(); i++) {
            if (!ordered.get(0).sameRow(ordered.get(i))
                    || ordered.get(i).seatNumber() != ordered.get(i - 1).seatNumber() + 1) return false;
        }
        return true;
    }
}
