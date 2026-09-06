package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Sprint 1 explainable recommender.
 * Each liked-event comparison gives different amount of weight for category, organiser,
 * and every shared tag. Popularity and start time break score ties.
 */
@Service
public class RecommendationService {

    static final int CATEGORY_WEIGHT = 3;
    static final int ORGANISER_WEIGHT = 2;
    static final int TAG_WEIGHT = 1;

    private final EventRepository eventRepository;
    private final LikeService likeService;

    public RecommendationService(EventRepository eventRepository, LikeService likeService) {
        this.eventRepository = eventRepository;
        this.likeService = likeService;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> recommendations(String email, String scope) {
        List<Event> likedEvents = likeService.likedEventEntities(email);
        Set<Long> likedIds = new HashSet<>();
        likedEvents.forEach(event -> likedIds.add(event.getId()));
        Map<Long, Long> counts = likeService.likeCounts();

        Comparator<Event> ranking = Comparator
                .comparingInt((Event event) -> score(event, likedEvents)).reversed()
                .thenComparing(Comparator.comparingLong(
                        (Event event) -> counts.getOrDefault(event.getId(), 0L)).reversed())
                .thenComparing(Event::getDateTime)
                .thenComparing(Event::getId);

        LocalDateTime now = LocalDateTime.now();
        return eventRepository.findAll().stream()
                .filter(event -> !event.getDateTime().isBefore(now))
                .filter(event -> matchesScope(event, scope))
                .filter(event -> !likedIds.contains(event.getId()))
                .sorted(ranking)
                .map(event -> EventResponse.fromEntity(
                        event, false, counts.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    static int score(Event candidate, List<Event> likedEvents) {
        int score = 0;
        Set<String> candidateTags = normalisedTags(candidate);
        for (Event liked : likedEvents) {
            if (same(candidate.getCategory(), liked.getCategory())) {
                score += CATEGORY_WEIGHT;
            }
            if (same(candidate.getOrganiserName(), liked.getOrganiserName())) {
                score += ORGANISER_WEIGHT;
            }
            Set<String> sharedTags = new HashSet<>(candidateTags);
            sharedTags.retainAll(normalisedTags(liked));
            score += sharedTags.size() * TAG_WEIGHT;
        }
        return score;
    }

    private boolean matchesScope(Event event, String scope) {
        if (scope == null || scope.isBlank() || scope.equalsIgnoreCase("ALL")
                || scope.equalsIgnoreCase("SYDNEY")) {
            return "Sydney".equalsIgnoreCase(event.getCity());
        }
        return scope.equalsIgnoreCase(event.getUniversity());
    }

    private static Set<String> normalisedTags(Event event) {
        Set<String> result = new HashSet<>();
        event.getTags().forEach(tag -> result.add(tag.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
