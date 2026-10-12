package com.ooaf.eventticketing.allocation;

public record AllocationSeat(
        Long seatId,
        String section,
        String rowLabel,
        int rowIndex,
        int seatNumber,
        boolean accessible,
        boolean blocked
) {
    public AllocationSeat {
        if (seatId == null) {
            throw new IllegalArgumentException("seatId is required");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section is required");
        }
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("rowLabel is required");
        }
        if (rowIndex < 0 || seatNumber < 1) {
            throw new IllegalArgumentException("rowIndex and seatNumber must identify a valid seat");
        }
    }
}
