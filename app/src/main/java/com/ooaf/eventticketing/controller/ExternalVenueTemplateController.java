package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.ExternalVenueTemplateRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.service.ExternalVenueTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organiser/venue-templates")
public class ExternalVenueTemplateController {

    private final ExternalVenueTemplateService templateService;

    public ExternalVenueTemplateController(
            ExternalVenueTemplateService templateService
    ) {
        this.templateService = templateService;
    }

    @PostMapping("/external")
    public SeatMapRequest generate(
            @Valid @RequestBody ExternalVenueTemplateRequest request
    ) {
        return templateService.generate(request);
    }
}
