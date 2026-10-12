package com.ooaf.eventticketing.exception;

import com.ooaf.eventticketing.dto.ApiErrorResponse;
import com.ooaf.eventticketing.dto.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                409, "Conflict", "The request conflicts with existing data. Check your booking before retrying.", request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());

        String message = exception.getReason();

        // Hide internal error details when a server-side failure occurs.
        if (status.is5xxServerError() || message == null) {
            message = status.getReasonPhrase();
        }

        ApiErrorResponse body = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidationErrors(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        // Keep one clear message when a field violates multiple rules.
                        errors.putIfAbsent(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return new ValidationErrorResponse(
                400,
                "Bad Request",
                "Validation failed",
                request.getRequestURI(),
                errors
        );
    }
}
