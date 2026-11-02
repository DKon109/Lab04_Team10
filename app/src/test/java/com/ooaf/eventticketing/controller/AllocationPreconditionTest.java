package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Allocation refuses to start when its inputs are missing, and leaves no partial state. */
@SpringBootTest @ActiveProfiles("test") @Transactional
class AllocationPreconditionTest {
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired AllocationRunRepository runs;

    Long eventId; String ownerEmail, studentEmail;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        ownerEmail = owner.getEmail();
        studentEmail = users.saveAndFlush(new User("student-" + suffix + "@test.local", "unused", UserRole.STUDENT)).getEmail();
        var event = new Event("Allocation preconditions", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        eventId = events.saveAndFlush(event).getId();
    }

    ResponseStatusException allocationFailure() {
        return assertThrows(ResponseStatusException.class, () -> seating.allocate(eventId, ownerEmail));
    }

    @Test void closedEventWithoutConfirmedBookingsCannotBeAllocated() {
        seating.configure(eventId, ownerEmail, new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of(), List.of(), true));
        eventService.changeEventStatus(eventId, "published", ownerEmail);
        eventService.changeEventStatus(eventId, "closed", ownerEmail);

        var failure = allocationFailure();
        assertEquals(409, failure.getStatusCode().value());
        assertEquals("No mock-payment-confirmed bookings to allocate", failure.getReason());
        assertFalse(runs.existsById(eventId));
    }

    @Test void identifiedSeatingWithoutSavedLayoutIsRejected() {
        eventService.changeEventStatus(eventId, "published", ownerEmail);
        var booking = checkout.checkout(eventId, studentEmail, new MockCheckoutRequest(false, null, false,
                UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS)).booking();
        eventService.changeEventStatus(eventId, "closed", ownerEmail);
        // Simulates inconsistent stored data: seat identifiers confirmed but no layout saved.
        var event = events.findById(eventId).orElseThrow();
        event.setSeatIdentifiersConfirmed(true);
        events.saveAndFlush(event);

        var failure = allocationFailure();
        assertEquals(409, failure.getStatusCode().value());
        assertEquals("Save a seating layout before allocation", failure.getReason());
        assertFalse(runs.existsById(eventId));
        var stored = bookings.findById(booking.id()).orElseThrow();
        assertEquals(BookingStatus.PENDING_ALLOCATION, stored.getStatus());
        assertNull(stored.getSeatCode());
    }
}
