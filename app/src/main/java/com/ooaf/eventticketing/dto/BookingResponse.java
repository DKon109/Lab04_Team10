package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.Booking;
import com.ooaf.eventticketing.entity.BookingStatus;

import java.time.LocalDateTime;

public record BookingResponse(Long id, String bookingRef, Long eventId, String eventTitle, String groupCode, boolean accessibilityRequired, BookingStatus status, LocalDateTime createdAt, boolean generalAdmission){
    public static BookingResponse fromEntity(Booking booking){
        return new BookingResponse(booking.getId(), booking.getBookingRef(), booking.getEvent().getId(), booking.getEvent().getTitle(), booking.getGroupCode(), booking.isAccessibilityRequired(), booking.getStatus(), booking.getCreatedAt(), !booking.getEvent().isSeatIdentifiersConfirmed());
    }
}
