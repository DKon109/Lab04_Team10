package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import com.ooaf.eventticketing.repository.AllocationRunRepository;
import com.ooaf.eventticketing.repository.BookingRepository;
import com.ooaf.eventticketing.repository.CheckoutAttemptRepository;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.service.EventService;
import com.ooaf.eventticketing.service.MockCheckoutService;
import com.ooaf.eventticketing.service.SeatingService;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import com.ooaf.eventticketing.dto.MockCheckoutRequest;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")

class GeneralAdmissionPersistenceTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EventService eventService;

    @Autowired
    private MockCheckoutService checkout;

    @Autowired
    private SeatingService seating;

    @Autowired
    private EventRepository events;

    @Autowired
    private UserRepository users;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private AllocationRunRepository runs;

    @Autowired
    private CheckoutAttemptRepository attempts;

    private record TestData (
            Long ownerId,
            Long studentId,
            Long eventId,
            Long bookingId,
            String ownerEmail,
            String studentEmail,
            LocalDateTime allocationCompletedAt
            ) {

    }

    private TransactionTemplate newTransaction() {
        var transaction  = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    @Test
    void generalAdmissionTicketSurvivesCommitAndAllocationRetry() {
        TestData data = newTransaction().execute(
                status -> createAllocatedBooking()
        );

        try {
            newTransaction().executeWithoutResult(status -> {
                var ticket = seating.ticket(
                        data.bookingId(), data.studentEmail()
                );

                assertEquals(data.bookingId(), ticket.booking().id());
                assertEquals(
                        BookingStatus.ALLOCATED,
                        ticket.booking().status()
                );
                assertEquals("MOCK_SUCCESS", ticket.paymentStatus());
                assertTrue(ticket.generalAdmission());
                assertNull(ticket.seatCode());
                assertNull(ticket.layout());

                var savedRun = runs.findById(data.eventId())
                        .orElseThrow();

                assertEquals(1, savedRun.getBookingCount());
                assertEquals(
                        data.allocationCompletedAt(),
                        savedRun.getCompletedAt()
                );

                var retriedRun = seating.allocate(
                        data.eventId(), data.ownerEmail()
                );

                assertEquals(
                        data.allocationCompletedAt(),
                        retriedRun.getCompletedAt()
                );
                assertEquals(1, retriedRun.getBookingCount());
                assertEquals(
                        1,
                        bookings.findByEventIdOrderByCreatedAtAsc(
                                data.eventId()
                        ).size()
                );
            });
        } finally {
            deleteTestData(data);
        }
    }

    private TestData createAllocatedBooking() {
        String suffix = UUID.randomUUID().toString();

        var owner = users.saveAndFlush(new User(
                "owner-" + suffix + "@test.local",
                "unused",
                UserRole.COMMITTEE_MEMBER,
                "Demo society"
        ));

        var student = users.saveAndFlush(new User(
                "student-" + suffix + "@test.local",
                "unused",
                UserRole.STUDENT
        ));

        var event = new Event(
                "General admission persistence test",
                "Test event",
                null,
                LocalDateTime.now().plusDays(2),
                "Test venue",
                "Sydney",
                "USYD",
                "Demo society",
                OrganiserType.SOCIETY,
                "Arts",
                List.of()
        );
        event.setOwner(owner);
        event.setSeatIdentifiersConfirmed(false);
        Long eventId = events.saveAndFlush(event).getId();

        eventService.changeEventStatus(
                eventId, "published", owner.getEmail()
        );

        var booking = checkout.checkout(
                eventId,
                student.getEmail(),
                new MockCheckoutRequest(
                        false,
                        null,
                        false,
                        UUID.randomUUID().toString(),
                        MockCheckoutRequest.Outcome.SUCCESS
                )
        ).booking();

        eventService.changeEventStatus(
                eventId, "closed", owner.getEmail()
        );

        var run = seating.allocate(eventId, owner.getEmail());

        return new TestData(
                owner.getId(),
                student.getId(),
                eventId,
                booking.id(),
                owner.getEmail(),
                student.getEmail(),
                run.getCompletedAt()
        );
    }

    private void deleteTestData(TestData data) {
        newTransaction().executeWithoutResult(status -> {
            attempts.deleteAll(
                    attempts.findByEventIdAndOutcome(
                            data.eventId(),
                            MockCheckoutRequest.Outcome.SUCCESS
                    )
            );
            attempts.flush();

            runs.deleteById(data.eventId());
            runs.flush();

            bookings.deleteById(data.bookingId());
            bookings.flush();

            events.deleteById(data.eventId());
            events.flush();

            users.deleteAllById(
                    List.of(data.ownerId(), data.studentId())
            );
            users.flush();
        });
    }
}
