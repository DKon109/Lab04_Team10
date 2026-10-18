package com.ooaf.eventticketing.allocation;

public record AllocationBooking(
        Long bookingId,
        String groupCode,
        boolean accessibilityRequired,
        Long assignedSeatId
) {
    public AllocationBooking(Long bookingId, String groupCode, boolean accessibilityRequired) {
        this(bookingId, groupCode, accessibilityRequired, null);
    }

    public AllocationBooking {
        if (bookingId == null) {
            throw new IllegalArgumentException("bookingId is required");
        }
        groupCode = groupCode == null || groupCode.isBlank()
                ? null
                : groupCode.trim();
    }
}
