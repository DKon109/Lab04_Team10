package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.BookingExportResponse;
import com.ooaf.eventticketing.dto.BookingResponse;
import com.ooaf.eventticketing.dto.CloseRegistrationResponse;
import com.ooaf.eventticketing.dto.CreateBookingRequest;
import com.ooaf.eventticketing.service.BookingService;
import com.ooaf.eventticketing.service.MockCheckoutService;
import com.ooaf.eventticketing.dto.MockCheckoutRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BookingController{
    private final BookingService bookingService;
    private final MockCheckoutService checkoutService;

    public BookingController(BookingService bookingService, MockCheckoutService checkoutService) {
        this.bookingService = bookingService;
        this.checkoutService = checkoutService;
    }

    @PostMapping("/api/events/{eventId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@PathVariable Long eventId, @Valid @RequestBody CreateBookingRequest request, Authentication authentication){
        // Backwards-compatible route: an explicit legacy booking is a simulated
        // success, with the same atomic audit and retry rules as the new checkout.
        return checkoutService.checkout(eventId, authentication.getName(), new MockCheckoutRequest(
                request.createGroup(), request.joinGroupCode(), request.accessibilityRequired(),
                request.idempotencyKey(), MockCheckoutRequest.Outcome.SUCCESS)).booking();
    }

    @GetMapping("/api/auth/me/bookings")
    public List<BookingResponse> listCurrentUserBookings(Authentication authentication){
        return bookingService.listCurrentUserBookings(authentication.getName());
    }

    @GetMapping("/api/events/{eventId}/bookings")
    public List<BookingResponse> listEventBookings(@PathVariable Long eventId, Authentication authentication){
        return bookingService.listEventBookings(eventId, authentication.getName());
    }

    @PostMapping("/api/events/{eventId}/close-registration")
    public CloseRegistrationResponse closeRegistration(@PathVariable Long eventId, Authentication authentication){
        return bookingService.closeRegistration(eventId, authentication.getName());
    }

    @GetMapping("/api/events/{eventId}/bookings/export")
    public List<BookingExportResponse> exportForAllocation(@PathVariable Long eventId, Authentication authentication){
        return bookingService.exportForAllocation(eventId, authentication.getName());
    }
}
