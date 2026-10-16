package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.VenueSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenueSeatRepository extends JpaRepository<VenueSeat, Long> {
    List<VenueSeat> findByEventIdOrderByElementIdAscRowIndexAscSeatNumberAsc(Long eventId);
    Optional<VenueSeat> findByEventIdAndCodeIgnoreCase(Long eventId, String code);
    void deleteByEventId(Long eventId);
}
