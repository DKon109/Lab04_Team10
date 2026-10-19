package com.ooaf.eventticketing.dto;

/**
 * Provides a consistent response body for API errors.
 */

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path
) {


}
