package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "venue_seats", uniqueConstraints =
        @UniqueConstraint(name = "uq_venue_seat_code", columnNames = {"event_id", "code"}))
@Getter
@NoArgsConstructor
public class VenueSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "element_id", nullable = false)
    private Long elementId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(length = 300)
    private String note;

    @Column(nullable = false, length = 80)
    private String sectionName;

    @Column(nullable = false, length = 32)
    private String rowLabel;

    @Column(nullable = false)
    private int rowIndex;

    @Column(nullable = false)
    private int seatNumber;

    @Column(nullable = false)
    private double positionX;

    @Column(nullable = false)
    private double positionY;

    @Column(name = "accessible_seat", nullable = false)
    private boolean accessible;

    @Column(nullable = false)
    private boolean blocked;

    @Column(nullable = false)
    private boolean privateSeat;

    @Column(nullable = false, length = 1000)
    private String ticketTypes = "";

    public VenueSeat(Long eventId, Long elementId, String code, String label, String note,
            String sectionName, String rowLabel, int rowIndex, int seatNumber,
            double positionX, double positionY, boolean accessible, boolean blocked,
            boolean privateSeat, String ticketTypes) {
        this.eventId = eventId;
        this.elementId = elementId;
        this.code = code;
        this.label = label;
        this.note = note;
        this.sectionName = sectionName;
        this.rowLabel = rowLabel;
        this.rowIndex = rowIndex;
        this.seatNumber = seatNumber;
        this.positionX = positionX;
        this.positionY = positionY;
        this.accessible = accessible;
        this.blocked = blocked;
        this.privateSeat = privateSeat;
        this.ticketTypes = ticketTypes;
    }
}
