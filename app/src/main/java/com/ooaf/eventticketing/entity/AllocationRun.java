package com.ooaf.eventticketing.entity;

import com.ooaf.eventticketing.allocation.AllocationStrategy;
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
    // Null means a historical run did not record its strategy.
    @Enumerated(EnumType.STRING) @Column(length = 32)
    private AllocationStrategy strategy;
    public void updateSplitGroupCount(int splitGroupCount) {
        this.splitGroupCount = splitGroupCount;
    }

    public AllocationRun(Long eventId, int layoutVersion, int bookingCount, int splitGroupCount, long elapsedMillis) {
        this(eventId, layoutVersion, bookingCount, splitGroupCount, elapsedMillis, AllocationStrategy.DEFAULT);
    }

    public AllocationRun(Long eventId, int layoutVersion, int bookingCount, int splitGroupCount,
            long elapsedMillis, AllocationStrategy strategy) {
        this.strategy = strategy;
        this.eventId = eventId; this.layoutVersion = layoutVersion; this.bookingCount = bookingCount;
        this.splitGroupCount = splitGroupCount; this.elapsedMillis = elapsedMillis;
        this.completedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
