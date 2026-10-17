package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.SeatingMapElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatingMapElementRepository extends JpaRepository<SeatingMapElement, Long> {
    List<SeatingMapElement> findByEventIdOrderByDisplayOrderAscIdAsc(Long eventId);
    void deleteByEventId(Long eventId);
}
