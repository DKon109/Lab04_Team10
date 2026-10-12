package com.ooaf.eventticketing.entity;

import com.ooaf.eventticketing.dto.MockCheckoutRequest.Outcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** Audit of simulated checkout only. No card data or real payment provider. */
@Entity
@Table(name = "checkout_attempts")
@Getter
@NoArgsConstructor
public class CheckoutAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String idempotencyKey;
    @Column(nullable = false, unique = true, length = 48) private String paymentRef;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long eventId;
    @Column(nullable = false, length = 160) private String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Outcome outcome;
    private Long bookingId;
    @Column(nullable = false) private LocalDateTime createdAt;

    public CheckoutAttempt(String key, String ref, Long userId, Long eventId, String fingerprint, Outcome outcome, Long bookingId) {
        this.idempotencyKey = key;
        this.paymentRef = ref;
        this.userId = userId;
        this.eventId = eventId;
        this.fingerprint = fingerprint;
        this.outcome = outcome;
        this.bookingId = bookingId;
        this.createdAt = LocalDateTime.now();
    }
}
