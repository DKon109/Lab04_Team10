package com.ooaf.eventticketing.controller;

import  com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import  com.ooaf.eventticketing.service.EventService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/organiser/events")
public class OrganiserEventController {
    private final EventService eventService;

    public OrganiserEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> listEvents(
            Authentication authentication,
            @RequestParam(required = false) String status) {
        String userEmail = authentication == null ? null : authentication.getName();

        return eventService.listOrganiserEvents(userEmail, status);
    }
}