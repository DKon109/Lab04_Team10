package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat_map_ticket_types", uniqueConstraints =
        @UniqueConstraint(name = "uq_seat_map_ticket_type", columnNames = {"event_id", "code"}))
@Getter
@NoArgsConstructor
public class SeatMapTicketType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private int capacity;

    public SeatMapTicketType(Long eventId, String code, int capacity) {
        this.eventId = eventId;
        this.code = code;
        this.capacity = capacity;
    }
}
