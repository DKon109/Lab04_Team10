package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByDateTimeAsc();

    List<Event> findByOwner_IdOrderByDateTimeAsc(Long ownerId);
}
