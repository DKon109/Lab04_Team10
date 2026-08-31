package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.UserRole;

public record CurrentUserResponse(
        Long id,
        String email,
        UserRole role
) {
}