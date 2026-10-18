package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.CheckoutAttempt;
import com.ooaf.eventticketing.entity.BookingStatus;
import com.ooaf.eventticketing.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.UUID;

@Service
public class MockCheckoutService {
    private final CheckoutAttemptRepository attempts;
    private final BookingRepository bookings;
    private final EventRepository events;
    private final UserRepository users;
    private final BookingService bookingService;

    public MockCheckoutService(CheckoutAttemptRepository attempts, BookingRepository bookings,
            EventRepository events, UserRepository users, BookingService bookingService) {
        this.attempts = attempts; this.bookings = bookings; this.events = events;
        this.users = users; this.bookingService = bookingService;
    }

    // The event lock serialises capacity checks; READ_COMMITTED makes retries see
    // the transaction that just released the lock. Attempt + booking commit together.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MockCheckoutResponse checkout(Long eventId, String email, MockCheckoutRequest request) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var event = events.findByIdForUpdate(eventId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        String group = request.joinGroupCode() == null ? "" : request.joinGroupCode().trim().toUpperCase(Locale.ROOT);
        boolean create = Boolean.TRUE.equals(request.createGroup());
        boolean accessible = Boolean.TRUE.equals(request.accessibilityRequired());
        if (create && !group.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose create or join group, not both");
        String fingerprint = create + "|" + group + "|" + accessible + "|" + request.outcome();
        var existing = attempts.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            var attempt = existing.get();
            if (!attempt.getUserId().equals(user.getId()) || !attempt.getEventId().equals(eventId)
                    || !attempt.getFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout key already used with different details");
            }
            BookingResponse booking = attempt.getBookingId() == null ? null : bookings.findById(attempt.getBookingId())
                    .map(BookingResponse::fromEntity).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Booking no longer available"));
            return new MockCheckoutResponse(attempt.getPaymentRef(), attempt.getOutcome(), booking);
        }
        // Bookings created before checkout attempts were introduced have no audit
        // row. Preserve retries of their original booking key without inventing
        // a historical payment or creating a second booking.
        var legacyBooking = bookings.findByIdempotencyKey(request.idempotencyKey());
        if (legacyBooking.isPresent()) {
            var booking = legacyBooking.get();
            if (!booking.getUser().getId().equals(user.getId())
                    || !booking.getEvent().getId().equals(eventId)
                    || request.outcome() != MockCheckoutRequest.Outcome.SUCCESS
                    || booking.getStatus() == BookingStatus.CANCELLED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking key has already been used");
            }
            return new MockCheckoutResponse(null, MockCheckoutRequest.Outcome.SUCCESS,
                    BookingResponse.fromEntity(booking));
        }
        BookingResponse booking = null;
        if (event.getStatus() == com.ooaf.eventticketing.entity.EventStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        if (request.outcome() == MockCheckoutRequest.Outcome.SUCCESS) {
            booking = bookingService.createBooking(eventId, email, new CreateBookingRequest(create, group, accessible, UUID.randomUUID().toString()));
        }
        var attempt = attempts.saveAndFlush(new CheckoutAttempt(request.idempotencyKey(), "MOCK-" + UUID.randomUUID(),
                user.getId(), eventId, fingerprint, request.outcome(), booking == null ? null : booking.id()));
        return new MockCheckoutResponse(attempt.getPaymentRef(), attempt.getOutcome(), booking);
    }
}
