package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.ManagedSeatMap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedSeatMapRepository extends JpaRepository<ManagedSeatMap, Long> {
}
