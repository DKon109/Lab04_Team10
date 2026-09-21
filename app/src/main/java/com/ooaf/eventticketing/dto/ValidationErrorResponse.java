package com.ooaf.eventticketing.dto;

import java.util.Map;

public record ValidationErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors
) {
}