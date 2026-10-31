package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.CreateEventRequest;
import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;

import com.ooaf.eventticketing.entity.EventStatus;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final LikeService likeService;
    private final UserRepository userRepository;
    private final com.ooaf.eventticketing.repository.SeatingLayoutRepository layouts;
    private final com.ooaf.eventticketing.repository.ManagedSeatMapRepository managedSeatMaps;

    public EventService(EventRepository eventRepository, LikeService likeService,
            UserRepository userRepository,
            com.ooaf.eventticketing.repository.SeatingLayoutRepository layouts,
            com.ooaf.eventticketing.repository.ManagedSeatMapRepository managedSeatMaps) {
        this.eventRepository = eventRepository;
        this.likeService = likeService;
        this.userRepository = userRepository;
        this.layouts = layouts;
        this.managedSeatMaps = managedSeatMaps;
    }

    public List<EventResponse> listEvents(String scope, OrganiserType organiserType, String category,
                                          LocalDate date, boolean includePast, String userEmail) {
        List<Event> events = eventRepository.findAllByOrderByDateTimeAsc().stream()
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED)
                .filter(event -> matches(event, scope, organiserType, category, date, includePast))
                .toList();
        return withLikeInfo(events, userEmail);
    }

    public EventResponse getEvent(Long id, String userEmail) {
        Event event = eventRepository.findById(id)
                .filter(foundEvent ->
                        foundEvent.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
        return withLikeInfo(List.of(event), userEmail).get(0);
    }

    public List<EventResponse> likedEvents(String userEmail) {
        return likeService.likedEvents(userEmail);
    }

    public EventResponse createEvent(
            CreateEventRequest request, String userEmail) {
        User owner = requireOrganiser(userEmail);
        String organiserName =
                requireMatchingSociety(owner, request.organiserName());

        String city = (request.city() == null || request.city().isBlank())
                ? "Sydney" : request.city().trim();

        String university =
                (request.university() == null || request.university().isBlank())
                        ? null : request.university().trim();

        Event event = new Event(
                request.title().trim(), request.description(), request.imageUrl(),
                request.dateTime(), request.venue().trim(), city, university,
                organiserName, request.organiserType(),
                request.category().trim(),
                request.tags() == null
                        ? new ArrayList<>() : new ArrayList<>(request.tags())
        );
        if (request.capacity() != null) event.setCapacity(request.capacity());
        event.setSeatIdentifiersConfirmed(Boolean.TRUE.equals(request.seatIdentifiersConfirmed()));
        event.setOwner(owner);
        return EventResponse.fromEntity(eventRepository.save(event), false, 0);
    }

    public List<EventResponse> listOrganiserEvents(
            String userEmail, String status) {
        User owner = requireOrganiser(userEmail);
        EventStatus statusFilter = parseStatusFilter(status);

        List<Event> events = eventRepository
                .findByOwner_IdOrderByDateTimeAsc(owner.getId())
                .stream()
                .filter(event -> statusFilter == null
                        || event.getStatus() == statusFilter)
                .toList();

        return withLikeInfo(events, userEmail);
    }

    @Transactional
    public EventResponse updateEvent(
            Long eventId, CreateEventRequest request, String userEmail) {
        Event event = requireOwnedEvent(eventId, userEmail);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only draft events can be updated");
        }

        String organiserName = requireMatchingSociety(
                event.getOwner(), request.organiserName());

        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setImageUrl(request.imageUrl());
        event.setDateTime(request.dateTime());
        event.setVenue(request.venue().trim());
        event.setCity(
                request.city() == null || request.city().isBlank()
    ? "Sydney" : request.city().trim()
        );
        event.setUniversity(
                request.university() == null || request.university().isBlank()
                        ? null : request.university().trim()
        );
        event.setOrganiserName(organiserName);
        event.setOrganiserType(request.organiserType());
        event.setCategory(request.category().trim());
        if (!layouts.existsById(eventId) && !managedSeatMaps.existsById(eventId)) {
            if (request.capacity() != null) event.setCapacity(request.capacity());
            event.setSeatIdentifiersConfirmed(Boolean.TRUE.equals(request.seatIdentifiersConfirmed()));
        }
        event.setTags(request.tags() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.tags())
        );

        return withLikeInfo(List.of(event), userEmail).get(0);
    }

    @Transactional
    public EventResponse changeEventStatus(
            Long eventId, String status, String userEmail) {
        Event event = requireOwnedEvent(eventId, userEmail);
        EventStatus targetStatus = parseStatusFilter(status);

        boolean canPublish =
                event.getStatus() == EventStatus.DRAFT
                && targetStatus == EventStatus.PUBLISHED;

        boolean canClose =
                event.getStatus() == EventStatus.PUBLISHED
                && targetStatus == EventStatus.CLOSED;

        if  (!canPublish && !canClose) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only draft-to-published and published-to-closed transitions are allowed"
            );
        }

        // Require a future event date when publishing.
        if (canPublish && (event.getDateTime() == null
        || !event.getDateTime().isAfter(LocalDateTime.now()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Event date must be in the future before publishing");
        }

        if (canPublish && (event.getVenue() == null || event.getVenue().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Event venue is required before publishing"
            );
        }

        if (canPublish && (event.getCapacity() == null || event.getCapacity() <= 0)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Event capacity must be greater than 0 before publishing");
        }

        if (canPublish && event.isSeatIdentifiersConfirmed()
                && !layouts.existsById(eventId) && !managedSeatMaps.existsById(eventId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Save the seating layout before publishing an assigned-seating event");
        }
        if (canClose) event.setRegistrationStatus(com.ooaf.eventticketing.entity.EventRegistrationStatus.CLOSED);
        event.setStatus(targetStatus);
        return withLikeInfo(List.of(event), userEmail).get(0);
    }

    private EventStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return EventStatus.valueOf(
                    status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception)  {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must be draft, published, or closed");
        }
    }

    private User requireOrganiser(String userEmail) {
        if  (userEmail == null || userEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findByEmail(userEmail).orElseThrow(()  -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found: "));

        if  (user.getRole() != UserRole.COMMITTEE_MEMBER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only organisers can perform this action");
        }
        return user;
    }

    private Event requireOwnedEvent(Long eventId, String userEmail) {
        User organiser = requireOrganiser(userEmail);

        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event Not found"));

        if (event.getOwner() == null || !organiser.getId().equals(event.getOwner().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the event owner can perform this action");
        }
        return event;
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
        if (!"Sydney".equalsIgnoreCase(event.getCity())) {
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

    private String requireMatchingSociety(
            User user, String requestedOrganiserName) {
        if (user.getSocietyName() == null || user.getSocietyName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Committee member must be assigned to a society");
        }

        String societyName = user.getSocietyName().trim();

        if (requestedOrganiserName == null
                || !societyName.equalsIgnoreCase(
                        requestedOrganiserName.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Committee member can only manage events for their society");
        }

        return societyName;
    }
}
