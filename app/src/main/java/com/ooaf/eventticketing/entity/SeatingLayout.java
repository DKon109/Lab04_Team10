package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "seating_layouts") @Getter @Setter @NoArgsConstructor
public class SeatingLayout {
    @Id private Long eventId;
    @Column(nullable = false) private String source;
    @Column(nullable = false) private String template;
    private String roomId;
    @Column(nullable = false) private int rowCount;
    @Column(nullable = false) private int seatsPerRow;
    @Column(nullable = false) private int aisleAfter;
    @Column(length = 2000, nullable = false) private String blockedCodes = "";
    @Column(length = 2000, nullable = false) private String accessibleCodes = "";
    @Column(nullable = false) private int layoutVersion = 1;
}
