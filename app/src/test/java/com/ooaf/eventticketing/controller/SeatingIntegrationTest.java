package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.ooaf.eventticketing.allocation.AllocationStrategy;
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
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
    @Autowired EntityManager entityManager;
    @Autowired CampusVenueTemplateService campusTemplates;
    @Autowired FrontStageTemplateService frontStageTemplates;
    @Autowired ExternalVenueTemplateService externalTemplates;
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

    @Test void metricsArePersistedPrivateAndUpdatedAfterAnOrganiserMove() throws Exception {
        saveAllocationMap(List.of(
                allocationRow("row-a", List.of(allocationSeat("A1", "A", 0, 1, false),
                        allocationSeat("A2", "A", 0, 2, false))),
                allocationRow("row-b", List.of(allocationSeat("B1", "B", 1, 1, false),
                        allocationSeat("B2", "B", 1, 2, false)))));
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> seating.metrics(eventId, ownerEmail)).getStatusCode().value());
        close(); seating.allocate(eventId, ownerEmail);
        entityManager.flush(); entityManager.clear();
        var run = runs.findById(eventId).orElseThrow();
        assertEquals(100.0, run.getPercentageGroupsTogether());
        assertEquals(100.0, run.getAverageGroupContiguityPercentage());
        assertEquals(0.0, run.getAverageSplitDistance());
        assertEquals(0, run.getIsolatedSeatCount());
        assertEquals(0, run.getDuplicateAllocationCount());
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        mvc.perform(get("/api/organiser/events/{id}/allocation/metrics", eventId)
                .header("Authorization", "Bearer " + tokens.issueToken(owner).token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.percentageGroupsTogether").value(100.0))
                .andExpect(jsonPath("$.duplicateAllocationCount").value(0))
                .andExpect(jsonPath("$.averageGroupContiguityPercentage").value(100.0))
                .andExpect(jsonPath("$.elapsedMillis").value(run.getElapsedMillis()));
        mvc.perform(get("/api/organiser/events/{id}/allocation/metrics", eventId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/organiser/events/{id}/allocation/metrics", eventId)
                .header("Authorization", "Bearer " + tokens.issueToken(users.findByEmail(studentEmail).orElseThrow()).token()))
                .andExpect(status().isForbidden());
        var outsider = users.saveAndFlush(new User("other-" + eventId + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Other"));
        mvc.perform(get("/api/organiser/events/{id}/allocation/metrics", eventId)
                .header("Authorization", "Bearer " + tokens.issueToken(outsider).token()))
                .andExpect(status().isForbidden());
        managedSeating.assignByOrganiser(eventId, second.id(), ownerEmail, new SeatAssignmentRequest("B1"));
        entityManager.flush(); entityManager.clear();
        run = runs.findById(eventId).orElseThrow();
        assertEquals(0.0, run.getPercentageGroupsTogether());
        assertEquals(2.5, run.getAverageSplitDistance()); // weighted row + element boundary
        assertEquals(0.0, run.getAverageGroupContiguityPercentage());
        assertEquals(2, run.getIsolatedSeatCount());
        assertEquals(1, run.getSplitGroupCount());
        assertEquals(run.getPercentageGroupsTogether(), seating.metrics(eventId, ownerEmail).percentageGroupsTogether());
        assertNotNull(seating.ticket(first.id(), studentEmail).splitReason());
    }

    @Test void generalAdmissionMetricsDoNotClaimContiguousSeating() {
        seating.configure(eventId, ownerEmail, layout(false, List.of())); publish();
        book(studentEmail, false, null, false); close();
        var run = seating.allocate(eventId, ownerEmail);
        assertNull(run.getPercentageGroupsTogether());
        assertNull(run.getAverageGroupContiguityPercentage());
        assertNull(run.getIsolatedSeatCount());
        assertNull(seating.metrics(eventId, ownerEmail).averageSplitDistance());
        assertEquals(0, run.getDuplicateAllocationCount());
    }

    @ParameterizedTest
    @CsvSource({"DEFAULT,B1", "PREFER_SAME_ROW,A6", "OMITTED,B1"})
    void allocationStrategyControlsPlacementAndCannotChangeAfterCompletion(String requested, String expected) throws Exception {
        saveAllocationMap(List.of(
                allocationRow("row-a", List.of(allocationSeat("A1", "A", 0, 1, false),
                        allocationSeat("A6", "A", 0, 6, false))),
                allocationRow("row-b", List.of(allocationSeat("B1", "B", 1, 1, false)))));
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);
        managedSeating.chooseSeat(first.id(), studentEmail, new SeatAssignmentRequest("A1"));
        close();
        String token = tokens.issueToken(users.findByEmail(ownerEmail).orElseThrow()).token();
        var request = post("/api/organiser/events/{id}/allocate", eventId).header("Authorization", "Bearer " + token);
        String selected = requested.equals("OMITTED") ? "DEFAULT" : requested;
        if (!requested.equals("OMITTED")) request.param("strategy", requested);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.strategy").value(selected));
        entityManager.flush(); entityManager.clear();
        var run = runs.findById(eventId).orElseThrow();
        assertEquals(AllocationStrategy.valueOf(selected), run.getStrategy());
        assertEquals("A1", seating.ticket(first.id(), studentEmail).seatCode());
        assertEquals(expected, seating.ticket(second.id(), friendEmail).seatCode());
        assertEquals(run.getCompletedAt(), seating.allocate(eventId, ownerEmail).getCompletedAt());
        mvc.perform(post("/api/organiser/events/{id}/allocate", eventId).param("strategy", selected)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.strategy").value(selected));
        mvc.perform(post("/api/organiser/events/{id}/allocate", eventId)
                .param("strategy", selected.equals("DEFAULT") ? "PREFER_SAME_ROW" : "DEFAULT")
                .header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        entityManager.flush(); entityManager.clear();
        assertEquals(expected, seating.ticket(second.id(), friendEmail).seatCode());
        assertEquals(run.getCompletedAt(), runs.findById(eventId).orElseThrow().getCompletedAt());
    }

    @ParameterizedTest
    @CsvSource({"DEFAULT,6,80", "PREFER_SAME_ROW,6,80", "DEFAULT,5,75", "PREFER_SAME_ROW,5,75"})
    void persistsBalancedRowSubgroupsAndContiguity(AllocationStrategy strategy, int count, double contiguity) {
        var rows = new ArrayList<SeatMapRequest.ElementRequest>();
        for (int row = 0; row < 2; row++) {
            String label = String.valueOf((char) ('A' + row));
            var seats = new ArrayList<SeatMapRequest.SeatRequest>();
            for (int number = 1; number <= 4; number++) seats.add(allocationSeat(label + number, label, row, number, false));
            rows.add(allocationRow("row-" + label, seats));
        }
        saveAllocationMap(rows);
        String groupCode = null;
        for (int i = 0; i < count; i++) {
            var user = users.saveAndFlush(new User("balanced-" + eventId + "-" + i + "@test.local", "unused", UserRole.STUDENT));
            var booking = book(user.getEmail(), i == 0, groupCode, false);
            groupCode = booking.groupCode();
        }
        close(); seating.allocate(eventId, ownerEmail, strategy);
        entityManager.flush(); entityManager.clear();
        var stored = bookings.findByEventIdOrderByCreatedAtAsc(eventId);
        var sizes = stored.stream().collect(java.util.stream.Collectors.groupingBy(
                b -> b.getSeatCode().substring(0, 1), java.util.stream.Collectors.counting())).values().stream().sorted().toList();
        assertEquals(count == 6 ? List.of(3L, 3L) : List.of(2L, 3L), sizes);
        var run = runs.findById(eventId).orElseThrow();
        assertEquals(strategy, run.getStrategy());
        assertEquals(1, run.getSplitGroupCount());
        assertEquals(contiguity, run.getAverageGroupContiguityPercentage());
        assertEquals(contiguity, seating.metrics(eventId, ownerEmail).averageGroupContiguityPercentage());
        assertEquals(count, stored.stream().map(Booking::getSeatCode).distinct().count());
    }

    @Test void invalidStrategyCannotCreateAnAllocation() throws Exception {
        seating.configure(eventId, ownerEmail, layout(true, List.of())); publish();
        var booking = book(studentEmail, false, null, false); close();
        mvc.perform(post("/api/organiser/events/{id}/allocate", eventId).param("strategy", "UNKNOWN")
                .header("Authorization", "Bearer " + tokens.issueToken(users.findByEmail(ownerEmail).orElseThrow()).token()))
                .andExpect(status().isBadRequest());
        assertFalse(runs.existsById(eventId));
        assertEquals(BookingStatus.PENDING_ALLOCATION, bookings.findById(booking.id()).orElseThrow().getStatus());
    }

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

        // Reload the managed map and ticket from the database.
        entityManager.flush();
        entityManager.clear();

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

    SeatMapRequest.ElementRequest table(String key, SeatingElementType type) {
        var seats = new ArrayList<SeatMapRequest.SeatRequest>();
        for (int side = 0; side < 2; side++) for (int number = 1; number <= 8; number++) {
            String row = side == 0 ? "N" : "S";
            seats.add(allocationSeat(key + "-" + row + number, row, side, number, false));
        }
        return new SeatMapRequest.ElementRequest(key, type, key, null, 0, 0, 0, 0, false, seats);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = SeatingElementType.class,
            names = {"ROUND_TABLE", "SQUARE_TABLE", "RECTANGULAR_TABLE"})
    void sameTableWarningsAndPersistedMetricsFollowOrganiserMoves(SeatingElementType type) {
        saveAllocationMap(List.of(table("T1", type), table("T2", type)));
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);
        managedSeating.chooseSeat(first.id(), studentEmail, new SeatAssignmentRequest("T1-N1"));
        managedSeating.chooseSeat(second.id(), friendEmail, new SeatAssignmentRequest("T1-S1"));
        close(); seating.allocate(eventId, ownerEmail);
        entityManager.flush(); entityManager.clear();
        assertEquals(type, managedSeating.organiserMap(eventId, ownerEmail).elements().get(0).type());
        var run = runs.findById(eventId).orElseThrow();
        assertEquals(0, run.getSplitGroupCount());
        assertEquals(100.0, run.getPercentageGroupsTogether());
        assertEquals(0.0, run.getAverageGroupContiguityPercentage());
        assertNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNull(seating.ticket(second.id(), friendEmail).splitReason());

        managedSeating.assignByOrganiser(eventId, second.id(), ownerEmail, new SeatAssignmentRequest("T2-S1"));
        entityManager.flush(); entityManager.clear();
        run = runs.findById(eventId).orElseThrow();
        assertEquals(1, run.getSplitGroupCount());
        assertEquals(0.0, run.getPercentageGroupsTogether());
        assertNotNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNotNull(seating.ticket(second.id(), friendEmail).splitReason());
        assertEquals(run.getPercentageGroupsTogether(), seating.metrics(eventId, ownerEmail).percentageGroupsTogether());

        managedSeating.assignByOrganiser(eventId, second.id(), ownerEmail, new SeatAssignmentRequest("T1-S1"));
        entityManager.flush(); entityManager.clear();
        assertEquals(0, runs.findById(eventId).orElseThrow().getSplitGroupCount());
        assertEquals(100.0, seating.metrics(eventId, ownerEmail).percentageGroupsTogether());
        assertNull(seating.ticket(second.id(), friendEmail).splitReason());
    }

    @Test void pendingMembersJoinAPreassignedTableAfterPersistenceReload() {
        saveAllocationMap(List.of(table("T1", SeatingElementType.RECTANGULAR_TABLE),
                table("T2", SeatingElementType.RECTANGULAR_TABLE)));
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);
        managedSeating.chooseSeat(first.id(), studentEmail, new SeatAssignmentRequest("T2-N1"));
        close(); entityManager.flush(); entityManager.clear();
        seating.allocate(eventId, ownerEmail);
        entityManager.flush(); entityManager.clear();
        assertEquals("T2-N1", seating.ticket(first.id(), studentEmail).seatCode());
        assertEquals("T2-N2", seating.ticket(second.id(), friendEmail).seatCode());
        assertEquals(100.0, runs.findById(eventId).orElseThrow().getPercentageGroupsTogether());
    }

    @Test void rectangularTablesCanBeSavedThroughTheExistingMapApi() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        mvc.perform(put("/api/organiser/events/{id}/seat-map", eventId)
                .header("Authorization", "Bearer " + tokens.issueToken(owner).token())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Banquet", "attendeesChooseSeats":true,"autoMatchTicketCapacities":true,
                 "identifiersConfirmed":true,"ticketTypeCapacities":{"STANDARD":0},"elements":[
                 {"key":"T1","type":"RECTANGULAR_TABLE","label":"Table 1",
                  "positionX":0,"positionY":0,"rotationDegrees":0,"displayOrder":0,"privateElement":false,"seats":[
                  {"code":"T1-N1","label":"N1","section":"Main","rowLabel":"N","rowIndex":0,
                   "seatNumber":1,"positionX":0,"positionY":0,"accessible":false,"blocked":false,
                   "privateSeat":false,"ticketTypes":["STANDARD"]},
                  {"code":"T1-S1","label":"S1","section":"Main","rowLabel":"S","rowIndex":1,
                   "seatNumber":1,"positionX":0,"positionY":0,"accessible":false,"blocked":false,
                   "privateSeat":false,"ticketTypes":["STANDARD"]}]}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].type").value("RECTANGULAR_TABLE"))
                .andExpect(jsonPath("$.elements[0].seats.length()").value(2));
    }

    SeatMapRequest.SeatRequest allocationSeat(String code, String row, int rowIndex, int number, boolean accessible) {
        return new SeatMapRequest.SeatRequest(code, code, null, "Main", row, rowIndex, number,
                number * 10, rowIndex * 20, accessible, false, false, List.of("STANDARD"));
    }

    SeatMapRequest.ElementRequest allocationRow(String key, List<SeatMapRequest.SeatRequest> seats) {
        return new SeatMapRequest.ElementRequest(key, SeatingElementType.ROW, key, null,
                0, 0, 0, 0, false, seats);
    }

    void saveAllocationMap(List<SeatMapRequest.ElementRequest> elements) {
        managedSeating.saveMap(eventId, ownerEmail, new SeatMapRequest("Allocation regression", true, true,
                true, Map.of("STANDARD", 0), elements));
        publish();
    }

    @Test void separateElementsDoNotBeatAnAvailableContiguousRow() {
        saveAllocationMap(List.of(
                allocationRow("left", List.of(allocationSeat("A1", "A", 0, 1, false))),
                allocationRow("right", List.of(allocationSeat("A2", "A", 0, 2, false))),
                allocationRow("row-b", List.of(allocationSeat("B1", "B", 1, 1, false),
                        allocationSeat("B2", "B", 1, 2, false)))));
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(0, run.getSplitGroupCount());
        assertEquals(Set.of("B1", "B2"), Set.of(seating.ticket(first.id(), studentEmail).seatCode(),
                seating.ticket(second.id(), friendEmail).seatCode()));
        assertNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNull(seating.ticket(second.id(), friendEmail).splitReason());
    }

    @Test void pendingMemberIsSeatedBesidePreassignedMemberAtEndOfRow() {
        saveAllocationMap(List.of(allocationRow("row-a", List.of(
                allocationSeat("A1", "A", 0, 1, false), allocationSeat("A2", "A", 0, 2, false),
                allocationSeat("A3", "A", 0, 3, false), allocationSeat("A4", "A", 0, 4, false)))));
        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);
        managedSeating.chooseSeat(first.id(), studentEmail, new SeatAssignmentRequest("A4"));
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(0, run.getSplitGroupCount());
        entityManager.flush(); entityManager.clear();
        assertEquals("A4", seating.ticket(first.id(), studentEmail).seatCode());
        assertEquals("A3", seating.ticket(second.id(), friendEmail).seatCode());
        assertNull(seating.ticket(first.id(), studentEmail).splitReason());
        assertNull(seating.ticket(second.id(), friendEmail).splitReason());
        assertFalse(seating.export(eventId, ownerEmail).contains("Group seats are not contiguous"));
    }

    @Test void pendingMemberFillsGapBetweenMultiplePreassignedGroupMembers() {
        saveAllocationMap(List.of(allocationRow("row-a", List.of(
                allocationSeat("A1", "A", 0, 1, false), allocationSeat("A2", "A", 0, 2, false),
                allocationSeat("A3", "A", 0, 3, false), allocationSeat("A4", "A", 0, 4, false)))));
        var third = users.saveAndFlush(new User("third-" + eventId + "@test.local", "unused", UserRole.STUDENT));
        var firstBooking = book(studentEmail, true, null, false);
        var secondBooking = book(friendEmail, false, firstBooking.groupCode(), false);
        var thirdBooking = book(third.getEmail(), false, firstBooking.groupCode(), false);
        managedSeating.chooseSeat(firstBooking.id(), studentEmail, new SeatAssignmentRequest("A4"));
        managedSeating.chooseSeat(secondBooking.id(), friendEmail, new SeatAssignmentRequest("A2"));
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(0, run.getSplitGroupCount());
        assertEquals("A4", seating.ticket(firstBooking.id(), studentEmail).seatCode());
        assertEquals("A2", seating.ticket(secondBooking.id(), friendEmail).seatCode());
        assertEquals("A3", seating.ticket(thirdBooking.id(), third.getEmail()).seatCode());
    }

    @Test void completingPreassignedGroupReservesAccessibilityForOtherBookings() {
        saveAllocationMap(List.of(allocationRow("row-a", List.of(
                allocationSeat("A1", "A", 0, 1, true), allocationSeat("A2", "A", 0, 2, false),
                allocationSeat("A3", "A", 0, 3, false)))));
        var third = users.saveAndFlush(new User("accessible-" + eventId + "@test.local", "unused", UserRole.STUDENT));
        var firstBooking = book(studentEmail, true, null, false);
        var secondBooking = book(friendEmail, false, firstBooking.groupCode(), false);
        var accessibleBooking = book(third.getEmail(), false, null, true);
        managedSeating.chooseSeat(firstBooking.id(), studentEmail, new SeatAssignmentRequest("A2"));
        close(); var run = seating.allocate(eventId, ownerEmail);
        assertEquals(0, run.getSplitGroupCount());
        assertEquals("A2", seating.ticket(firstBooking.id(), studentEmail).seatCode());
        assertEquals("A3", seating.ticket(secondBooking.id(), friendEmail).seatCode());
        assertEquals("A1", seating.ticket(accessibleBooking.id(), third.getEmail()).seatCode());
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

    @Test
    void anotherOrganiserCannotDeleteManagedMap() throws Exception {
        var original = managedSeating.saveMap(
                eventId, ownerEmail, managedMap(false)
        );
        Integer originalCapacity = events.findById(eventId).orElseThrow().getCapacity();

        var otherOwner = users.saveAndFlush(new User(
                "other-owner-" + UUID.randomUUID() + "@test.local",
                "unused",
                UserRole.COMMITTEE_MEMBER,
                "Demo society"
        ));
        String token = tokens.issueToken(otherOwner).token();

        mvc.perform(delete("/api/organiser/events/{id}/seat-map", eventId
        ).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        //Reload to verify that the rejected request changed nothing.
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                original,
                managedSeating.organiserMap(eventId, ownerEmail)
        );
        var unchanged = events.findById(eventId).orElseThrow();
        assertEquals(originalCapacity, unchanged.getCapacity());
        assertTrue(unchanged.isSeatIdentifiersConfirmed());
    }

    @Test
    void publishedManagedMapCannotBeReplacedOrDeleted() {
        var original = managedSeating.saveMap(
                eventId, ownerEmail, managedMap(false)
        );
        Integer originalCapacity = events.findById(eventId).orElseThrow().getCapacity();
        publish();

        var replacementError = assertThrows(
                ResponseStatusException.class, () -> managedSeating.saveMap(
                        eventId, ownerEmail, managedMap(true)
                )
        );
        assertEquals(409, replacementError.getStatusCode().value());

        var deletionError = assertThrows(
                ResponseStatusException.class,
                () -> managedSeating.deleteMap(eventId, ownerEmail)
        );
        assertEquals(409, deletionError.getStatusCode().value());

        // Reload to verify that both rejected operations preserved the map.
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                original,
                managedSeating.organiserMap(eventId, ownerEmail)
        );
        var unchanged = events.findById(eventId).orElseThrow();
        assertEquals(EventStatus.PUBLISHED, unchanged.getStatus());
        assertEquals(originalCapacity, unchanged.getCapacity());
        assertTrue(unchanged.isSeatIdentifiersConfirmed());
    }

    @Test
    void campusTemplatesAreAvailableOnlyToOrganisers() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();
        String studentToken = tokens.issueToken(student).token();

        mvc.perform(get("/api/organiser/venue-templates")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id")
                        .value("usyd-seymour-everest"))
                .andExpect(jsonPath("$[0].map.ticketTypeCapacities.STANDARD")
                        .value(505))
                .andExpect(jsonPath("$[0].map.identifiersConfirmed")
                        .value(false));

        mvc.perform(get("/api/organiser/venue-templates")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/organiser/venue-templates"))
                .andExpect(status().isUnauthorized());

    }

    @Test
    void campusTemplateCanBeSavedAndReloadedWithoutConfirmingSeats() {
        var template = campusTemplates.listTemplates().stream()
                .filter(item -> "usyd-seymour-everest".equals(item.id()))
                .findFirst()
                .orElseThrow();

        managedSeating.saveMap(eventId, ownerEmail, template.map());

        // Flush writes and reload from the database instead of the JPA cache.
        entityManager.flush();
        entityManager.clear();

        var reloaded = managedSeating.organiserMap(eventId, ownerEmail);
        assertEquals(template.name(), reloaded.name());
        assertEquals(505L, reloaded.totalSeats());
        assertEquals(505L, reloaded.publicCapacity());

        Set<String> expectCodes = template.map().elements().stream()
                .flatMap(element -> element.seats().stream())
                .map(SeatMapRequest.SeatRequest::code)
                .collect(java.util.stream.Collectors.toSet());

        Set<String> actualCodes = reloaded.elements().stream()
                .flatMap(element -> element.seats().stream())
                .map(SeatingMapManagementService.SeatView::code)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expectCodes, actualCodes);

        var event = events.findById(eventId).orElseThrow();
        assertEquals(Integer.valueOf(505), event.getCapacity());
        assertFalse(event.isSeatIdentifiersConfirmed());
    }

    @Test
    void everestDemoAllocatesAccessibleBookingsToAssumedPositions() {
        var template = campusTemplates.listTemplates().stream()
                .filter(item -> "usyd-seymour-everest".equals(item.id()))
                .findFirst()
                .orElseThrow();

        var source = template.map();

        // Confirm only this synthetic test copy, not the real venue template.
        var demoMap = new SeatMapRequest(
                "Everest coursework demo - assumed accessibility",
                false,
                source.autoMatchTicketCapacities(),
                true,
                source.ticketTypeCapacities(),
                source.elements()
        );

        managedSeating.saveMap(eventId, ownerEmail, demoMap);
        publish();

        var first = book(studentEmail, false, null, true);
        var second = book(friendEmail, false, null, true);

        close();
        seating.allocate(eventId, ownerEmail);

        entityManager.flush();
        entityManager.clear();

        var firstTicket = seating.ticket(first.id(), studentEmail);
        var secondTicket = seating.ticket(second.id(), friendEmail);

        assertEquals(BookingStatus.ALLOCATED, firstTicket.booking().status());
        assertEquals(BookingStatus.ALLOCATED, secondTicket.booking().status());
        assertNotNull(firstTicket.seatCode());
        assertNotNull(secondTicket.seatCode());
        assertNotEquals(firstTicket.seatCode(), secondTicket.seatCode());

        assertEquals(
                Set.of("A12", "A26"),
                Set.of(firstTicket.seatCode(), secondTicket.seatCode())
        );

        assertEquals(Boolean.FALSE, source.identifiersConfirmed());
    }

    @Test
    void frontStageTemplateCanBeGeneratedOnlyByOrganisers() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();
        String studentToken = tokens.issueToken(student).token();

        String body = """
                {
                "name": "Front-stage API demo",
                "rows": 2,
                "seatsPerRow": 6,
                "aisleAfter": 3,
                "blockedCodes": ["B6"],
                "accessibleCodes": ["A1"]
                }
                """;

        String endpoint = "/api/organiser/venue-templates/front-stage";

        mvc.perform(post(endpoint)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Front-stage API demo"))

                .andExpect(jsonPath("$.ticketTypeCapacities.STANDARD").value(11))
                .andExpect(jsonPath("$.identifiersConfirmed").value(false))
                .andExpect(jsonPath("$.elements.length()").value(4))
                .andExpect(jsonPath("$.elements[0].seats[0].code").value("A1"))
                .andExpect(jsonPath("$.elements[0].seats[0].accessible").value(true))
                .andExpect(jsonPath("$.elements[3].seats[2].code").value("B6"))
                .andExpect(jsonPath("$.elements[3].seats[2].blocked").value(true));

        mvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void frontStageTemplateApiRejectsInvalidInput() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();

        var invalidBodies = List.of(
                """
                        {
                        "name": "Invalid dimensions",
                        "rows": 0,
                        "seatsPerRow": 6,
                        "aisleAfter": 0,
                        "blockedCodes": [],
                        "accessibleCodes": []
                        }
                        """,
                """
                        {
                        "name": "Invalid aisle",
                        "rows": 2,
                        "seatsPerRow": 6,
                        "aisleAfter": 6,
                        "blockedCodes": [],
                        "accessibleCodes": []
                        }
                        """,
                """
                        {
                        "name": "Unknown seat",
                        "rows": 1,
                        "seatsPerRow": 3,
                        "aisleAfter": 0,
                        "blockedCodes": ["A4"],
                        "accessibleCodes": []
                        }
                        """
        );

        for (String body : invalidBodies) {
            mvc.perform(post("/api/organiser/venue-templates/front-stage")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void frontStageTemplatesCanBeSavedAndReloaded() {
        var request = new FrontStageTemplateRequest(
                "Persisted front-stage demo",
                2, 6, 3, List.of("B6"), List.of("A1")
        );

        var map = frontStageTemplates.generate(request);
        managedSeating.saveMap(eventId, ownerEmail, map);

        entityManager.flush();
        entityManager.clear();

        var reloaded = managedSeating.organiserMap(eventId, ownerEmail);

        assertEquals("Persisted front-stage demo", reloaded.name());
        assertEquals(12L, reloaded.totalSeats());
        assertEquals(11L, reloaded.publicCapacity());
        assertEquals(4, reloaded.elements().size());

        var seats = reloaded.elements().stream()
                .flatMap(element -> element.seats().stream())
                .toList();

        var accessibleSeat = seats.stream()
                .filter(seat -> seat.code().equals("A1"))
                .findFirst()
                .orElseThrow();
        assertTrue(accessibleSeat.accessible());
        assertEquals("Left", accessibleSeat.section());

        var blockedSeat = seats.stream()
                .filter(seat -> seat.code().equals("B6"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                SeatingMapManagementService.SeatStatus.BLOCKED,
                blockedSeat.status()
        );
        assertEquals("Right", blockedSeat.section());

        var event = events.findById(eventId).orElseThrow();
        assertEquals(Integer.valueOf(11), event.getCapacity());
        assertFalse(event.isSeatIdentifiersConfirmed());
    }

    @Test
    void frontStageAllocationKeepsGroupOnOneSideOfAisle() {
        var generated = frontStageTemplates.generate(
                new FrontStageTemplateRequest(
                        "Front-stage allocation demo",
                        1, 4, 2,
                        List.of(),
                        List.of()
                )
        );

        // Confirm identifiers only for this synthetic allocation fixture.
        var confirmMap = new SeatMapRequest(
                generated.name(),
                generated.attendeesChooseSeats(),
                generated.autoMatchTicketCapacities(),
                true,
                generated.ticketTypeCapacities(),
                generated.elements()
        );

        managedSeating.saveMap(eventId, ownerEmail, confirmMap);
        publish();

        var first = book(studentEmail, true, null, false);
        var second = book(friendEmail, false, first.groupCode(), false);

        close();

        seating.allocate(eventId, ownerEmail);

        var firstTicket = seating.ticket(first.id(), studentEmail);
        var secondTicket = seating.ticket(second.id(), friendEmail);

        assertEquals(BookingStatus.ALLOCATED, firstTicket.booking().status());
        assertEquals(BookingStatus.ALLOCATED, secondTicket.booking().status());
        assertNotNull(firstTicket.seatCode());
        assertNotNull(secondTicket.seatCode());
        assertNotEquals(firstTicket.seatCode(), secondTicket.seatCode());

        Set<String> assignedCodes = Set.of(
                firstTicket.seatCode(),
                secondTicket.seatCode()
        );

        assertTrue(
                assignedCodes.equals(Set.of("A1", "A2"))
                || assignedCodes.equals(Set.of("A3", "A4")),
                "The group must occupy adjacent seats on one side of the aisle"
        );

        assertEquals(Boolean.FALSE, generated.identifiersConfirmed());
    }

    @Test
    void assignedSeatingEventCannotBePublishedWithoutLayout() {
        var event = events.findById(eventId).orElseThrow();
        event.setSeatIdentifiersConfirmed(true);
        events.saveAndFlush(event);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> publish()
        );

        assertEquals(409, error.getStatusCode().value());
        assertEquals("Save the seating layout before publishing an assigned-seating event",
                error.getReason());

        var unchanged = events.findById(eventId).orElseThrow();
        assertEquals(EventStatus.DRAFT, unchanged.getStatus());
        assertEquals(
                EventRegistrationStatus.OPEN,
                unchanged.getRegistrationStatus()
        );
    }

    @Test
    void closingEventWithGeneratedLayoutRejectsNewBookings() {
        var map = frontStageTemplates.generate(
                new FrontStageTemplateRequest(
                        "Registration closure demo",
                        1, 4, 2,
                        List.of(),
                        List.of()
                )
        );

        managedSeating.saveMap(eventId, ownerEmail, map);
        publish();

        var first = book(studentEmail, false, null, false);

        close();

        var closed = events.findById(eventId).orElseThrow();
        assertEquals(EventStatus.CLOSED, closed.getStatus());
        assertEquals(
                EventRegistrationStatus.CLOSED,
                closed.getRegistrationStatus()
        );

        var error = assertThrows(
                ResponseStatusException.class,
                () -> book(friendEmail, false, null, false)
        );

        assertEquals(409, error.getStatusCode().value());
        assertEquals(
                "Registration is closed for this event",
                error.getReason()
        );

        var savedBookings = bookings.findByEventIdOrderByCreatedAtAsc(eventId);
        assertEquals(1, savedBookings.size());
        assertEquals(first.id(), savedBookings.get(0).getId());
    }

    @Test
    void campusTemplateApiIncludesReginald() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();

        String reginald =
                "$[?(@.id == 'usyd-seymour-reginald')]";

        mvc.perform(get("/api/organiser/venue-templates")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(reginald,
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath(reginald + ".name",
                        org.hamcrest.Matchers.contains(
                                "Seymour Centre - Reginald Theatre")))
                .andExpect(jsonPath(reginald + ".sourceUrl",
                        org.hamcrest.Matchers.contains(
                                "https://www.seymourcentre.com/media/"
                                        + "3rjab23b/reginald-seating-plan.pdf")))
                .andExpect(jsonPath(
                        reginald + ".map.ticketTypeCapacities.STANDARD",
                        org.hamcrest.Matchers.contains(148)))
                .andExpect(jsonPath(
                        reginald + ".map.identifiersConfirmed",
                        org.hamcrest.Matchers.contains(false)));
    }

    @Test
    void reginaldTemplatePreservesLayoutAfterReload() throws Exception {
        var template = campusTemplates.listTemplates().stream()
                .filter(item -> "usyd-seymour-reginald".equals(item.id()))
                .findFirst()
                .orElseThrow();

        managedSeating.saveMap(eventId, ownerEmail, template.map());

        // Flush changes and reload entities from the database.
        entityManager.flush();
        entityManager.clear();

        var reloaded = managedSeating.organiserMap(eventId, ownerEmail);

        assertEquals(template.name(), reloaded.name());
        assertEquals(148L, reloaded.totalSeats());
        assertEquals(148L, reloaded.publicCapacity());
        assertEquals(17, reloaded.elements().size());

        for (var expectedElement : template.map().elements()) {
            var actualElement = reloaded.elements().stream()
                    .filter(element -> expectedElement.key().equals(element.key()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(expectedElement.type(), actualElement.type());
            assertEquals(expectedElement.seats().size(), actualElement.seats().size());

            for (var expectedSeat : expectedElement.seats()) {
                var actualSeat = actualElement.seats().stream()
                        .filter(seat -> expectedSeat.code().equals(seat.code()))
                        .findFirst()
                        .orElseThrow();

                assertEquals(expectedSeat.section(), actualSeat.section());
                assertEquals(expectedSeat.rowLabel(), actualSeat.rowLabel());
                assertEquals(expectedSeat.rowIndex(), actualSeat.rowIndex());
                assertEquals(expectedSeat.seatNumber(), actualSeat.seatNumber());
                assertEquals(expectedSeat.positionX(), actualSeat.positionX());
                assertEquals(expectedSeat.positionY(), actualSeat.positionY());
                assertEquals(expectedSeat.accessible(), actualSeat.accessible());
            }
        }

        var savedEvent = events.findById(eventId).orElseThrow();
        assertEquals(Integer.valueOf(148), savedEvent.getCapacity());
        assertFalse(savedEvent.isSeatIdentifiersConfirmed());
    }

    @Test
    void yorkTemplatePreservesLayoutAfterReload() throws Exception {
        var template = campusTemplates.listTemplates().stream()
                .filter(item -> "usyd-seymour-york".equals(item.id()))
                .findFirst()
                .orElseThrow();

        managedSeating.saveMap(eventId, ownerEmail, template.map());

        // Flush changes and reload entities from the database.
        entityManager.flush();
        entityManager.clear();

        var reloaded = managedSeating.organiserMap(eventId, ownerEmail);

        assertEquals(template.name(), reloaded.name());
        assertEquals(781L, reloaded.totalSeats());
        assertEquals(781L, reloaded.publicCapacity());
        assertEquals(60, reloaded.elements().size());

        for (var expectedElement : template.map().elements()) {
            var actualElement = reloaded.elements().stream()
                    .filter(element -> expectedElement.key().equals(element.key()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(expectedElement.type(), actualElement.type());
            assertEquals(expectedElement.seats().size(), actualElement.seats().size());

            for (var expectedSeat : expectedElement.seats()) {
                var actualSeat = actualElement.seats().stream()
                        .filter(seat -> expectedSeat.code().equals(seat.code()))
                        .findFirst()
                        .orElseThrow();

                assertEquals(expectedSeat.section(), actualSeat.section());
                assertEquals(expectedSeat.rowLabel(), actualSeat.rowLabel());
                assertEquals(expectedSeat.rowIndex(), actualSeat.rowIndex());
                assertEquals(expectedSeat.seatNumber(), actualSeat.seatNumber());
                assertEquals(expectedSeat.positionX(), actualSeat.positionX());
                assertEquals(expectedSeat.positionY(), actualSeat.positionY());
                assertEquals(expectedSeat.accessible(), actualSeat.accessible());
            }
        }

        var savedEvent = events.findById(eventId).orElseThrow();
        assertEquals(Integer.valueOf(781), savedEvent.getCapacity());
        assertFalse(savedEvent.isSeatIdentifiersConfirmed());
    }

    @Test
    void campusTemplateApiIncludesYork() throws Exception {
        var owner =  users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();

        String york = "$[?(@.id == 'usyd-seymour-york')]";

        mvc.perform(get("/api/organiser/venue-templates")
                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(york,
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath(york + ".name",
                        org.hamcrest.Matchers.contains(
                                "Seymour Centre - York Theatre")))
                .andExpect(jsonPath(york + ".sourceUrl",
                        org.hamcrest.Matchers.contains(
                                "https://www.seymourcentre.com/media/"
                                + "vyfjqbae/york-theatre-seating-plan.pdf")))
                .andExpect(jsonPath(york + ".map.ticketTypeCapacities.STANDARD",
                        org.hamcrest.Matchers.contains(781)))
                .andExpect(jsonPath(york + ".map.identifiersConfirmed",
                        org.hamcrest.Matchers.contains(false)));
    }

    @ParameterizedTest
    @CsvSource({
            "FRONT, Left",
            "FAN, Left",
            "CENTRAL, North-Left"
    })
    void externalTemplatesCanBeGeneratedOnlyByOrganisers(
            String template, String expectedSection
    ) throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();
        String studentToken = tokens.issueToken(student).token();

        String body = """
                {
                "name": "External API demo",
                "template": "%s",
                "rows": 2,
                "seatsPerRow": 6,
                "aisleAfter": 3,
                "blockedCodes": ["B6"],
                "accessibleCodes": ["A1"]
                }
                """.formatted(template);

        String endpoint = "/api/organiser/venue-templates/external";

        mvc.perform(post(endpoint)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("External API demo"))

                .andExpect(jsonPath("$.ticketTypeCapacities.STANDARD").value(11))

                .andExpect(jsonPath("$.identifiersConfirmed").value(false))
                .andExpect(jsonPath("$.elements.length()").value(4))

                .andExpect(jsonPath("$.elements[0].seats[0].code").value("A1"))
                .andExpect(jsonPath("$.elements[0].seats[0].section")
                        .value(expectedSection))

                .andExpect(jsonPath("$.elements[0].seats[0].accessible")
                        .value(true))

                .andExpect(jsonPath("$.elements[3].seats[2].code").value("B6"))
                .andExpect(jsonPath("$.elements[3].seats[2].blocked")
                        .value(true));

        mvc.perform(post(endpoint)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void externalTemplateApiRejectsInvalidRequest() throws Exception {
        var owner = users.findByEmail(ownerEmail).orElseThrow();
        String ownerToken = tokens.issueToken(owner).token();

        String validBody = """
                {
                    "name": "External validation demo",
                    "template": "FAN",
                    "rows": 2,
                    "seatsPerRow": 6,
                    "aisleAfter": 3,
                    "blockedCodes": ["B6"],
                    "accessibleCodes": ["A1"]
                    }
        """;

        var invalidBodies = List.of(
                validBody.replace("\"FAN\"", "\"UNKNOWN\""),
                validBody.replace("\"FAN\"", "null"),
                validBody.replace("\"rows\": 2", "\"rows\": 0"),
                validBody.replace("\"seatsPerRow\": 6",
                        "\"seatsPerRow\": 21"
                ),
                validBody.replace("\"aisleAfter\": 3", "\"aisleAfter\": 6"),
                validBody.replace("[\"B6\"]", "[\"B7\"]"),
                validBody.replace("[\"A1\"]", "[\"A1\", \"A1\"]"),
                validBody.replace("[\"A1\"]", "[\"B6\"]"),
                validBody.replace(
                        "\"External validation demo\"", "\"\""
                ),
                validBody.replace("\"FAN\"", "\"CENTRAL\"")
                        .replace("\"rows\": 2", "\"rows\": 1")
                        .replace("[\"B6\"]", "[]")
        );
                for (int i = 0; i < invalidBodies.size(); i++) {
                    mvc.perform(post("/api/organiser/venue-templates/external")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBodies.get(i)))
                            .andExpect(status().isBadRequest());
                }
    }

    @ParameterizedTest
    @CsvSource({"FRONT", "FAN", "CENTRAL"})
    void externalTemplatesCanBeSavedAndReloaded(
            String template
    ) {
        var request = new ExternalVenueTemplateRequest(
                "Persisted external demo",
                SeatingLayoutRequest.Template.valueOf(template),
                3, 4, 2,
                List.of("C4"),
                List.of("A1")
        );

        var generated = externalTemplates.generate(request);
        managedSeating.saveMap(eventId, ownerEmail, generated);

        entityManager.flush();
        entityManager.clear();

        var reloaded = managedSeating.organiserMap(eventId, ownerEmail);

        assertEquals(generated.name(), reloaded.name());
        assertEquals(12L, reloaded.totalSeats());
        assertEquals(11L, reloaded.publicCapacity());
        assertEquals(6, reloaded.elements().size());

        var actualSeats = reloaded.elements().stream()
                .flatMap(element -> element.seats().stream())
                .toList();

        assertEquals(12L, actualSeats.stream()
                .map(seat -> seat.code()).distinct().count());

        for (var element : generated.elements()) {
            for (var expected : element.seats()) {
                var actual = actualSeats.stream()
                        .filter(seat -> seat.code().equals(expected.code()))
                        .findFirst().orElseThrow();

                assertEquals(expected.section(), actual.section());
                assertEquals(expected.rowLabel(), actual.rowLabel());
                assertEquals(expected.rowIndex(), actual.rowIndex());
                assertEquals(expected.seatNumber(), actual.seatNumber());
                assertEquals(expected.positionX(), actual.positionX(), 0.000001);
                assertEquals(expected.positionY(), actual.positionY(), 0.000001);
                assertEquals(expected.accessible(), actual.accessible());

                if (expected.blocked()) {
                    assertEquals(
                            SeatingMapManagementService.SeatStatus.BLOCKED,
                            actual.status()
                    );
                } else {
                    assertNotEquals(
                            SeatingMapManagementService.SeatStatus.BLOCKED,
                            actual.status()
                    );
                }
            }
        }

        var savedEvent = events.findById(eventId).orElseThrow();
        assertEquals(Integer.valueOf(11), savedEvent.getCapacity());
        assertFalse(savedEvent.isSeatIdentifiersConfirmed());
    }

    @ParameterizedTest
    @CsvSource({"FRONT", "FAN", "CENTRAL"})
    void externalTemplateAllocationPreservesAccessibilityAndAisle(
            String template
    ) {
        var generated = externalTemplates.generate(
                new ExternalVenueTemplateRequest(
                        "External allocation demo",

                        SeatingLayoutRequest.Template.valueOf(template),
                        2, 4, 2,
                        List.of("B4"),
                        List.of("A1")
                )
        );

                // Confirm identifiers only for this synthetic test fixture.
        var confirmedMap = new SeatMapRequest(
                generated.name(),
                generated.attendeesChooseSeats(),
                generated.autoMatchTicketCapacities(),
                true,
                generated.ticketTypeCapacities(),
                generated.elements()
        );
        managedSeating.saveMap(eventId, ownerEmail, confirmedMap);
        publish();

        var first = book(studentEmail, true, null, true);
        var second = book(friendEmail, false, first.groupCode(), false
        );

        close();
        seating.allocate(eventId, ownerEmail);

        var firstTicket = seating.ticket(first.id(), studentEmail);
        var secondTicket = seating.ticket(second.id(), friendEmail);

        assertEquals(
                BookingStatus.ALLOCATED,
                firstTicket.booking().status()
        );
        assertEquals(
                BookingStatus.ALLOCATED,
                secondTicket.booking().status()
        );
        // A1 is the only accessible seat; A2 is its adjacent companion.
        assertEquals("A1", firstTicket.seatCode());
        assertEquals("A2", secondTicket.seatCode());
        assertNotEquals(firstTicket.seatCode(), secondTicket.seatCode());

        assertNotEquals("B4", firstTicket.seatCode());
        assertNotEquals("B4", secondTicket.seatCode());

        assertEquals(Boolean.FALSE, generated.identifiersConfirmed());
    }
}
