package com.ooaf.eventticketing.allocation;

import java.util.List;

public record GroupSplit(
        String groupCode,
        List<Long> bookingIds,
        List<Long> seatIds,
        String reason
) {
    public GroupSplit {
        bookingIds = List.copyOf(bookingIds);
        seatIds = List.copyOf(seatIds);
    }
}
