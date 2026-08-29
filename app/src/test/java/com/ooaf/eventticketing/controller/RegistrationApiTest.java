package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersValidUniversityUser() throws Exception {
        String rawPassword = "StrongPassword123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new.student@uni.sydney.edu.au",
                                  "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email")
                        .value("new.student@uni.sydney.edu.au"))
                .andExpect(jsonPath("$.role")
                        .value("STUDENT"));

        User savedUser = userRepository
                .findByEmail("new.student@uni.sydney.edu.au")
                .orElseThrow();

        assertTrue(passwordEncoder.matches(
                rawPassword,
                savedUser.getPasswordHash()
        ));
        assertFalse(savedUser.getPasswordHash()
                .contains(rawPassword));
        assertTrue(savedUser.getRole() == UserRole.STUDENT);
    }

    @Test
    void rejectsEmailOutsideUniversityDomain() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "student@gmail.com",
                              "password": "StrongPassword123!"
                            }
                            """))
                .andExpect(status().isBadRequest());
    }
    @Test
    void rejectsDuplicateEmail() throws Exception {
        String requestBody = """
            {
              "email": "duplicate@uni.sydney.edu.au",
              "password": "StrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }
    @Test
    void rejectsMissingEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "password": "StrongPassword123!"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.errors.email")
                        .value("Email is required"));
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "student@uni.sydney.edu.au",
                              "password": "short"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.errors.password")
                        .value(
                                "Password must be between 8 and 72 characters"
                        ));
    }
}