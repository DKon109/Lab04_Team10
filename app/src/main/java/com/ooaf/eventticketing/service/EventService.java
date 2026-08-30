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

@Service
public class EventService {
    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventResponse> listEvents(String scope, OrganiserType organiserType, String category,
                                          LocalDate date, boolean includePast) {
        return eventRepository.findAllByOrderByDateTimeAsc().stream()
                .filter(event -> matches(event, scope, organiserType, category, date, includePast))
                .map(EventResponse::fromEntity)
                // TODO(Member 5 integration): once the Like repository exists, look up the
                // current user's liked event IDs here and return updated EventResponses with
                // likedByMe/likeCount set, instead of the defaults from fromEntity().
                .toList();
    }

    public EventResponse getEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
        return EventResponse.fromEntity(event);
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
