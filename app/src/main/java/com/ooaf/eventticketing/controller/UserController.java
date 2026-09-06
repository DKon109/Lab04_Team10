package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.service.EventService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final EventService eventService;

    public UserController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/me/liked-events")
    public List<EventResponse> likedEvents(Authentication authentication) {
        return eventService.likedEvents(authentication.getName());
    }
}
