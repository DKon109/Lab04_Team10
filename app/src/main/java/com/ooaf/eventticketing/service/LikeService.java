package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.EventLike;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.repository.EventLikeRepository;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persistent likes plus the agreed explainable "For You" recommendation
 * scoring (same formula the frontend uses in its temporary local mode):
 * +3 same category as a liked event, +2 same organiser, +1 per matching tag.
 */
@Service
public class LikeService {

    private final EventLikeRepository likeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public LikeService(EventLikeRepository likeRepository,
                       EventRepository eventRepository,
                       UserRepository userRepository) {
        this.likeRepository = likeRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void like(String userEmail, Long eventId) {
        User user = requireUser(userEmail);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventId));
        // Idempotent: liking an already-liked event is not an error.
        if (likeRepository.findByUserIdAndEventId(user.getId(), eventId).isEmpty()) {
            likeRepository.save(new EventLike(user, event));
        }
    }

    @Transactional
    public void unlike(String userEmail, Long eventId) {
        User user = requireUser(userEmail);
        likeRepository.findByUserIdAndEventId(user.getId(), eventId)
                .ifPresent(likeRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<Event> likedEvents(String userEmail) {
        User user = requireUser(userEmail);
        return likeRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(EventLike::getEvent)
                .toList();
    }

    /** Liked event ids for the given user; empty for anonymous requests. */
    @Transactional(readOnly = true)
    public Set<Long> likedEventIds(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(likeRepository.findEventIdsByUserEmail(userEmail));
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> likeCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : likeRepository.countAllGroupedByEvent()) {
            counts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }

    /**
     * Upcoming events ranked by similarity to what the user has liked.
     * Cold start (no likes yet) falls back to all upcoming events by date.
     * Already-liked events are excluded from the recommendations.
     */
    @Transactional(readOnly = true)
    public List<Event> forYou(String userEmail) {
        LocalDateTime now = LocalDateTime.now();
        List<Event> upcoming = eventRepository.findAllByOrderByDateTimeAsc().stream()
                .filter(event -> !event.getDateTime().isBefore(now))
                .toList();

        Set<Long> likedIds = likedEventIds(userEmail);
        if (likedIds.isEmpty()) {
            return upcoming;
        }
        List<Event> liked = eventRepository.findAllById(likedIds);

        return upcoming.stream()
                .filter(event -> !likedIds.contains(event.getId()))
                .sorted(Comparator.comparingInt((Event e) -> score(e, liked)).reversed()
                        .thenComparing(Event::getDateTime))
                .toList();
    }

    static int score(Event candidate, List<Event> likedEvents) {
        int score = 0;
        for (Event liked : likedEvents) {
            if (equalsIgnoreCase(candidate.getCategory(), liked.getCategory())) {
                score += 3;
            }
            if (equalsIgnoreCase(candidate.getOrganiserName(), liked.getOrganiserName())) {
                score += 2;
            }
            for (String tag : candidate.getTags()) {
                if (liked.getTags().stream().anyMatch(t -> equalsIgnoreCase(t, tag))) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private User requireUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }
}
