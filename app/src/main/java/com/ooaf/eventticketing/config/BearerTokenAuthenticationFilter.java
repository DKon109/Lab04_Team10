package com.ooaf.eventticketing.config;

import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class BearerTokenAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public BearerTokenAuthenticationFilter(
            TokenService tokenService
    ) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader != null
                && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String rawToken = authorizationHeader
                    .substring(BEARER_PREFIX.length())
                    .trim();

            tokenService.findUserByRawToken(rawToken)
                    .ifPresent(user ->
                            authenticateRequest(request, user));
        }

        // Spring Security decides later whether this request requires a user.
        filterChain.doFilter(request, response);
    }

    private void authenticateRequest(
            HttpServletRequest request,
            User user
    ) {
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name()
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of(authority)
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}