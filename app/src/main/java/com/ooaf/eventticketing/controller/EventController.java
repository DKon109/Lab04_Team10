package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.CreateEventRequest;
import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.service.EventService;
import com.ooaf.eventticketing.service.LikeService;
import com.ooaf.eventticketing.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;
    private final LikeService likeService;

    private final RecommendationService recommendationService;

    public EventController(EventService eventService, LikeService likeService,
                           RecommendationService recommendationService) {
        this.eventService = eventService;
        this.likeService = likeService;
        this.recommendationService = recommendationService;
    }
    /**
     * Examples:
     *   GET /api/events                       -> all upcoming Sydney events
     *   GET /api/events?scope=USYD             -> upcoming USYD events only
     *   GET /api/events?organiserType=SOCIETY  -> upcoming society-run events
     *   GET /api/events?category=Music&date=2026-09-12
     */
    @GetMapping
    public List<EventResponse> listEvents(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) OrganiserType organiserType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean includePast,
            Authentication authentication) {
        return eventService.listEvents(scope, organiserType, category, date, includePast,
                emailOf(authentication));
    }

    /** Personalised feed; requires authentication (enforced in SecurityConfig). */
    @GetMapping("/for-you")
    public List<EventResponse> forYou(@RequestParam(required = false) String scope,
                                      Authentication authentication) {
        return recommendationService.recommendations(emailOf(authentication), scope);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable Long eventId, Authentication authentication) {
        return eventService.getEvent(eventId, emailOf(authentication));
    }

    @PostMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void like(@PathVariable Long eventId, Authentication authentication) {
        likeService.like(emailOf(authentication), eventId);
    }

    @DeleteMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(@PathVariable Long eventId, Authentication authentication) {
        likeService.unlike(emailOf(authentication), eventId);
    }

    /** Organiser-only event posting (role enforced in SecurityConfig). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request,
                                     Authentication authentication) {
        return eventService.createEvent(request,  emailOf(authentication));
    }

    private String emailOf(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
