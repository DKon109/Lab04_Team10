package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.FrontStageTemplateRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.service.FrontStageTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organiser/venue-templates")
public class FrontStageTemplateController {

    private final FrontStageTemplateService templateService;

    public FrontStageTemplateController(
            FrontStageTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/front-stage")
    public SeatMapRequest generate(
            @Valid @RequestBody FrontStageTemplateRequest request
    ) {
        return templateService.generate(request);
    }
}
