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
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SeatingIntegrationTest {
    @Autowired SeatingService seating;
    @Autowired SeatingMapManagementService managedSeating;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired AllocationRunRepository runs;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired TokenService tokens;
    @Autowired MockMvc mvc;
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
    SeatMapRequest managedMap(boolean attendeesChooseSeats) {
        var seats = List.of(
                new SeatMapRequest.SeatRequest("A1", "A1", "Wheelchair position", "Stalls", "A", 0, 1,
                        10, 20, true, false, false, List.of("Standard")),
                new SeatMapRequest.SeatRequest("A2", "A2", null, "Stalls", "A", 0, 2,
                        20, 20, false, false, false, List.of("Standard")));
        var privateSeats = List.of(
                new SeatMapRequest.SeatRequest("VIP-PRIVATE-1", "VIP 1", null, "VIP", "T1", 1, 1,
                        50, 50, false, false, false, List.of("VIP")));
        return new SeatMapRequest("Main auditorium", attendeesChooseSeats, true, true,
                Map.of("Standard", 99, "VIP", 99),
                List.of(
                        new SeatMapRequest.ElementRequest("row-a", SeatingElementType.ROW, "Row A", null,
                                0, 0, 0, 0, false, seats),
                        new SeatMapRequest.ElementRequest("vip-table", SeatingElementType.ROUND_TABLE, "VIP table", null,
                                50, 50, 0, 1, true, privateSeats),
                        new SeatMapRequest.ElementRequest("stage", SeatingElementType.OBJECT, "Stage", null,
                                0, -20, 0, 2, false, List.of())));
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
        var ticket = seating.ticket(first.id(), studentEmail);
        assertNotNull(ticket.layout()); assertNull(ticket.managedMap());
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
    @Test void allocatedSeatCannotBeRemovedAndRetryPreservesTicketAndExport() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(false));
        publish();
        var booking = book(studentEmail, false, null, false);
        close();
        var run = seating.allocate(eventId, ownerEmail);
        String originalSeat = seating.ticket(booking.id(), studentEmail).seatCode();
        assertNotNull(originalSeat);

        for (String removal : Arrays.asList(null, " ")) {
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> managedSeating.assignByOrganiser(eventId, booking.id(), ownerEmail,
                            new SeatAssignmentRequest(removal))).getStatusCode().value());
        }
        assertEquals(run.getCompletedAt(), seating.allocate(eventId, ownerEmail).getCompletedAt());
        var ticket = seating.ticket(booking.id(), studentEmail);
        assertEquals(BookingStatus.ALLOCATED, ticket.booking().status());
        assertEquals(originalSeat, ticket.seatCode());
        assertFalse(ticket.generalAdmission());
        String csv = seating.export(eventId, ownerEmail);
        assertTrue(csv.contains("\"" + originalSeat + "\""));
        assertFalse(csv.contains("GENERAL_ADMISSION"));

        // Moving to a replacement remains supported after allocation.
        managedSeating.assignByOrganiser(eventId, booking.id(), ownerEmail,
                new SeatAssignmentRequest("VIP-PRIVATE-1"));
        assertEquals("VIP-PRIVATE-1", seating.ticket(booking.id(), studentEmail).seatCode());
        assertTrue(seating.export(eventId, ownerEmail).contains("VIP-PRIVATE-1"));
    }

    @Test void managedMapPersistsElementsMappingsPrivateSeatsAndAutoMatchedCapacity() {
        var view = managedSeating.saveMap(eventId, ownerEmail, managedMap(true));

        assertEquals(3, view.totalSeats());
        assertEquals(2, view.publicCapacity());
        assertEquals(2, events.findById(eventId).orElseThrow().getCapacity());
        assertEquals(3, view.elements().size());
        assertEquals(2, view.ticketTypes().stream()
                .filter(type -> type.code().equals("STANDARD")).findFirst().orElseThrow().configuredCapacity());
        assertEquals(0, view.ticketTypes().stream()
                .filter(type -> type.code().equals("VIP")).findFirst().orElseThrow().configuredCapacity());
        assertEquals(SeatingMapManagementService.SeatStatus.PRIVATE,
                view.elements().stream().filter(element -> element.key().equals("vip-table"))
                        .findFirst().orElseThrow().seats().get(0).status());
    }
    @Test void attendeesCanChooseOnlyCompatibleAvailableSeatsWithoutLeakingBookingDetails() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(true));
        publish();
        var accessibleBooking = book(studentEmail, false, null, true);
        var standardBooking = book(friendEmail, false, null, false);

        assertEquals("A1", managedSeating.chooseSeat(accessibleBooking.id(), studentEmail,
                new SeatAssignmentRequest("a1")).seatCode());
        assertEquals("A2", managedSeating.chooseSeat(standardBooking.id(), friendEmail,
                new SeatAssignmentRequest("A2")).seatCode());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> managedSeating.chooseSeat(standardBooking.id(), friendEmail,
                        new SeatAssignmentRequest("A1"))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> managedSeating.chooseSeat(standardBooking.id(), friendEmail,
                        new SeatAssignmentRequest("VIP-PRIVATE-1"))).getStatusCode().value());

        var publicMap = managedSeating.attendeeMap(eventId);
        assertTrue(publicMap.elements().stream().flatMap(element -> element.seats().stream())
                .filter(seat -> seat.status() == SeatingMapManagementService.SeatStatus.ASSIGNED)
                .allMatch(seat -> seat.assignedBookingId() == null && seat.assignedBookingReference() == null));
        assertEquals(2, managedSeating.organiserMap(eventId, ownerEmail).assignedSeats());
    }
    @Test void managedMapRejectsDuplicateSeatCodesAndUnknownTicketMappings() {
        var first = new SeatMapRequest.SeatRequest("A1", "A1", null, "Stalls", "A", 0, 1,
                0, 0, false, false, false, List.of("UNKNOWN"));
        var duplicate = new SeatMapRequest.SeatRequest("a1", "A1 duplicate", null, "Stalls", "A", 0, 2,
                10, 0, false, false, false, List.of("STANDARD"));
        var request = new SeatMapRequest("Invalid map", true, false, true, Map.of("STANDARD", 2),
                List.of(new SeatMapRequest.ElementRequest("row-a", SeatingElementType.ROW, "Row A", null,
                        0, 0, 0, 0, false, List.of(first, duplicate))));

        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> managedSeating.saveMap(eventId, ownerEmail, request)).getStatusCode().value());
    }
    @Test void organiserCanAssignMoveAndRemoveAConfirmedBookingSeat() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(false));
        publish();
        var booking = book(studentEmail, false, null, false);

        assertEquals("A2", managedSeating.assignByOrganiser(eventId, booking.id(), ownerEmail,
                new SeatAssignmentRequest("A2")).seatCode());
        assertEquals("VIP-PRIVATE-1", managedSeating.assignByOrganiser(eventId, booking.id(), ownerEmail,
                new SeatAssignmentRequest("VIP-PRIVATE-1")).seatCode());
        assertNull(managedSeating.assignByOrganiser(eventId, booking.id(), ownerEmail,
                new SeatAssignmentRequest(null)).seatCode());
        assertEquals(BookingStatus.PENDING_ALLOCATION,
                bookings.findById(booking.id()).orElseThrow().getStatus());
    }
    @Test void managedMapIsReturnedOnPrivateTicketWithoutOtherBookingDetails() throws Exception {
        seating.configure(eventId, ownerEmail, layout(true, List.of()));
        managedSeating.saveMap(eventId, ownerEmail, managedMap(true));
        publish();
        var booking = book(studentEmail, false, null, false);
        var otherBooking = book(friendEmail, false, null, true);
        managedSeating.chooseSeat(booking.id(), studentEmail, new SeatAssignmentRequest("A2"));
        managedSeating.chooseSeat(otherBooking.id(), friendEmail, new SeatAssignmentRequest("A1"));
        close();
        seating.allocate(eventId, ownerEmail);

        var ticket = seating.ticket(booking.id(), studentEmail);
        assertNull(ticket.layout());
        assertNotNull(ticket.managedMap());
        assertEquals("A2", ticket.seatCode());
        assertEquals(3, ticket.managedMap().elements().size());
        assertTrue(ticket.managedMap().elements().stream().flatMap(element -> element.seats().stream())
                .allMatch(seat -> seat.assignedBookingId() == null && seat.assignedBookingReference() == null));
        mvc.perform(get("/api/bookings/{id}/ticket", booking.id())
                        .header("Authorization", "Bearer " + tokens.issueToken(student).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managedMap.name").value("Main auditorium"))
                .andExpect(jsonPath("$.managedMap.elements[0].seats[1].code").value("A2"))
                .andExpect(jsonPath("$.seatCode").value("A2"));
        mvc.perform(get("/api/bookings/{id}/ticket", booking.id())
                        .header("Authorization", "Bearer " + tokens.issueToken(friend).token()))
                .andExpect(status().isNotFound());
    }

    @Test void unconfirmedManagedMapRemainsGeneralAdmissionAndRejectsSeatAssignments() {
        var template = managedMap(true);
        var unconfirmed = new SeatMapRequest(template.name(), template.attendeesChooseSeats(),
                template.autoMatchTicketCapacities(), false, template.ticketTypeCapacities(), template.elements());
        managedSeating.saveMap(eventId, ownerEmail, unconfirmed);
        assertFalse(events.findById(eventId).orElseThrow().isSeatIdentifiersConfirmed());
        publish();
        var booking = book(studentEmail, false, null, false);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> managedSeating.chooseSeat(
                booking.id(), studentEmail, new SeatAssignmentRequest("A2"))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> managedSeating.assignByOrganiser(
                eventId, booking.id(), ownerEmail, new SeatAssignmentRequest("A2"))).getStatusCode().value());
        assertNull(bookings.findById(booking.id()).orElseThrow().getSeatCode());
        close(); seating.allocate(eventId, ownerEmail);
        assertTrue(seating.ticket(booking.id(), studentEmail).generalAdmission());
        assertNull(seating.ticket(booking.id(), studentEmail).seatCode());
        assertTrue(seating.export(eventId, ownerEmail).contains("GENERAL_ADMISSION"));
    }

    @Test void mapReplacementRequiresExplicitPhysicalIdentifierConfirmation() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(false));
        assertTrue(events.findById(eventId).orElseThrow().isSeatIdentifiersConfirmed());
        var template = managedMap(false);
        managedSeating.saveMap(eventId, ownerEmail, new SeatMapRequest(template.name(), false, true,
                false, template.ticketTypeCapacities(), template.elements()));
        assertFalse(events.findById(eventId).orElseThrow().isSeatIdentifiersConfirmed());
        managedSeating.saveMap(eventId, ownerEmail, template);
        assertTrue(events.findById(eventId).orElseThrow().isSeatIdentifiersConfirmed());
    }

    @Test void omittedPhysicalIdentifierConfirmationDefaultsToGeneralAdmission() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/organiser/events/{id}/seat-map", eventId)
                .header("Authorization", "Bearer " + tokens.issueToken(owner).token())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Unnumbered room","attendeesChooseSeats":false,
                     "autoMatchTicketCapacities":false,"ticketTypeCapacities":{},
                     "elements":[{"key":"row-a","type":"ROW","label":"Row A",
                       "positionX":0,"positionY":0,"rotationDegrees":0,"displayOrder":0,"privateElement":false,
                       "seats":[{"code":"A1","label":"A1","section":"Main","rowLabel":"A",
                         "rowIndex":0,"seatNumber":1,"positionX":0,"positionY":0,
                         "accessible":false,"blocked":false,"privateSeat":false,"ticketTypes":[]}]}]}
                    """))
                .andExpect(status().isOk());
        assertFalse(events.findById(eventId).orElseThrow().isSeatIdentifiersConfirmed());
    }

    @Test void organiserCannotAssignOrMoveAccessibilityBookingToRegularSeat() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(false)); publish();
        var booking = book(studentEmail, false, null, true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> managedSeating.assignByOrganiser(
                eventId, booking.id(), ownerEmail, new SeatAssignmentRequest("A2"))).getStatusCode().value());
        assertEquals(BookingStatus.PENDING_ALLOCATION, bookings.findById(booking.id()).orElseThrow().getStatus());
        assertNull(bookings.findById(booking.id()).orElseThrow().getSeatCode());
        close(); seating.allocate(eventId, ownerEmail);
        assertEquals("A1", seating.ticket(booking.id(), studentEmail).seatCode());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> managedSeating.assignByOrganiser(
                eventId, booking.id(), ownerEmail, new SeatAssignmentRequest("VIP-PRIVATE-1"))).getStatusCode().value());
        assertEquals("A1", seating.ticket(booking.id(), studentEmail).seatCode());
    }

    @Test void fullyPreassignedSplitGroupHasWarningsAndMetricsThatFollowOrganiserMoves() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(false)); publish();
        var first = book(studentEmail, true, null, true);
        var second = book(friendEmail, false, first.groupCode(), false);
        managedSeating.assignByOrganiser(eventId, first.id(), ownerEmail, new SeatAssignmentRequest("A1"));
        managedSeating.assignByOrganiser(eventId, second.id(), ownerEmail, new SeatAssignmentRequest("VIP-PRIVATE-1"));
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(1, run.getSplitGroupCount());
        assertNotNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNotNull(seating.ticket(second.id(), friendEmail).splitReason());
        assertTrue(seating.export(eventId, ownerEmail).contains("Group seats are not contiguous"));
        assertEquals(run.getCompletedAt(), seating.allocate(eventId, ownerEmail).getCompletedAt());
        assertEquals(1, seating.allocate(eventId, ownerEmail).getSplitGroupCount());

        managedSeating.assignByOrganiser(eventId, second.id(), ownerEmail, new SeatAssignmentRequest("A2"));
        assertEquals(0, runs.findById(eventId).orElseThrow().getSplitGroupCount());
        assertNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNull(seating.ticket(second.id(), friendEmail).splitReason());
        assertFalse(seating.export(eventId, ownerEmail).contains("Group seats are not contiguous"));
        managedSeating.assignByOrganiser(eventId, second.id(), ownerEmail, new SeatAssignmentRequest("VIP-PRIVATE-1"));
        assertEquals(1, runs.findById(eventId).orElseThrow().getSplitGroupCount());
        assertNotNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNotNull(seating.ticket(second.id(), friendEmail).splitReason());
        assertEquals(run.getCompletedAt(), seating.allocate(eventId, ownerEmail).getCompletedAt());
    }

    @Test void partiallyPreassignedSplitGroupIncludesEveryMemberInSplitReporting() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(false)); publish();
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), true);
        managedSeating.assignByOrganiser(eventId, first.id(), ownerEmail, new SeatAssignmentRequest("VIP-PRIVATE-1"));
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(1, run.getSplitGroupCount());
        assertEquals("VIP-PRIVATE-1", seating.ticket(first.id(), studentEmail).seatCode());
        assertEquals("A1", seating.ticket(second.id(), friendEmail).seatCode());
        assertNotNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNotNull(seating.ticket(second.id(), friendEmail).splitReason());
    }

    @Test void partiallyPreassignedContiguousGroupDoesNotGetFalseSplitWarnings() {
        managedSeating.saveMap(eventId, ownerEmail, managedMap(true)); publish();
        var first = book(studentEmail, true, null, true);
        var second = book(friendEmail, false, first.groupCode(), false);
        managedSeating.chooseSeat(first.id(), studentEmail, new SeatAssignmentRequest("A1"));
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(0, run.getSplitGroupCount());
        assertEquals("A1", seating.ticket(first.id(), studentEmail).seatCode());
        assertEquals("A2", seating.ticket(second.id(), friendEmail).seatCode());
        assertNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNull(seating.ticket(second.id(), friendEmail).splitReason());
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
}
