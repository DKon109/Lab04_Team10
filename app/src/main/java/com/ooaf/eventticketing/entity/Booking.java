package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="bookings")
@NoArgsConstructor
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String bookingRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 32)
    private String groupCode;

    @Column(nullable = false)
    private boolean accessibilityRequired;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Booking(String bookingRef, Event event, User user, String groupCode, boolean accessibilityRequired, String idempotencyKey){
        this.bookingRef = bookingRef;
        this.event = event;
        this.user = user;
        this.groupCode = groupCode;
        this.accessibilityRequired = accessibilityRequired;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.PENDING_ALLOCATION;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public Event getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public boolean isAccessibilityRequired() {
        return accessibilityRequired;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
