package com.ooaf.eventticketing.dto;

public record CampusVenueTemplateResponse(
        String id,
        String name,
        String sourceUrl,
        SeatMapRequest map
){}
