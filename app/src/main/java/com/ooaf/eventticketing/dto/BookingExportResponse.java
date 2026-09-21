package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.Booking;
import com.ooaf.eventticketing.entity.BookingStatus;

public record BookingExportResponse(String bookingRef, String groupCode, boolean accessibilityRequired, BookingStatus status){
    public static BookingExportResponse fromEntity(Booking booking){
        return new BookingExportResponse(booking.getBookingRef(), booking.getGroupCode(), booking.isAccessibilityRequired(), booking.getStatus());
    }
}
