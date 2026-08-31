package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.EventLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventLikeRepository extends JpaRepository<EventLike, Long> {

    Optional<EventLike> findByUserIdAndEventId(Long userId, Long eventId);

    List<EventLike> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select l.event.id from EventLike l where l.user.email = :email")
    List<Long> findEventIdsByUserEmail(@Param("email") String email);

    /** Rows of [eventId, likeCount] for every event that has at least one like. */
    @Query("select l.event.id, count(l) from EventLike l group by l.event.id")
    List<Object[]> countAllGroupedByEvent();
}
