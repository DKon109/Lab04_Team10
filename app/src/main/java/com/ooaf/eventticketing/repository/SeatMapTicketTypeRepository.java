package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.SeatMapTicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatMapTicketTypeRepository extends JpaRepository<SeatMapTicketType, Long> {
    List<SeatMapTicketType> findByEventIdOrderByCodeAsc(Long eventId);
    void deleteByEventId(Long eventId);
}
