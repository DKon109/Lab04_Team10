package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.UserRole;

public record LoginResponse(
        Long id,
        String email,
        UserRole role,
        String token,
        String tokenType,
        long expiresInSeconds
) {
}