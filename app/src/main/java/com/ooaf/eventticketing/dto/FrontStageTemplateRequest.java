package com.ooaf.eventticketing.dto;

import jakarta.validation.constraints.*;

import java.util.List;

public record FrontStageTemplateRequest (
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(20) int rows,
        @Min(1) @Max(20) int seatsPerRow,
        @Min(0) @Max(19) int aisleAfter,
        @NotNull @Size(max = 300)
        List<@NotBlank @Pattern(regexp = "[A-T][1-9][0-9]?") String> blockedCodes,
        @NotNull @Size(max = 300)
        List<@NotBlank @Pattern(regexp = "[A-T][1-9][0-9]?") String> accessibleCodes
) {}
