package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final LikeService likeService;

    public EventService(EventRepository eventRepository, LikeService likeService) {
        this.eventRepository = eventRepository;
        this.likeService = likeService;
    }

    public List<EventResponse> listEvents(String scope, OrganiserType organiserType, String category,
                                          LocalDate date, boolean includePast, String currentUserEmail) {
        Set<Long> likedIds = likeService.likedEventIds(currentUserEmail);
        Map<Long, Long> counts = likeService.likeCounts();
        return eventRepository.findAllByOrderByDateTimeAsc().stream()
                .filter(event -> matches(event, scope, organiserType, category, date, includePast))
                .map(event -> EventResponse.fromEntity(event, likedIds.contains(event.getId()),
                        counts.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    public EventResponse getEvent(Long id, String currentUserEmail) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
        boolean liked = likeService.likedEventIds(currentUserEmail).contains(id);
        long count = likeService.likeCounts().getOrDefault(id, 0L);
        return EventResponse.fromEntity(event, liked, count);
    }

    private boolean matches(Event event, String scope, OrganiserType organiserType,
                            String category, LocalDate date, boolean includePast) {
        if (!includePast && event.getDateTime().isBefore(LocalDateTime.now())) {
            return false;
        }
        if (!isAllSydney(scope) && !scope.equalsIgnoreCase(event.getUniversity())) {
            return false;
        }
        if (organiserType != null && event.getOrganiserType() != organiserType) {
            return false;
        }
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase(event.getCategory())) {
            return false;
        }
        return date == null || event.getDateTime().toLocalDate().equals(date);
    }

    private boolean isAllSydney(String scope) {
        return scope == null || scope.isBlank() || scope.equalsIgnoreCase("ALL") || scope.equalsIgnoreCase("SYDNEY");
    }
}
