package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PrintableSeatingPlanIntegrationTest {
    @Autowired SeatingService seating;
    @Autowired SeatingMapManagementService maps;
    @Autowired FrontStageTemplateService frontStage;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    Long eventId; User owner, attendee, friend;

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        owner = users.saveAndFlush(new User("plan-owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo"));
        attendee = users.saveAndFlush(new User("plan-attendee-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        friend = users.saveAndFlush(new User("plan-friend-" + suffix + "@test.local", "unused", UserRole.STUDENT));
        var event = new Event("Print <script>alert(1)</script>", "Demo", null, LocalDateTime.now().plusDays(2),
                "Room & <Hall>", "Sydney", "USYD", "Demo", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner); eventId = events.saveAndFlush(event).getId();
    }
    BookingResponse book(User user, boolean create, String group, boolean accessible) {
        return checkout.checkout(eventId, user.getEmail(), new MockCheckoutRequest(create, group, accessible,
                UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS)).booking();
    }
    void publish() { eventService.changeEventStatus(eventId, "published", owner.getEmail()); }
    void allocate() { eventService.changeEventStatus(eventId, "closed", owner.getEmail()); seating.allocate(eventId, owner.getEmail()); entityManager.flush(); entityManager.clear(); }
    String download() throws Exception {
        return mvc.perform(get("/api/organiser/events/{id}/allocation/print", eventId)
                .header("Authorization", "Bearer " + tokens.issueToken(owner).token()))
                .andExpect(status().isOk()).andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=event-" + eventId + "-seating-plan.html"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest @EnumSource(SeatingLayoutRequest.Template.class)
    void printsLegacyFamiliesWithAnonymousReferencesAndEscapedText(SeatingLayoutRequest.Template family) throws Exception {
        seating.configure(eventId, owner.getEmail(), new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                family, null, 2, 4, 2, List.of("B4"), List.of("A1"), true));
        publish(); var first = book(attendee, true, null, true); var second = book(friend, false, first.groupCode(), false);
        allocate(); String html = download();
        assertTrue(html.contains(first.bookingRef())); assertTrue(html.contains(second.bookingRef()));
        assertTrue(html.contains("<td>G1</td>")); assertTrue(html.contains("A1 · Accessible"));
        assertTrue(html.contains("B4 · Unavailable")); assertTrue(html.contains("STAGE"));
        assertTrue(html.contains("Print &lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("Room &amp; &lt;Hall&gt;")); assertFalse(html.contains("<script>"));
        assertFalse(html.contains(first.groupCode())); assertFalse(html.contains(attendee.getEmail()));
        assertFalse(html.contains(friend.getEmail())); assertFalse(html.contains("paymentRef"));
        assertTrue(html.contains("thead{display:table-header-group}"));
        // Saved aisle boundaries remain a larger gap between A2 and A3.
        assertTrue(html.contains("translate(30.0 0.0)") || family == SeatingLayoutRequest.Template.FAN);
        assertTrue(html.contains("translate(90.0 0.0)") || family == SeatingLayoutRequest.Template.FAN);
    }

    @Test void managedPlanPreservesGeometryAndOmitsNotesAndUnconfirmedBookings() throws Exception {
        var template = frontStage.generate(new FrontStageTemplateRequest("Managed demo", 2, 4, 2,
                List.of("B4"), List.of("A1")));
        // Confirmation is explicit for this synthetic test fixture.
        maps.saveMap(eventId, owner.getEmail(), new SeatMapRequest(template.name(), false, true, true,
                template.ticketTypeCapacities(), template.elements()));
        publish(); var confirmed = book(attendee, false, null, true);
        bookings.saveAndFlush(new Booking("LEGACY-OMITTED", events.findById(eventId).orElseThrow(), friend,
                null, false, UUID.randomUUID().toString()));
        allocate(); String html = download();
        assertTrue(html.contains(confirmed.bookingRef())); assertFalse(html.contains("LEGACY-OMITTED"));
        assertFalse(html.contains(attendee.getEmail())); assertFalse(html.contains("assignedBookingId"));
        var stored = maps.organiserMap(eventId, owner.getEmail());
        var seat = stored.elements().stream().flatMap(e -> e.seats().stream()).filter(s -> s.code().equals("A1")).findFirst().orElseThrow();
        assertTrue(html.contains("translate(" + seat.positionX() * 3 + " " + seat.positionY() * 3 + ")"));
    }

    @Test void generalAdmissionHasReferencesWithoutAnInventedMap() throws Exception {
        publish(); var booking = book(attendee, false, null, false); allocate();
        String html = download();
        assertTrue(html.contains("General admission: no seats are assigned."));
        assertTrue(html.contains(booking.bookingRef())); assertFalse(html.contains("<svg"));
    }

    @Test void rejectsPreAllocationAndUnauthorisedAccess() throws Exception {
        String path = "/api/organiser/events/{id}/allocation/print";
        mvc.perform(get(path, eventId)).andExpect(status().isUnauthorized());
        mvc.perform(get(path, eventId).header("Authorization", "Bearer " + tokens.issueToken(owner).token()))
                .andExpect(status().isConflict());
        mvc.perform(get(path, eventId).header("Authorization", "Bearer " + tokens.issueToken(attendee).token()))
                .andExpect(status().isForbidden());
        var other = users.saveAndFlush(new User("other-" + eventId + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Other"));
        mvc.perform(get(path, eventId).header("Authorization", "Bearer " + tokens.issueToken(other).token()))
                .andExpect(status().isForbidden());
    }
}
