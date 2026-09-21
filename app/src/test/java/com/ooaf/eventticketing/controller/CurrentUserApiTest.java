package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.LoginResponse;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ooaf.eventticketing.entity.AuthenticationToken;
import com.ooaf.eventticketing.repository.AuthenticationTokenRepository;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CurrentUserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationTokenRepository tokenRepository;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void rejectsRequestWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));

    }

    @Test
    void returnsCurrentUserWithValidToken() throws Exception {
        User user = new User(
                "current.user@uni.sydney.edu.au",
                passwordEncoder.encode("StrongPassword123!"),
                UserRole.STUDENT
        );
        User savedUser = userRepository.saveAndFlush(user);

        LoginResponse loginResponse =
                tokenService.issueToken(savedUser);

        mockMvc.perform(get("/api/auth/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + loginResponse.token()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(savedUser.getId()))
                .andExpect(jsonPath("$.email")
                        .value("current.user@uni.sydney.edu.au"))
                .andExpect(jsonPath("$.role")
                        .value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsRequestWithExpiredToken() throws Exception {
        User user = new User(
                "expired.token@uni.sydney.edu.au",
                passwordEncoder.encode("StrongPassword123!"),
                UserRole.STUDENT
        );
        User savedUser = userRepository.saveAndFlush(user);

        String rawToken = "expired-test-token";

        AuthenticationToken expiredToken = new AuthenticationToken(
                tokenService.hashToken(rawToken),
                savedUser,
                Instant.now().minusSeconds(60)
        );
        tokenRepository.saveAndFlush(expiredToken);

        mockMvc.perform(get("/api/auth/me")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + rawToken
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }
}
