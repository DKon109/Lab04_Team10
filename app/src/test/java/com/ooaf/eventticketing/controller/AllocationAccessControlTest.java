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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Only the owning organiser can run allocation through the API. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AllocationAccessControlTest {
    static final String PATH = "/api/organiser/events/{id}/allocate";
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired AllocationRunRepository runs;

    Long eventId, bookingId; User owner, student, otherOrganiser;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        student = users.saveAndFlush(new User("student-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        // Same society, but not the event owner.
        otherOrganiser = users.saveAndFlush(new User("other-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        var event = new Event("Allocation access control", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        eventId = events.saveAndFlush(event).getId();
        seating.configure(eventId, owner.getEmail(), new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of(), List.of(), true));
        eventService.changeEventStatus(eventId, "published", owner.getEmail());
        bookingId = checkout.checkout(eventId, student.getEmail(), new MockCheckoutRequest(false, null, false,
                UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS)).booking().id();
        eventService.changeEventStatus(eventId, "closed", owner.getEmail());
    }

    String bearer(User user) { return "Bearer " + tokens.issueToken(user).token(); }

    @Test void rejectsUnauthenticatedCustomerAndNonOwnerWithoutAllocating() throws Exception {
        mvc.perform(post(PATH, eventId)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH, eventId).header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(post(PATH, eventId).header("Authorization", bearer(otherOrganiser))).andExpect(status().isForbidden());

        assertFalse(runs.existsById(eventId));
        var booking = bookings.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.PENDING_ALLOCATION, booking.getStatus());
        assertNull(booking.getSeatCode());
    }

    @Test void ownerCanAllocate() throws Exception {
        mvc.perform(post(PATH, eventId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingCount").value(1));
        assertTrue(runs.existsById(eventId));
    }
}
