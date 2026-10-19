package com.ooaf.eventticketing.allocation;

public record SeatAssignment(
        Long bookingId,
        Long seatId,
        String groupCode,
        boolean split
) {
}
