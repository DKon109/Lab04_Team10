package com.ooaf.eventticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.ooaf.eventticketing.dto.ApiErrorResponse;
import com.ooaf.eventticketing.exception.ApiErrorResponseWriter;

@Configuration
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter
            bearerTokenAuthenticationFilter;

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public SecurityConfig(
            BearerTokenAuthenticationFilter
                    bearerTokenAuthenticationFilter,
            ApiErrorResponseWriter
                    apiErrorResponseWriter
    ) {
        this.bearerTokenAuthenticationFilter =
                bearerTokenAuthenticationFilter;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                // Use the CORS policy defined by CorsConfigurationSource.
                .cors(cors -> {})
                // This API uses authentication tokens instead of browser sessions.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        apiErrorResponseWriter.write(
                                                response,
                                                new ApiErrorResponse(
                                                        401,
                                                        "Unauthorized",
                                                        "Authentication is required",
                                                        request.getRequestURI()
                                                )
                                        )
                        )
                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        apiErrorResponseWriter.write(
                                                response,
                                                new ApiErrorResponse(
                                                        403,
                                                        "Forbidden",
                                                        "You do not have permission to perform this action",
                                                        request.getRequestURI()
                                                )
                                        )
                        ))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        ).permitAll()
                        // Personalised feed needs a user; must be matched
                        // before the broader anonymous GET /api/events/**.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/events/for-you"
                        ).authenticated()
                        // Only organiser accounts may post events.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/events"
                        ).hasRole("COMMITTEE_MEMBER")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/events/*"
                        ).hasRole("COMMITTEE_MEMBER")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/events/*/status"
                        ).hasRole("COMMITTEE_MEMBER")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/events",
                                "/api/events/*"
                        ).permitAll()
                        .requestMatchers("/api/organiser/**")
                        .hasRole("COMMITTEE_MEMBER")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        bearerTokenAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
