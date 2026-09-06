package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.EventLike;
import com.ooaf.eventticketing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventLikeRepository extends JpaRepository<EventLike, Long> {

    boolean existsByUserAndEvent(User user, Event event);

    long deleteByUserAndEvent(User user, Event event);

    @Query("select eventLike from EventLike eventLike " +
            "join fetch eventLike.event where eventLike.user.email = :email " +
            "order by eventLike.createdAt desc, eventLike.id desc")
    List<EventLike> findAllByUserEmailWithEvent(String email);

    @Query("select eventLike.event.id as eventId, count(eventLike) as likeCount " +
            "from EventLike eventLike group by eventLike.event.id")
    List<EventLikeCount> countLikesByEvent();
}
