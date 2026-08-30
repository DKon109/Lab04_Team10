package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.LoginResponse;
import com.ooaf.eventticketing.entity.AuthenticationToken;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.repository.AuthenticationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenService {

    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final AuthenticationTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
            AuthenticationTokenRepository tokenRepository
    ) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public LoginResponse issueToken(User user) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt =
                Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS);

        AuthenticationToken authenticationToken =
                new AuthenticationToken(
                        tokenHash,
                        user,
                        expiresAt
                );

        tokenRepository.save(authenticationToken);

        // Return the raw token only once; only its hash is persisted.
        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                rawToken,
                "Bearer",
                TOKEN_LIFETIME_SECONDS
        );
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by the Java platform.
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }
}