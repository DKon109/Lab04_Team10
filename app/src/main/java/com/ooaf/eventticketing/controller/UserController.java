package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.service.LikeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final LikeService likeService;

    public UserController(LikeService likeService) {
        this.likeService = likeService;
    }

    @GetMapping("/liked-events")
    public List<EventResponse> likedEvents(Authentication authentication) {
        return likeService.likedEvents(authentication.getName());
    }
}
