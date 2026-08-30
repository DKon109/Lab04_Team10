package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.AuthenticationToken;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationTokenRepositoryTest {

    @Autowired
    private AuthenticationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void loadsUserWithAuthenticationToken() {
        User user = new User(
                "token.repository@uni.sydney.edu.au",
                "hashed-password",
                UserRole.STUDENT
        );
        User savedUser = userRepository.saveAndFlush(user);

        AuthenticationToken token =
                new AuthenticationToken(
                        "a".repeat(64),
                        savedUser,
                        Instant.now().plusSeconds(3600)
                );
        tokenRepository.saveAndFlush(token);

        entityManager.clear();

        AuthenticationToken savedToken =
                tokenRepository.findByTokenHash("a".repeat(64))
                        .orElseThrow();

        assertTrue(
                entityManagerFactory
                        .getPersistenceUnitUtil()
                        .isLoaded(savedToken.getUser())
        );
    }
}