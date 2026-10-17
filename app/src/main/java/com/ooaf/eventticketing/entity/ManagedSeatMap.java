package com.ooaf.eventticketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "managed_seat_maps")
@Getter
@NoArgsConstructor
public class ManagedSeatMap {
    @Id
    private Long eventId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean attendeesChooseSeats;

    @Column(nullable = false)
    private int mapVersion = 1;

    public ManagedSeatMap(Long eventId, String name, boolean attendeesChooseSeats, int mapVersion) {
        this.eventId = eventId;
        this.name = name;
        this.attendeesChooseSeats = attendeesChooseSeats;
        this.mapVersion = mapVersion;
    }
}
