package com.ooaf.eventticketing.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void allowsConfiguredFrontendOriginForApiRoutes() {
        String frontendOrigin = "http://localhost:5173";

        CorsConfigurationSource source =
                new CorsConfig(frontendOrigin)
                        .corsConfigurationSource();

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "OPTIONS",
                        "/api/auth/login"
                );

        CorsConfiguration configuration =
                source.getCorsConfiguration(request);

        assertNotNull(configuration);

        assertEquals(
                List.of(frontendOrigin),
                configuration.getAllowedOrigins()
        );

        assertTrue(
                configuration.getAllowedMethods().containsAll(
                        List.of("GET", "POST", "DELETE", "OPTIONS")
                )
        );

        assertTrue(
                configuration.getAllowedHeaders().containsAll(
                        List.of("Authorization", "Content-Type")
                )
        );
    }
}
