package com.ooaf.eventticketing.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.service.EventService;
import com.ooaf.eventticketing.dto.MockCheckoutRequest;
import com.ooaf.eventticketing.repository.BookingRepository;
import com.ooaf.eventticketing.repository.CheckoutAttemptRepository;
import com.ooaf.eventticketing.service.MockCheckoutService;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationClosureConcurrencyTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EventRepository events;

    @Autowired
    private UserRepository users;

    @Autowired
    private EventService eventService;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private CheckoutAttemptRepository attempts;

    @Autowired
    private MockCheckoutService checkout;

    private record TestData(
            Long ownerId,
            Long studentId,
            Long eventId,
            String ownerEmail,
            String studentEmail
    ) {

    }

    private TransactionTemplate newTransaction() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private TestData createTestData() {
        return newTransaction().execute(status -> {
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
                    "Registration closure concurrency test",
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
            event.setCapacity(2);
            event.setSeatIdentifiersConfirmed(false);

            Long eventId = events.saveAndFlush(event).getId();
            eventService.changeEventStatus(
                    eventId, "published", owner.getEmail()
            );

            return new TestData(
                    owner.getId(),
                    student.getId(),
                    eventId,
                    owner.getEmail(),
                    student.getEmail()
            );
        });
    }

    private void deleteTestData(TestData data) {
        newTransaction().executeWithoutResult(status -> {
            for (var outcome : MockCheckoutRequest.Outcome.values()) {
                attempts.deleteAll(
                        attempts.findByEventIdAndOutcome(
                                data.eventId(), outcome
                        )
                );
            }
            attempts.flush();

            bookings.deleteAll(
                    bookings.findByEventIdOrderByCreatedAtAsc(data.eventId()
                    )
            );
            bookings.flush();

            events.deleteById(data.eventId());
            events.flush();

            users.deleteAllById(
                    List.of(data.ownerId(), data.studentId())
            );
            users.flush();
        });
    }

    private void awaitSignal(CountDownLatch signal) {
        try {
            if (!signal.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "Timed out waiting for the other transaction"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while waiting for the other transaction", exception
            );
        }
    }

    @Test
    void checkoutIsRejectedWhenClosureCommitsFirst() throws Exception {
        var data  = createTestData();
        var pool = Executors.newFixedThreadPool(2);
        var closureReady = new CountDownLatch(1);
        var checkoutStarted = new CountDownLatch(1);
        var allowClosureCommit = new CountDownLatch(1);
        String paymentKey = UUID.randomUUID().toString();

        try {
            var closing = pool.submit(() ->
                    newTransaction().executeWithoutResult(status -> {
                        eventService.changeEventStatus(
                                data.eventId(), "closed",
                                data.ownerEmail()
                        );

                        // Keep the event lock until the test allows the commit.
                        closureReady.countDown();
                        awaitSignal(allowClosureCommit);
                    })
            );

            awaitSignal(closureReady);

            var paying = pool.submit(() -> {
                    checkoutStarted.countDown();

            return assertThrows(
                    ResponseStatusException.class,
                    () -> checkout.checkout(
                            data.eventId(),
                            data.studentEmail(),
                            new MockCheckoutRequest(
                                    false,
                                    null,
                                    false,
                                    paymentKey,

                                    MockCheckoutRequest.Outcome.SUCCESS
                            )
                    )
            );
        });

            awaitSignal(checkoutStarted);

            // Checkout must not finish while closure holds the event lock.

        assertThrows(
                java.util.concurrent.TimeoutException.class,
                () -> paying.get(300, TimeUnit.MILLISECONDS)
        );

        allowClosureCommit.countDown();
        closing.get(15, TimeUnit.SECONDS);

        var error = paying.get(15, TimeUnit.SECONDS);
        assertEquals(409, error.getStatusCode().value());
        assertEquals("Registration is closed for this event", error.getReason());

        newTransaction().executeWithoutResult(status -> {
            var savedEvent =
                    events.findById(data.eventId()).orElseThrow();

            assertEquals(EventStatus.CLOSED, savedEvent.getStatus());
            assertEquals(EventRegistrationStatus.CLOSED, savedEvent.getRegistrationStatus());
            assertTrue(
                    bookings.findByEventIdOrderByCreatedAtAsc(data.eventId()).isEmpty()
            );

            assertTrue(attempts.findByIdempotencyKey(paymentKey).isEmpty());
        });
    } finally {
            allowClosureCommit.countDown();
            pool.shutdown();

            if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                assertTrue(
                        pool.awaitTermination(10, TimeUnit.SECONDS),
                        "Worker threads must stop before deleting test data"
                );
            }

            deleteTestData(data);
        }
    }

    @Test
    void bookingIsPreservedWhenCheckoutCommitsFirst() throws Exception {
        var data  = createTestData();
        var pool = Executors.newFixedThreadPool(2);
        var checkoutReady = new CountDownLatch(1);
        var closureStarted = new CountDownLatch(1);
        var allowCheckoutCommit = new CountDownLatch(1);
        String paymentKey = UUID.randomUUID().toString();

        try {
            var paying = pool.submit(() ->
                    newTransaction().execute(status -> {
                        var response = checkout.checkout(
                                data.eventId(),
                                data.studentEmail(),
                                new MockCheckoutRequest(
                                        false,
                                        null,
                                        false,
                                        paymentKey,

                                        MockCheckoutRequest.Outcome.SUCCESS
                                )
                        );

                        // Keep the event lock until the test allows the commit.
                        checkoutReady.countDown();
                        awaitSignal(allowCheckoutCommit);
                        return response;
                    })
            );

            awaitSignal(checkoutReady);

            var closing = pool.submit(() -> {
                    closureStarted.countDown();
            return eventService.changeEventStatus(
                    data.eventId(), "closed", data.ownerEmail()
            );
        });
            awaitSignal(closureStarted);

            // Closure must not finish while checkout holds the event lock.
        assertThrows(
                java.util.concurrent.TimeoutException.class,
                () -> closing.get(300, TimeUnit.MILLISECONDS)
        );

        allowCheckoutCommit.countDown();

        var payment = paying.get(15, TimeUnit.SECONDS);
        closing.get(15, TimeUnit.SECONDS);

        assertEquals(
                MockCheckoutRequest.Outcome.SUCCESS,
                payment.outcome()
        );
        assertNotNull(payment.booking());

        newTransaction().executeWithoutResult(status -> {
            var savedEvent =
                    events.findById(data.eventId()).orElseThrow();
            assertEquals(EventStatus.CLOSED, savedEvent.getStatus());
            assertEquals(EventRegistrationStatus.CLOSED, savedEvent.getRegistrationStatus()
            );

            var savedBookings =
                    bookings.findByEventIdOrderByCreatedAtAsc(data.eventId());

            assertEquals(1, savedBookings.size());
            assertEquals(
                    payment.booking().id(),
                    savedBookings.get(0).getId()
            );
            assertEquals(
                    BookingStatus.PENDING_ALLOCATION,
                    savedBookings.get(0).getStatus()
            );

            var attempt =
                    attempts.findByIdempotencyKey(paymentKey).orElseThrow();
            assertEquals(MockCheckoutRequest.Outcome.SUCCESS, attempt.getOutcome()
            );
            assertEquals(payment.booking().id(),
                    attempt.getBookingId()
            );
        });
    } finally {
            allowCheckoutCommit.countDown();
            pool.shutdown();

            if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                assertTrue(
                        pool.awaitTermination(10, TimeUnit.SECONDS),
                        "Worker threads must stop before deleting test data"
                );
            }

            deleteTestData(data);
        }
    }
}
