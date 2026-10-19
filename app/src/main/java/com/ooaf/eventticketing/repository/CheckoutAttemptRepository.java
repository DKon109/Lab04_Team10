package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.CheckoutAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt, Long> {
    Optional<CheckoutAttempt> findByIdempotencyKey(String key);
    java.util.List<CheckoutAttempt> findByEventIdAndOutcome(Long eventId, com.ooaf.eventticketing.dto.MockCheckoutRequest.Outcome outcome);
    boolean existsByBookingIdAndOutcome(Long bookingId, com.ooaf.eventticketing.dto.MockCheckoutRequest.Outcome outcome);
}
