package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.CampusVenueTemplateResponse;
import com.ooaf.eventticketing.service.CampusVenueTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organiser/venue-templates")
public class CampusVenueTemplateController {

    private final CampusVenueTemplateService templateService;

    public CampusVenueTemplateController(
            CampusVenueTemplateService templateService
    ) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<CampusVenueTemplateResponse> listTemplates() {
        return templateService.listTemplates();
    }
}
