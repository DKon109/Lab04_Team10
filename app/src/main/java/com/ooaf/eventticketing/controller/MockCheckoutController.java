package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.service.MockCheckoutService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class MockCheckoutController {
    private final MockCheckoutService service;
    public MockCheckoutController(MockCheckoutService service) { this.service = service; }

    @PostMapping("/api/events/{eventId}/mock-checkout")
    public MockCheckoutResponse checkout(@PathVariable Long eventId, @Valid @RequestBody MockCheckoutRequest request, Authentication authentication) {
        return service.checkout(eventId, authentication.getName(), request);
    }
}
