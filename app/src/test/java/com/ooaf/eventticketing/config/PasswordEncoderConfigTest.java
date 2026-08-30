package com.ooaf.eventticketing.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderConfigTest {

    private final PasswordEncoder passwordEncoder =
            new PasswordEncoderConfig().passwordEncoder();

    @Test
    void encodesAndMatchesPassword() {
        String rawPassword = "StrongPassword123!";

        String passwordHash = passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, passwordHash);
        assertTrue(passwordEncoder.matches(rawPassword, passwordHash));
        assertFalse(passwordEncoder.matches(
                "WrongPassword123!",
                passwordHash
        ));
    }
}