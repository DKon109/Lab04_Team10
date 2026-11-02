package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tickets report payment and allocation state honestly before and without allocation. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class TicketPaymentStatusTest {
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired BookingService bookingService;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;

    Long eventId; String ownerEmail; User paid, legacy;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        ownerEmail = owner.getEmail();
        paid = users.saveAndFlush(new User("paid-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        legacy = users.saveAndFlush(new User("legacy-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        var event = new Event("Ticket payment status", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        eventId = events.saveAndFlush(event).getId();
        seating.configure(eventId, ownerEmail, new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of(), List.of(), true));
        eventService.changeEventStatus(eventId, "published", ownerEmail);
    }

    @Test void paidTicketBeforeAllocationHasNoSeatYet() throws Exception {
        var booking = checkout.checkout(eventId, paid.getEmail(), new MockCheckoutRequest(false, null, false,
                UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS)).booking();

        mvc.perform(get("/api/bookings/{id}/ticket", booking.id())
                        .header("Authorization", "Bearer " + tokens.issueToken(paid).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("MOCK_SUCCESS"))
                .andExpect(jsonPath("$.seatCode").doesNotExist())
                .andExpect(jsonPath("$.booking.status").value("PENDING_ALLOCATION"));
    }

    @Test void bookingWithoutPaymentIsLabelledUnverifiedAndStaysUnseated() {
        var booking = bookingService.createBooking(eventId, legacy.getEmail(),
                new CreateBookingRequest(false, "", false, UUID.randomUUID().toString()));
        checkout.checkout(eventId, paid.getEmail(), new MockCheckoutRequest(false, null, false,
                UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS));
        eventService.changeEventStatus(eventId, "closed", ownerEmail);
        seating.allocate(eventId, ownerEmail);

        var ticket = seating.ticket(booking.id(), legacy.getEmail());
        assertEquals("UNVERIFIED_LEGACY", ticket.paymentStatus());
        assertNull(ticket.seatCode());
        assertEquals(BookingStatus.PENDING_ALLOCATION, ticket.booking().status());
    }
}
