package com.ooaf.eventticketing.allocation;

public record AllocationSeat(
        Long seatId,
        String section,
        String rowLabel,
        int rowIndex,
        int seatNumber,
        boolean accessible,
        boolean blocked,
        Long elementId,
        boolean table
) {
    public AllocationSeat(Long seatId, String section, String rowLabel, int rowIndex,
            int seatNumber, boolean accessible, boolean blocked, Long elementId) {
        this(seatId, section, rowLabel, rowIndex, seatNumber, accessible, blocked, elementId, false);
    }
    public AllocationSeat(Long seatId, String section, String rowLabel, int rowIndex,
            int seatNumber, boolean accessible, boolean blocked) {
        this(seatId, section, rowLabel, rowIndex, seatNumber, accessible, blocked, null);
    }

    public boolean sameRow(AllocationSeat other) {
        return java.util.Objects.equals(elementId, other.elementId)
                && section.equalsIgnoreCase(other.section)
                && rowIndex == other.rowIndex && rowLabel.equalsIgnoreCase(other.rowLabel);
    }

    public static final double COLUMN_PENALTY = 1.0;
    public static final double ROW_PENALTY = 1.5;

    /** Weighted logical steps, plus one per section/element boundary; not metres. */
    public double distanceTo(AllocationSeat other) {
        long rowSteps = Math.abs((long) rowIndex - other.rowIndex);
        // Different row labels must remain separate even if their indices coincide.
        if (rowSteps == 0 && !rowLabel.equalsIgnoreCase(other.rowLabel)) rowSteps = 1;
        return ROW_PENALTY * rowSteps + COLUMN_PENALTY * Math.abs((long) seatNumber - other.seatNumber)
                + (section.equalsIgnoreCase(other.section) ? 0 : 1)
                + (java.util.Objects.equals(elementId, other.elementId) ? 0 : 1);
    }

    public AllocationSeat {
        if (table && elementId == null) throw new IllegalArgumentException("Table seats require an elementId");
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
