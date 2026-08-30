package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.service.EventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
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
            @RequestParam(defaultValue = "false") boolean includePast) {
        return eventService.listEvents(scope, organiserType, category, date, includePast);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }
}
