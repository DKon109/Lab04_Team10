package com.ooaf.eventticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateEventStatusRequest (
    @NotBlank(message = "Status is required")
    @Pattern(
            regexp = "published|closed",
            message = "Status must be published or closed"
    )
    String status
            ) {
}
