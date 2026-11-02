package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Only bookings with a successful mock payment may enter an allocation run. */
@SpringBootTest @ActiveProfiles("test") @Transactional
class AllocationConfirmedBookingsTest {
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired BookingService bookingService;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired EntityManager entityManager;

    Long eventId; String ownerEmail, paidEmail, legacyEmail, declinedEmail;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        ownerEmail = owner.getEmail();
        paidEmail = users.saveAndFlush(new User("paid-" + suffix + "@test.local", "unused", UserRole.STUDENT)).getEmail();
        legacyEmail = users.saveAndFlush(new User("legacy-" + suffix + "@test.local", "unused", UserRole.STUDENT)).getEmail();
        declinedEmail = users.saveAndFlush(new User("declined-" + suffix + "@test.local", "unused", UserRole.STUDENT)).getEmail();
        var event = new Event("Confirmed-only allocation", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        eventId = events.saveAndFlush(event).getId();
        seating.configure(eventId, ownerEmail, new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of(), List.of(), true));
        eventService.changeEventStatus(eventId, "published", ownerEmail);
    }

    MockCheckoutResponse pay(String email, MockCheckoutRequest.Outcome outcome) {
        return checkout.checkout(eventId, email, new MockCheckoutRequest(false, null, false, UUID.randomUUID().toString(), outcome));
    }

    @Test void unpaidAndDeclinedBookingsReceiveNoSeat() {
        var paid = pay(paidEmail, MockCheckoutRequest.Outcome.SUCCESS).booking();
        // A booking created without a checkout attempt has no recorded payment.
        var legacy = bookingService.createBooking(eventId, legacyEmail,
                new CreateBookingRequest(false, "", false, UUID.randomUUID().toString()));
        assertNull(pay(declinedEmail, MockCheckoutRequest.Outcome.DECLINED).booking());
        eventService.changeEventStatus(eventId, "closed", ownerEmail);

        var run = seating.allocate(eventId, ownerEmail);
        entityManager.flush(); entityManager.clear();

        assertEquals(1, run.getBookingCount());
        var storedPaid = bookings.findById(paid.id()).orElseThrow();
        assertEquals(BookingStatus.ALLOCATED, storedPaid.getStatus());
        assertNotNull(storedPaid.getSeatCode());
        var storedLegacy = bookings.findById(legacy.id()).orElseThrow();
        assertEquals(BookingStatus.PENDING_ALLOCATION, storedLegacy.getStatus());
        assertNull(storedLegacy.getSeatCode());
        assertEquals(1, bookings.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(Booking::getSeatCode).filter(Objects::nonNull).count());

        String csv = seating.export(eventId, ownerEmail);
        assertTrue(csv.contains(paid.bookingRef()));
        assertFalse(csv.contains(legacy.bookingRef()));
    }
}
