package com.ooaf.eventticketing.dto;

import jakarta.validation.constraints.*;

public record MockCheckoutRequest(Boolean createGroup, @Size(max = 32) String joinGroupCode,
        Boolean accessibilityRequired, @NotBlank @Size(max = 80) String idempotencyKey,
        @NotNull Outcome outcome) {
    public enum Outcome { SUCCESS, DECLINED, CANCELLED }
}
