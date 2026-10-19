package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.allocation.AllocationStrategy;
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
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic fixtures; setup is excluded, allocation and flushed persistence are timed.
 * Correctness is checked again after clearing the persistence context.
 */
@SpringBootTest @ActiveProfiles("test") @Transactional
class AllocationBenchmarkTest {
    @Autowired SeatingService seating;
    @Autowired SeatingMapManagementService maps;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired AllocationRunRepository runs;
    @Autowired EntityManager entityManager;

    @ParameterizedTest(name = "{0}: {1} attendees, group size {2}, {3}")
    @CsvSource({
            "FRONT,300,2,DEFAULT",
            "FRONT,300,2,PREFER_SAME_ROW",
            "FRONT,300,300,DEFAULT",
            "FRONT,300,300,PREFER_SAME_ROW",
            "FAN,280,7,DEFAULT",
            "FAN,280,7,PREFER_SAME_ROW",
            "CENTRAL,300,5,DEFAULT",
            "CENTRAL,300,5,PREFER_SAME_ROW",
            "CAMPUS_DEMO,48,3,DEFAULT",
            "CAMPUS_DEMO,48,3,PREFER_SAME_ROW",
            "MANAGED,300,0,DEFAULT",
            "MANAGED,300,0,PREFER_SAME_ROW",
            "TABLES,300,2,DEFAULT",
            "TABLES,300,2,PREFER_SAME_ROW",
            "TABLES,300,7,DEFAULT",
            "TABLES,300,7,PREFER_SAME_ROW",
            "TABLES,300,20,DEFAULT",
            "TABLES,300,20,PREFER_SAME_ROW",
            "TABLES,300,300,DEFAULT",
            "TABLES,300,300,PREFER_SAME_ROW"
    })
    void persistsVariedLayoutsWithinTenSeconds(String family, int count, int groupSize, AllocationStrategy strategy) {
        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("bench-owner-" + suffix, "unused", UserRole.COMMITTEE_MEMBER, "Benchmark"));
        var event = new Event("Synthetic benchmark " + family, "Fixture", null, LocalDateTime.now().plusDays(2),
                "Synthetic venue", "Sydney", "USYD", "Benchmark", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner); event = events.saveAndFlush(event);
        Long eventId = event.getId();
        boolean managed = family.equals("MANAGED");
        Set<String> blocked = new HashSet<>();
        if (family.equals("TABLES")) {
            var tables = new ArrayList<SeatMapRequest.ElementRequest>();
            for (int table = 1; table <= 20; table++) {
                var seats = new ArrayList<SeatMapRequest.SeatRequest>();
                for (int side = 0; side < 2; side++) for (int number = 1; number <= 8; number++) {
                    String label = side == 0 ? "N" : "S";
                    String code = "T" + table + "-" + label + number;
                    seats.add(new SeatMapRequest.SeatRequest(code, code, null, "Main", label, side,
                            number, number * 10, side * 20, number == 1, false, false, List.of("STANDARD")));
                }
                tables.add(new SeatMapRequest.ElementRequest("T" + table, SeatingElementType.RECTANGULAR_TABLE,
                        "Table " + table, null, 0, table * 50, 0, table, false, seats));
            }
            maps.saveMap(eventId, owner.getEmail(), new SeatMapRequest("Synthetic tables", false, true, true,
                    Map.of("STANDARD", 0), tables));
        } else if (managed) {
            var rows = new ArrayList<SeatMapRequest.ElementRequest>();
            for (int row = 0; row < 16; row++) {
                var seats = new ArrayList<SeatMapRequest.SeatRequest>();
                String label = String.valueOf((char) ('A' + row));
                for (int number = 1; number <= 20; number++) {
                    String code = label + number;
                    if (number == 20) blocked.add(code);
                    seats.add(new SeatMapRequest.SeatRequest(code, code, null, number <= 10 ? "Left" : "Right",
                            label, row, number, number * 10, row * 20, number == 1,
                            number == 20, false, List.of("STANDARD")));
                }
                rows.add(new SeatMapRequest.ElementRequest("row-" + row, SeatingElementType.ROW, label, null,
                        0, row * 20, 0, row, false, seats));
            }
            maps.saveMap(eventId, owner.getEmail(), new SeatMapRequest("Synthetic benchmark", false, true, true,
                    Map.of("STANDARD", 0), rows));
        } else {
            boolean campus = family.equals("CAMPUS_DEMO");
            if (family.equals("FAN")) blocked.addAll(List.of("C5", "H8", "M15"));
            seating.configure(eventId, owner.getEmail(), new SeatingLayoutRequest(
                    campus ? SeatingLayoutRequest.Source.CAMPUS_DEMO : SeatingLayoutRequest.Source.EXTERNAL,
                    campus ? SeatingLayoutRequest.Template.FRONT : SeatingLayoutRequest.Template.valueOf(family),
                    campus ? "woolley-demo" : null, 15, 20, 5,
                    List.copyOf(blocked), List.of("A1", "B1", "C1", "D1"), true));
        }
        eventService.changeEventStatus(eventId, "published", owner.getEmail());
        var fixture = new ArrayList<Booking>();
        int group = 0, remaining = 0;
        for (int i = 0; i < count; i++) {
            if (remaining == 0) {
                remaining = groupSize == 0 ? new int[]{1, 2, 3, 5, 7}[group % 5] : groupSize;
                group++;
            }
            var attendee = users.save(new User("bench-" + suffix + "-" + i, "unused", UserRole.STUDENT));
            var booking = new Booking("BENCH-" + eventId + "-" + i, event, attendee, "G" + group,
                    i % 100 == 0, UUID.randomUUID().toString());
            if (managed && i == 0) booking.assignSeat("A1", null);
            fixture.add(booking); remaining--;
        }
        bookings.saveAllAndFlush(fixture);
        attempts.saveAllAndFlush(fixture.stream().map(b -> new CheckoutAttempt(UUID.randomUUID().toString(),
                "MOCK-" + UUID.randomUUID(), b.getUser().getId(), eventId, "benchmark", MockCheckoutRequest.Outcome.SUCCESS, b.getId())).toList());
        eventService.changeEventStatus(eventId, "closed", owner.getEmail());
        entityManager.flush(); entityManager.clear();
        long started = System.nanoTime();
        seating.allocate(eventId, owner.getEmail(), strategy);
        entityManager.flush();
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        entityManager.clear();
        var persisted = bookings.findByEventIdOrderByCreatedAtAsc(eventId);
        assertEquals(count, persisted.size());
        assertEquals(count, persisted.stream().map(Booking::getSeatCode).filter(Objects::nonNull).distinct().count());
        assertTrue(persisted.stream().allMatch(b -> b.getStatus() == BookingStatus.ALLOCATED && !blocked.contains(b.getSeatCode())));
        assertTrue(persisted.stream().filter(Booking::isAccessibilityRequired).allMatch(b -> b.getSeatCode().endsWith("1")));
        var metrics = seating.metrics(eventId, owner.getEmail());
        assertEquals(0, metrics.duplicateAllocationCount());
        var run = runs.findById(eventId).orElseThrow();
        assertEquals(strategy, run.getStrategy());
        assertEquals(metrics.percentageGroupsTogether(), run.getPercentageGroupsTogether());
        assertEquals(metrics.averageSplitDistance(), run.getAverageSplitDistance());
        assertEquals(metrics.averageGroupContiguityPercentage(), run.getAverageGroupContiguityPercentage());
        assertEquals(metrics.isolatedSeatCount(), run.getIsolatedSeatCount());
        assertEquals(run.getSplitGroupCount(), persisted.stream().filter(b -> b.getSplitReason() != null)
                .map(Booking::getGroupCode).distinct().count());
        if (managed) assertEquals("A1", persisted.get(0).getSeatCode());
        assertEquals(run.getCompletedAt(), seating.allocate(eventId, owner.getEmail()).getCompletedAt());
        System.out.printf(Locale.ROOT,
                "PERSISTED_BENCHMARK strategy=%s family=%s attendees=%d groupSize=%s elapsedMs=%d splitGroups=%d contiguityPct=%.2f weightedSplitDistance=%.2f isolated=%d java=%s os=%s arch=%s cpus=%d database=MySQL%n",
                strategy, family, count, groupSize == 0 ? "mixed1/2/3/5/7+fixed" : groupSize, elapsed,
                run.getSplitGroupCount(), metrics.averageGroupContiguityPercentage(), metrics.averageSplitDistance(),
                metrics.isolatedSeatCount(), System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
        assertTrue(elapsed < 10_000, "Validated inputs through persisted results must finish within ten seconds");
    }
}
