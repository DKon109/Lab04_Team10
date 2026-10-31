package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The database itself rejects two bookings holding the same seat in one event. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingSeatConstraintTest {
    @Autowired BookingRepository bookings;
    @Autowired EventRepository events;
    @Autowired UserRepository users;

    Event first, second; User alice, bob;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        alice = users.saveAndFlush(new User("alice-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        bob = users.saveAndFlush(new User("bob-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        first = events.saveAndFlush(event("First"));
        second = events.saveAndFlush(event("Second"));
    }

    Event event(String title) {
        return new Event(title, "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
    }

    Booking seated(Event event, User user, String seatCode) {
        var booking = new Booking("REF-" + UUID.randomUUID().toString().substring(0, 8), event, user,
                null, false, UUID.randomUUID().toString());
        booking.assignSeat(seatCode, null);
        return booking;
    }

    @Test void sameSeatTwiceInOneEventIsRejected() {
        bookings.saveAndFlush(seated(first, alice, "A1"));
        assertThrows(DataIntegrityViolationException.class, () -> bookings.saveAndFlush(seated(first, bob, "A1")));
    }

    @Test void sameSeatCodeInAnotherEventIsAllowed() {
        bookings.saveAndFlush(seated(first, alice, "A1"));
        assertDoesNotThrow(() -> bookings.saveAndFlush(seated(second, bob, "A1")));
    }

    @Test void generalAdmissionBookingsWithoutSeatsDoNotCollide() {
        bookings.saveAndFlush(seated(first, alice, null));
        assertDoesNotThrow(() -> bookings.saveAndFlush(seated(first, bob, null)));
    }
}
