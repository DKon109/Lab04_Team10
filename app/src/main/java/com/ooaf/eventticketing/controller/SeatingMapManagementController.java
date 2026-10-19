package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.SeatAssignmentRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.service.SeatingMapManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class SeatingMapManagementController {
    private final SeatingMapManagementService service;

    public SeatingMapManagementController(SeatingMapManagementService service) {
        this.service = service;
    }

    @GetMapping("/api/organiser/events/{eventId}/seat-map")
    public SeatingMapManagementService.SeatMapView organiserMap(
            @PathVariable Long eventId, Authentication authentication) {
        return service.organiserMap(eventId, authentication.getName());
    }

    @PutMapping("/api/organiser/events/{eventId}/seat-map")
    public SeatingMapManagementService.SeatMapView saveMap(
            @PathVariable Long eventId, @Valid @RequestBody SeatMapRequest request,
            Authentication authentication) {
        return service.saveMap(eventId, authentication.getName(), request);
    }

    @DeleteMapping("/api/organiser/events/{eventId}/seat-map")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMap(@PathVariable Long eventId, Authentication authentication) {
        service.deleteMap(eventId, authentication.getName());
    }

    @GetMapping("/api/events/{eventId}/seat-map")
    public SeatingMapManagementService.SeatMapView attendeeMap(@PathVariable Long eventId) {
        return service.attendeeMap(eventId);
    }

    @PutMapping("/api/organiser/events/{eventId}/bookings/{bookingId}/seat")
    public SeatingMapManagementService.AssignmentView assignByOrganiser(
            @PathVariable Long eventId, @PathVariable Long bookingId,
            @Valid @RequestBody SeatAssignmentRequest request, Authentication authentication) {
        return service.assignByOrganiser(eventId, bookingId, authentication.getName(), request);
    }

    @PutMapping("/api/bookings/{bookingId}/seat")
    public SeatingMapManagementService.AssignmentView chooseSeat(
            @PathVariable Long bookingId, @Valid @RequestBody SeatAssignmentRequest request,
            Authentication authentication) {
        return service.chooseSeat(bookingId, authentication.getName(), request);
    }
}
