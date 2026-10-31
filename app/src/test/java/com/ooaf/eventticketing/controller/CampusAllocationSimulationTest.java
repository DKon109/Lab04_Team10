package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.allocation.AllocationStrategy;
import com.ooaf.eventticketing.dto.CampusVenueTemplateResponse;
import com.ooaf.eventticketing.dto.MockCheckoutRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simulates the organiser journey on the three Seymour Centre campus templates at full house and half
 * occupancy: save the managed map, publish, confirm bookings, close, allocate, then produce the anonymous
 * CSV and printable plan. Allocation is timed as in AllocationBenchmarkTest; outputs are timed separately.
 * Each CSV and plan is written to target/allocation-simulation for inspection.
 */
@SpringBootTest @ActiveProfiles("test") @Transactional
class CampusAllocationSimulationTest {
    private static final int[] GROUP_SIZES = {1, 2, 3, 5, 7};
    private static final int PROPOSAL_ATTENDEE_LIMIT = 300;
    private static final Path OUTPUT_DIR = Path.of("target", "allocation-simulation");

    @Autowired SeatingService seating;
    @Autowired SeatingMapManagementService maps;
    @Autowired CampusVenueTemplateService campusTemplates;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired EntityManager entityManager;

    @ParameterizedTest(name = "{0}: {1}, {2}")
    @CsvSource({
            "usyd-seymour-everest,FULL,DEFAULT",
            "usyd-seymour-everest,FULL,PREFER_SAME_ROW",
            "usyd-seymour-everest,HALF,DEFAULT",
            "usyd-seymour-everest,HALF,PREFER_SAME_ROW",
            "usyd-seymour-reginald,FULL,DEFAULT",
            "usyd-seymour-reginald,FULL,PREFER_SAME_ROW",
            "usyd-seymour-reginald,HALF,DEFAULT",
            "usyd-seymour-reginald,HALF,PREFER_SAME_ROW",
            "usyd-seymour-york,FULL,DEFAULT",
            "usyd-seymour-york,FULL,PREFER_SAME_ROW",
            "usyd-seymour-york,HALF,DEFAULT",
            "usyd-seymour-york,HALF,PREFER_SAME_ROW"
    })
    void simulatesCampusVenueThroughAnonymousOutputs(String templateId, String occupancy, AllocationStrategy strategy)
            throws IOException {
        var template = campusTemplates.listTemplates().stream()
                .filter(candidate -> candidate.id().equals(templateId)).findFirst().orElseThrow();
        Set<String> usableCodes = usableSeatCodes(template.map());
        Set<String> accessibleCodes = template.map().elements().stream().filter(element -> !element.privateElement())
                .flatMap(element -> element.seats().stream())
                .filter(seat -> seat.accessible() && !seat.blocked() && !seat.privateSeat())
                .map(SeatMapRequest.SeatRequest::code).collect(Collectors.toSet());
        int count = occupancy.equals("FULL") ? usableCodes.size() : usableCodes.size() / 2;

        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("sim-owner-" + suffix + "@uni.sydney.edu.au", "unused",
                UserRole.COMMITTEE_MEMBER, "Simulation"));
        var event = new Event("Simulation " + template.name(), "Fixture", null, LocalDateTime.now().plusDays(2),
                template.name(), "Sydney", "USYD", "Simulation", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        Long eventId = events.saveAndFlush(event).getId();
        maps.saveMap(eventId, owner.getEmail(), confirmedIdentifiers(template));
        assertEquals(usableCodes.size(), events.findById(eventId).orElseThrow().getCapacity());
        eventService.changeEventStatus(eventId, "published", owner.getEmail());

        // Mixed group sizes; accessibility is requested only where the template has accessible seats.
        int accessibleRequests = Math.min(accessibleCodes.size(), 2);
        var fixture = new ArrayList<Booking>();
        int group = 0, remaining = 0;
        for (int i = 0; i < count; i++) {
            if (remaining == 0) {
                remaining = Math.min(GROUP_SIZES[group % GROUP_SIZES.length], count - i);
                group++;
            }
            var attendee = users.save(new User("sim-" + suffix + "-" + i + "@uni.sydney.edu.au", "unused",
                    UserRole.STUDENT));
            boolean accessibility = i < accessibleRequests;
            fixture.add(new Booking("SIM-" + eventId + "-" + i, event, attendee, "G" + group,
                    accessibility, UUID.randomUUID().toString()));
            remaining--;
        }
        bookings.saveAllAndFlush(fixture);
        attempts.saveAllAndFlush(fixture.stream().map(b -> new CheckoutAttempt(UUID.randomUUID().toString(),
                "MOCK-" + UUID.randomUUID(), b.getUser().getId(), eventId, "simulation",
                MockCheckoutRequest.Outcome.SUCCESS, b.getId())).toList());
        eventService.changeEventStatus(eventId, "closed", owner.getEmail());
        entityManager.flush(); entityManager.clear();

        long started = System.nanoTime();
        var run = seating.allocate(eventId, owner.getEmail(), strategy);
        entityManager.flush();
        long allocationMs = (System.nanoTime() - started) / 1_000_000;
        entityManager.clear();

        var persisted = bookings.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<String, String> seatByReference = persisted.stream()
                .collect(Collectors.toMap(Booking::getBookingRef, Booking::getSeatCode));
        assertEquals(count, persisted.size());
        assertTrue(persisted.stream().allMatch(b -> b.getStatus() == BookingStatus.ALLOCATED));
        assertEquals(count, seatByReference.values().stream().filter(Objects::nonNull).distinct().count());
        assertTrue(usableCodes.containsAll(seatByReference.values()), "Only usable template seats may be assigned");
        assertTrue(persisted.stream().filter(Booking::isAccessibilityRequired)
                .allMatch(b -> accessibleCodes.contains(b.getSeatCode())));
        if (occupancy.equals("FULL")) assertEquals(usableCodes, new HashSet<>(seatByReference.values()));
        var metrics = seating.metrics(eventId, owner.getEmail());
        assertEquals(0, metrics.duplicateAllocationCount());

        long outputStarted = System.nanoTime();
        String csv = seating.export(eventId, owner.getEmail());
        long csvMs = (System.nanoTime() - outputStarted) / 1_000_000;
        outputStarted = System.nanoTime();
        String plan = seating.printablePlan(eventId, owner.getEmail());
        long printMs = (System.nanoTime() - outputStarted) / 1_000_000;

        var lines = csv.split("\r\n");
        assertEquals("booking_reference,group_reference,seat,split_reason", lines[0]);
        assertEquals(count + 1, lines.length, "CSV needs one row per confirmed booking");
        Map<String, String> csvSeatByReference = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            var cells = Arrays.stream(lines[i].split(",", -1)).map(cell -> cell.replace("\"", "")).toList();
            assertEquals(4, cells.size());
            assertNull(csvSeatByReference.put(cells.get(0), cells.get(2)), "Booking repeated in CSV");
        }
        assertEquals(seatByReference, csvSeatByReference, "CSV seats must match persisted assignments");
        assertFalse(csv.contains("@"), "CSV must not expose attendee emails");
        assertFalse(plan.contains("uni.sydney.edu.au"), "Printable plan must not expose attendee emails");
        assertTrue(seatByReference.keySet().stream().allMatch(plan::contains),
                "Printable plan must list every booking reference");

        String fileName = templateId + "-" + occupancy.toLowerCase(Locale.ROOT) + "-" + count + "-"
                + strategy.name().toLowerCase(Locale.ROOT).replace('_', '-');
        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve(fileName + ".csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(OUTPUT_DIR.resolve(fileName + ".html"), plan, StandardCharsets.UTF_8);

        System.out.printf(Locale.ROOT,
                "CAMPUS_SIMULATION template=%s occupancy=%s strategy=%s attendees=%d usableSeats=%d groups=%d "
                        + "allocationMs=%d csvMs=%d printMs=%d splitGroups=%d groupsTogetherPct=%.2f "
                        + "contiguityPct=%.2f isolated=%d%n",
                templateId, occupancy, strategy, count, usableCodes.size(), group, allocationMs, csvMs, printMs,
                run.getSplitGroupCount(), metrics.percentageGroupsTogether(),
                metrics.averageGroupContiguityPercentage(), metrics.isolatedSeatCount());
        if (count <= PROPOSAL_ATTENDEE_LIMIT) {
            assertTrue(allocationMs < 10_000, "Allocation for up to 300 attendees must finish within ten seconds");
        }
    }

    private static Set<String> usableSeatCodes(SeatMapRequest map) {
        return map.elements().stream().filter(element -> !element.privateElement())
                .flatMap(element -> element.seats().stream())
                .filter(seat -> !seat.blocked() && !seat.privateSeat())
                .map(SeatMapRequest.SeatRequest::code).collect(Collectors.toSet());
    }

    private static SeatMapRequest confirmedIdentifiers(CampusVenueTemplateResponse template) {
        var map = template.map();
        return new SeatMapRequest(map.name(), map.attendeesChooseSeats(), map.autoMatchTicketCapacities(), true,
                map.ticketTypeCapacities(), map.elements());
    }
}
