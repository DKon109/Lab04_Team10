package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "allocation_runs") @Getter @NoArgsConstructor
public class AllocationRun {
    @Id private Long eventId;
    private int layoutVersion;
    private int bookingCount;
    private int splitGroupCount;
    private long elapsedMillis;
    private LocalDateTime completedAt;
    public AllocationRun(Long eventId, int layoutVersion, int bookingCount, int splitGroupCount, long elapsedMillis) {
        this.eventId = eventId; this.layoutVersion = layoutVersion; this.bookingCount = bookingCount;
        this.splitGroupCount = splitGroupCount; this.elapsedMillis = elapsedMillis;
        this.completedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
