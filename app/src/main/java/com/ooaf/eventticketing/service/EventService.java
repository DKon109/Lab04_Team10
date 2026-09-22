package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.CreateEventRequest;
import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final LikeService likeService;

    public EventService(EventRepository eventRepository, UserRepository userRepository, LikeService likeService) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.likeService = likeService;
    }

    public List<EventResponse> listEvents(String scope, OrganiserType organiserType, String category,
                                          LocalDate date, boolean includePast, String userEmail) {
        List<Event> events = eventRepository.findAllByOrderByDateTimeAsc().stream()
                .filter(event -> matches(event, scope, organiserType, category, date, includePast))
                .toList();
        return withLikeInfo(events, userEmail);
    }

    public EventResponse getEvent(Long id, String userEmail) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
        return withLikeInfo(List.of(event), userEmail).get(0);
    }

    public List<EventResponse> likedEvents(String userEmail) {
        return likeService.likedEvents(userEmail);
    }

    public EventResponse createEvent(CreateEventRequest request, String userEmail) {
        User user = requireCommitteeMember(userEmail);
        String organiserName = requireMatchingSociety(user, request.organiserName());
        String city = (request.city() == null || request.city().isBlank()) ? "Sydney" : request.city().trim();
        String university = (request.university() == null || request.university().isBlank())
                ? null : request.university().trim();
        Event event = new Event(
                request.title().trim(), request.description(), request.imageUrl(),
                request.dateTime(), request.venue().trim(), city, university,
                organiserName, request.organiserType(),
                request.category().trim(),
                request.tags() == null ? new ArrayList<>() : new ArrayList<>(request.tags())
        );
        return EventResponse.fromEntity(eventRepository.save(event), false, 0);
    }

    /** Decorates events with the caller's likedByMe flags and real like counts. */
    private List<EventResponse> withLikeInfo(List<Event> events, String userEmail) {
        Set<Long> likedIds = likeService.likedEventIds(userEmail);
        Map<Long, Long> counts = likeService.likeCounts();
        return events.stream()
                .map(event -> EventResponse.fromEntity(
                        event,
                        likedIds.contains(event.getId()),
                        counts.getOrDefault(event.getId(), 0L)))
                .toList();
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

    private User requireCommitteeMember(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        if (user.getRole() != UserRole.COMMITTEE_MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Committee member access is required");
        }
        return user;
    }

    private String requireMatchingSociety(User user, String requestedOrganiserName) {
        if (user.getSocietyName() == null || user.getSocietyName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Committee member must be assigned to a society");
        }
        String societyName = user.getSocietyName().trim();
        if (requestedOrganiserName == null || !societyName.equalsIgnoreCase(requestedOrganiserName.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Committee member can only create events for their society");
        }
        return societyName;
    }
}
