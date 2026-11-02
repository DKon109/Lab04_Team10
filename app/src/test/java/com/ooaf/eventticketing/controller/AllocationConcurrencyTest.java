package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Simultaneous allocation requests must produce one run and one seat per booking. */
@SpringBootTest @ActiveProfiles("test")
class AllocationConcurrencyTest {
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SeatingService seating;
    @Autowired MockCheckoutService checkout;
    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired AllocationRunRepository runs;
    @Autowired SeatingLayoutRepository layouts;

    record TestData(Long eventId, String ownerEmail, List<Long> userIds) {}

    TransactionTemplate newTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    TestData createTestData() {
        String suffix = UUID.randomUUID().toString();
        var owner = users.saveAndFlush(new User("owner-" + suffix + "@test.local", "unused", UserRole.COMMITTEE_MEMBER, "Demo society"));
        var userIds = new ArrayList<>(List.of(owner.getId()));
        var event = new Event("Allocation concurrency", "Demo", null, LocalDateTime.now().plusDays(2), "Demo room",
                "Sydney", "USYD", "Demo society", OrganiserType.SOCIETY, "Arts", List.of());
        event.setOwner(owner);
        Long eventId = events.saveAndFlush(event).getId();
        seating.configure(eventId, owner.getEmail(), new SeatingLayoutRequest(SeatingLayoutRequest.Source.EXTERNAL,
                SeatingLayoutRequest.Template.FRONT, null, 2, 4, 2, List.of(), List.of(), true));
        eventService.changeEventStatus(eventId, "published", owner.getEmail());
        for (int i = 0; i < 4; i++) {
            var student = users.saveAndFlush(new User("student-" + i + "-" + suffix + "@test.local", "unused", UserRole.STUDENT));
            userIds.add(student.getId());
            checkout.checkout(eventId, student.getEmail(), new MockCheckoutRequest(false, null, false,
                    UUID.randomUUID().toString(), MockCheckoutRequest.Outcome.SUCCESS));
        }
        eventService.changeEventStatus(eventId, "closed", owner.getEmail());
        return new TestData(eventId, owner.getEmail(), userIds);
    }

    void deleteTestData(TestData data) {
        newTransaction().executeWithoutResult(status -> {
            runs.findById(data.eventId()).ifPresent(runs::delete);
            layouts.findById(data.eventId()).ifPresent(layouts::delete);
            for (var outcome : MockCheckoutRequest.Outcome.values()) {
                attempts.deleteAll(attempts.findByEventIdAndOutcome(data.eventId(), outcome));
            }
            bookings.deleteAll(bookings.findByEventIdOrderByCreatedAtAsc(data.eventId()));
            events.deleteById(data.eventId());
            users.deleteAllById(data.userIds());
        });
    }

    @Test void simultaneousAllocationRequestsShareOneRun() throws Exception {
        var data = createTestData();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<AllocationRun> request = () -> {
                start.await(10, TimeUnit.SECONDS);
                return seating.allocate(data.eventId(), data.ownerEmail());
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            start.countDown();

            // Both requests succeed; the event row lock makes the second reuse the first run.
            var firstRun = first.get(30, TimeUnit.SECONDS);
            var secondRun = second.get(30, TimeUnit.SECONDS);
            assertEquals(4, firstRun.getBookingCount());
            assertEquals(4, secondRun.getBookingCount());
            assertEquals(4, runs.findById(data.eventId()).orElseThrow().getBookingCount());

            var stored = bookings.findByEventIdOrderByCreatedAtAsc(data.eventId());
            assertEquals(4, stored.size());
            var seats = new HashSet<String>();
            for (var booking : stored) {
                assertEquals(BookingStatus.ALLOCATED, booking.getStatus());
                assertNotNull(booking.getSeatCode());
                assertTrue(seats.add(booking.getSeatCode()), "Duplicate seat " + booking.getSeatCode());
            }
        } finally {
            pool.shutdownNow();
            deleteTestData(data);
        }
    }
}
