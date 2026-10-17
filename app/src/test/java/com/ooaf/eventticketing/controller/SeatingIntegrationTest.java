package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SeatingIntegrationTest {
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired AllocationRunRepository runs;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    Long eventId; String ownerEmail, studentEmail, friendEmail; User student, friend;
    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        ownerEmail = owner.getEmail();
        student = users.saveAndFlush(new User("student-" + suffix + "@test.local", "unused", UserRole.STUDENT)); studentEmail = student.getEmail();
        friend = users.saveAndFlush(new User("friend-" + suffix + "@test.local", "unused", UserRole.STUDENT)); friendEmail = friend.getEmail();
        var event = new Event("Seating integration demo", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room", "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner); eventId = events.saveAndFlush(event).getId();
    }
    SeatingLayoutRequest layout(boolean identified, List<String> accessible) {
        return new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL, SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of("B4"), accessible, identified);
    }
    BookingResponse book(String email, boolean create, String group, boolean accessible) {
        return checkout.checkout(eventId, email, new MockCheckoutRequest(create, group, accessible, UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS)).booking();
    }
    void publish() { eventService.changeEventStatus(eventId, "published", ownerEmail); }
    void close() { eventService.changeEventStatus(eventId, "closed", ownerEmail); }

    @Test void fullLifecyclePersistsPrivateTicketsAndAnonymousExport() throws Exception {
        var config = seating.configure(eventId, ownerEmail, layout(true, List.of("A1")));
        assertEquals(7, config.event().getCapacity()); publish();
        var first = book(studentEmail, true, null, true);
        var second = book(friendEmail, false, first.groupCode(), false);
        assertNull(seating.ticket(first.id(), studentEmail).seatCode());
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(2, run.getBookingCount()); assertEquals(0, run.getSplitGroupCount());

        // Flush pending writes and reload data without the persistence context cache.
        entityManager.flush();
        entityManager.clear();

        var reloaded = seating.management(eventId, ownerEmail);
        assertEquals(config.layout(), reloaded.layout());
        assertEquals("closed", reloaded.event().getStatus());
        assertEquals(EventRegistrationStatus.CLOSED, reloaded.event().getRegistrationStatus());
        assertEquals(2, reloaded.allocation().getBookingCount());

        var ticket = seating.ticket(first.id(), studentEmail);
        assertEquals("A1", ticket.seatCode()); assertEquals("A2", seating.ticket(second.id(), friendEmail).seatCode());
        assertEquals(BookingStatus.ALLOCATED, ticket.booking().status());
        assertEquals(run.getCompletedAt(), seating.allocate(eventId, ownerEmail).getCompletedAt());
        String csv = seating.export(eventId, ownerEmail);
        assertTrue(csv.contains(first.bookingRef())); assertFalse(csv.contains(studentEmail)); assertFalse(csv.contains("user_id"));
        mvc.perform(get("/api/bookings/{id}/ticket", first.id()).header("Authorization", "Bearer " + tokens.issueToken(student).token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seatCode").value("A1")).andExpect(jsonPath("$.paymentStatus").value("MOCK_SUCCESS"));
        mvc.perform(get("/api/bookings/{id}/ticket", first.id()).header("Authorization", "Bearer " + tokens.issueToken(friend).token())).andExpect(status().isNotFound());
    }
    @Test void allocationBeforeCloseCannotChangeBookings() {
        seating.configure(eventId, ownerEmail, layout(true, List.of("A1"))); publish(); var booking = book(studentEmail, false, null, false);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> seating.allocate(eventId, ownerEmail)).getStatusCode().value());
        assertFalse(runs.existsById(eventId)); assertEquals(BookingStatus.PENDING_ALLOCATION, bookings.findById(booking.id()).orElseThrow().getStatus());
    }
    @Test void publishedLayoutIsImmutable() {
        seating.configure(eventId, ownerEmail, layout(true, List.of("A1"))); publish();
        assertThrows(ResponseStatusException.class, () -> seating.configure(eventId, ownerEmail, layout(true, List.of("A2"))));
        assertEquals(1, seating.management(eventId, ownerEmail).layout().version());
    }
    @Test void sameSocietyDoesNotGrantAnotherOrganiserOwnership() {
        var other = users.saveAndFlush(new User("other-" + UUID.randomUUID() + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> seating.management(eventId, other.getEmail())).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> seating.configure(eventId, other.getEmail(), layout(true, List.of())));
    }
    @Test void missingAccessibleCapacityRollsBackWholeAllocation() {
        seating.configure(eventId, ownerEmail, layout(true, List.of())); publish(); var booking = book(studentEmail, false, null, true); close();
        assertThrows(ResponseStatusException.class, () -> seating.allocate(eventId, ownerEmail));
        assertFalse(runs.existsById(eventId)); assertNull(bookings.findById(booking.id()).orElseThrow().getSeatCode());
        assertEquals(BookingStatus.PENDING_ALLOCATION, bookings.findById(booking.id()).orElseThrow().getStatus());
    }
    @Test void generalAdmissionCreatesTicketWithoutInventingSeat() {
        publish(); var booking = book(studentEmail, false, null, false); close(); seating.allocate(eventId, ownerEmail);
        var ticket = seating.ticket(booking.id(), studentEmail);
        assertTrue(ticket.generalAdmission()); assertNull(ticket.seatCode()); assertEquals(BookingStatus.ALLOCATED, ticket.booking().status());
        assertTrue(seating.export(eventId, ownerEmail).contains("GENERAL_ADMISSION"));
    }
    @Test void invalidSeatCodesAndAccessibleBlockedOverlapAreRejected() {
        assertThrows(ResponseStatusException.class, () -> seating.configure(eventId, ownerEmail, layout(true, List.of("Z99"))));
        assertThrows(ResponseStatusException.class, () -> seating.configure(eventId, ownerEmail, layout(true, List.of("B4"))));
    }
    @Test void allThreeExternalFamiliesAndCampusDemoProfilesArePersisted() {
        for (var family : SeatingLayoutRequest.Template.values()) {
            var result = seating.configure(eventId, ownerEmail, new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL, family, null, 3, 6, 3, List.of(), List.of("A1"), true));
            assertEquals(family.name(), result.layout().template()); assertEquals(18, result.layout().seats().size());
        }
        for (var profile : SeatingService.CAMPUS_PROFILES) {
            var result = seating.configure(eventId, ownerEmail, new SeatingLayoutRequest(SeatingLayoutRequest.Source.CAMPUS_DEMO, SeatingLayoutRequest.Template.FRONT, profile.id(), 1, 1, 0, List.of(), List.of(), false));
            assertEquals(profile.rows() * profile.seatsPerRow(), result.layout().seats().size());
            assertEquals("CAMPUS_DEMO", result.layout().source());
        }
    }
    @Test void persistsThreeHundredUniqueSeatsWithinTenSeconds() {
        seating.configure(eventId, ownerEmail, new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 15, 20, 10, List.of(), List.of(), true));
        publish();
        var fixtureBookings = new ArrayList<Booking>();
        for (int i = 0; i < 300; i++) {
            var attendee = users.save(new User("perf-" + eventId + "-" + i + "@test.local", "unused", UserRole.STUDENT));
            fixtureBookings.add(new Booking("PERF-" + eventId + "-" + i, events.findById(eventId).orElseThrow(), attendee, "G" + i / 5, false, UUID.randomUUID().toString()));
        }
        bookings.saveAllAndFlush(fixtureBookings);
        var fixtureAttempts = fixtureBookings.stream().map(b -> new CheckoutAttempt(UUID.randomUUID().toString(), "MOCK-" + UUID.randomUUID(), b.getUser().getId(), eventId, "performance-fixture", MockCheckoutRequest.Outcome.SUCCESS, b.getId())).toList();
        attempts.saveAllAndFlush(fixtureAttempts); close();
        long start = System.nanoTime(); var run = seating.allocate(eventId, ownerEmail);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("PERSISTED_ALLOCATION_300_ATTENDEES_MS=" + elapsed);
        assertEquals(300, run.getBookingCount());
        assertEquals(300, bookings.findByEventIdOrderByCreatedAtAsc(eventId).stream().map(Booking::getSeatCode).filter(Objects::nonNull).distinct().count());
        assertTrue(elapsed < 10_000, "Persisted allocation must finish within 10 seconds on this test machine");
    }

    @Test
    void layoutApiRejectsZeroRowsWithoutSaving() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String token = tokens.issueToken(owner).token();

        mvc.perform(put("/api/organiser/events/{id}/seating", eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                        "source": "EXTERNAL",
                        "template": "FRONT",
                        "rows": 0,
                        "seatsPerRow": 4,
                        "aisleAfter": 0,
                        "blockedCodes": [],
                        "identifiersConfirmed": true
                        }
                        """))
                .andExpect(status().isBadRequest());

        assertNull(seating.management(eventId, ownerEmail).layout());
    }

    @Test
    void oversizedLayoutDoesNotReplaceSavedLayout() throws Exception {
        var original = seating.configure(
                eventId, ownerEmail, layout(true, List.of("A1")));

        var oversized = new SeatingLayoutRequest(
                SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT,
                null,
                16,
                20,
                0,
                List.of(),
                List.of(),
                true
        );

        var exception = assertThrows(
                ResponseStatusException.class,
                () -> seating.configure(eventId, ownerEmail, oversized)
        );

        assertEquals(400, exception.getStatusCode().value());

        var current = seating.management(eventId, ownerEmail);
        assertEquals(original.layout(), current.layout());
        assertEquals(original.event().getCapacity(),
                current.event().getCapacity());
    }
}
