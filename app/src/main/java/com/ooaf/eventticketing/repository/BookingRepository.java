package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.Booking;
import com.ooaf.eventticketing.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsByEventIdAndUserIdAndStatusNot(Long eventId, Long userId, BookingStatus status);

    boolean existsByEventIdAndGroupCodeAndStatusNot(Long eventId, String groupCode, BookingStatus status);

    long countByEventIdAndStatusNot(Long eventId, BookingStatus status);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByEventIdOrderByCreatedAtAsc(Long eventId);

    Optional<Booking> findByEventIdAndSeatCodeAndStatusNot(Long eventId, String seatCode, BookingStatus status);
}
