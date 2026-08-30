package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.UserRole;

/** Registration result that deliberately excludes all password data. */
public record RegistrationResponse(
        Long id,
        String email,
        UserRole role
) {
}
