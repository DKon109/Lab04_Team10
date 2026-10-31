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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contract of the anonymous allocation CSV export. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AllocationExportApiTest {
    static final String PATH = "/api/organiser/events/{id}/allocation/export";
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired EntityManager entityManager;

    Long eventId; User owner, student, otherOrganiser; BookingResponse booking;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        student = users.saveAndFlush(new User("student-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        otherOrganiser = users.saveAndFlush(new User("other-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        var event = new Event("Allocation export", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        eventId = events.saveAndFlush(event).getId();
        seating.configure(eventId, owner.getEmail(), new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of(), List.of(), true));
        eventService.changeEventStatus(eventId, "published", owner.getEmail());
        booking = checkout.checkout(eventId, student.getEmail(), new MockCheckoutRequest(false, null, false,
                UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS)).booking();
        eventService.changeEventStatus(eventId, "closed", owner.getEmail());
    }

    String bearer(User user) { return "Bearer " + tokens.issueToken(user).token(); }

    void allocate() {
        seating.allocate(eventId, owner.getEmail());
        entityManager.flush(); entityManager.clear();
    }

    @Test void rejectsUnauthorisedCallersAndExportBeforeAllocation() throws Exception {
        mvc.perform(get(PATH, eventId)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH, eventId).header("Authorization", bearer(owner))).andExpect(status().isConflict());
        allocate();
        mvc.perform(get(PATH, eventId).header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get(PATH, eventId).header("Authorization", bearer(otherOrganiser))).andExpect(status().isForbidden());
    }

    @Test void ownerDownloadsAnonymousCsvAttachment() throws Exception {
        allocate();
        String body = mvc.perform(get(PATH, eventId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=event-" + eventId + "-allocation.csv"))
                .andReturn().getResponse().getContentAsString();

        var lines = body.split("\r\n");
        assertEquals("booking_reference,group_reference,seat,split_reason", lines[0]);
        assertEquals(2, lines.length);
        assertTrue(lines[1].startsWith("\"" + booking.bookingRef() + "\","));
        assertFalse(body.contains(student.getEmail()));
    }

    @Test void escapesQuotesAndCommasInsideFields() {
        allocate();
        var stored = bookings.findById(booking.id()).orElseThrow();
        stored.assignSeat(stored.getSeatCode(), "Split, \"nearest\" seats");
        bookings.saveAndFlush(stored);

        String row = seating.export(eventId, owner.getEmail()).split("\r\n")[1];
        assertTrue(row.endsWith(",\"Split, \"\"nearest\"\" seats\""), row);
    }
}
