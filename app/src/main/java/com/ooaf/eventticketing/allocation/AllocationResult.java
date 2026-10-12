package com.ooaf.eventticketing.allocation;

import java.util.List;

public record AllocationResult(
        List<SeatAssignment> assignments,
        List<GroupSplit> splits
) {
    public AllocationResult {
        assignments = List.copyOf(assignments);
        splits = List.copyOf(splits);
    }
}
