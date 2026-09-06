package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A user's like on an event. One row per (user, event) pair; liking is
 * idempotent at the service layer and backed by a unique constraint here.
 */
@Entity
@Table(name = "event_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"}))
public class EventLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private Instant createdAt;

    protected EventLike() {
    }

    public EventLike(User user, Event event) {
        this.user = user;
        this.event = event;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Event getEvent() {
        return event;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
