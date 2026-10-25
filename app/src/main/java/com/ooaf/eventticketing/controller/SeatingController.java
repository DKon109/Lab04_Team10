package com.ooaf.eventticketing.controller;
import com.ooaf.eventticketing.allocation.AllocationStrategy;
import com.ooaf.eventticketing.dto.AllocationMetricsResponse;
import com.ooaf.eventticketing.dto.SeatingLayoutRequest;
import com.ooaf.eventticketing.entity.AllocationRun;
import com.ooaf.eventticketing.service.SeatingService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class SeatingController {
    private final SeatingService service;
    public SeatingController(SeatingService service) { this.service = service; }
    @GetMapping("/api/organiser/seating-profiles")
    public List<SeatingService.CampusProfile> profiles() { return SeatingService.CAMPUS_PROFILES; }
    @GetMapping("/api/organiser/events/{id}/seating")
    public SeatingService.ManagementView management(@PathVariable Long id, Authentication auth) { return service.management(id, auth.getName()); }
    @PutMapping("/api/organiser/events/{id}/seating")
    public SeatingService.ManagementView configure(@PathVariable Long id, @Valid @RequestBody SeatingLayoutRequest request, Authentication auth) { return service.configure(id, auth.getName(), request); }
    @PostMapping("/api/organiser/events/{id}/allocate")
    public AllocationRun allocate(@PathVariable Long id,
            @RequestParam(required = false) AllocationStrategy strategy, Authentication auth) {
        return service.allocate(id, auth.getName(), strategy);
    }
    @GetMapping("/api/organiser/events/{id}/allocation/metrics")
    public AllocationMetricsResponse metrics(@PathVariable Long id, Authentication auth) {
        return service.metricsResponse(id, auth.getName());
    }
    @GetMapping("/api/bookings/{id}/ticket")
    public SeatingService.TicketView ticket(@PathVariable Long id, Authentication auth) { return service.ticket(id, auth.getName()); }
    @GetMapping("/api/organiser/events/{id}/allocation/export")
    public ResponseEntity<String> export(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=event-" + id + "-allocation.csv")
                .body(service.export(id, auth.getName()));
    }
}
