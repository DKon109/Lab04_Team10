package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seating_map_elements", uniqueConstraints =
        @UniqueConstraint(name = "uq_seating_element_key", columnNames = {"event_id", "element_key"}))
@Getter
@NoArgsConstructor
public class SeatingMapElement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "element_key", nullable = false, length = 60)
    private String elementKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SeatingElementType type;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private double positionX;

    @Column(nullable = false)
    private double positionY;

    @Column(nullable = false)
    private int rotationDegrees;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean privateElement;

    public SeatingMapElement(Long eventId, String elementKey, SeatingElementType type,
            String label, String note, double positionX, double positionY,
            int rotationDegrees, int displayOrder, boolean privateElement) {
        this.eventId = eventId;
        this.elementKey = elementKey;
        this.type = type;
        this.label = label;
        this.note = note;
        this.positionX = positionX;
        this.positionY = positionY;
        this.rotationDegrees = rotationDegrees;
        this.displayOrder = displayOrder;
        this.privateElement = privateElement;
    }
}
