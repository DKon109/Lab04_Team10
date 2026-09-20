package com.ooaf.eventticketing.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ApiExceptionHandlerTest {

    @Test
    void hideServerErrorDetails() {
        ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();

        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/auth/login");

        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal database details"
        );

        var response =
                apiExceptionHandler.handleResponseStatusException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("Internal Server Error", response.getBody().error());
        assertEquals("Internal Server Error", response.getBody().message());
        assertEquals("/api/auth/login", response.getBody().path());
    }
}
