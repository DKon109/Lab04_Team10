package com.ooaf.eventticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        Boolean createGroup,
        @Size(max = 32, message = "Group code must not exceed 32 characters")
        String joinGroupCode,
        Boolean accessibilityRequired,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey){
}