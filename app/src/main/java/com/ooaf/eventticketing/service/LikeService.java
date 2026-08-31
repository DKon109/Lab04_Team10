package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.EventLike;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.repository.EventLikeCount;
import com.ooaf.eventticketing.repository.EventLikeRepository;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LikeService {

    private final EventLikeRepository eventLikeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public LikeService(EventLikeRepository eventLikeRepository, EventRepository eventRepository,
                       UserRepository userRepository) {
        this.eventLikeRepository = eventLikeRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    /** Idempotent: repeating the request does not create a duplicate relationship. */
    @Transactional
    public void like(String email, Long eventId) {
        User user = findUser(email);
        Event event = findEvent(eventId);
        if (!eventLikeRepository.existsByUserAndEvent(user, event)) {
            eventLikeRepository.save(new EventLike(user, event));
        }
    }

    /** Idempotent: deleting a missing relationship is still successful. */
    @Transactional
    public void unlike(String email, Long eventId) {
        eventLikeRepository.deleteByUserAndEvent(findUser(email), findEvent(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> likedEvents(String email) {
        Map<Long, Long> counts = likeCounts();
        return likedEventEntities(email).stream()
                .map(event -> EventResponse.fromEntity(
                        event, true, counts.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Event> likedEventEntities(String email) {
        return eventLikeRepository.findAllByUserEmailWithEvent(email).stream()
                .map(EventLike::getEvent)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> likedEventIds(String email) {
        if (email == null) {
            return Set.of();
        }
        return likedEventEntities(email).stream().map(Event::getId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> likeCounts() {
        return eventLikeRepository.countLikesByEvent().stream()
                .collect(Collectors.toMap(EventLikeCount::getEventId, EventLikeCount::getLikeCount));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Event not found: " + eventId));
    }
}
