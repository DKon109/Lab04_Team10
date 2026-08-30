package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.RegistrationRequest;
import com.ooaf.eventticketing.dto.RegistrationResponse;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Applies registration rules before creating a user account.
 */
@Service
public class RegistrationService {

    // Public Sprint 1 registration is restricted to USYD student accounts.
    private static final String ACCEPTED_UNIVERSITY_DOMAIN =
            "@uni.sydney.edu.au";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegistrationResponse register(
            RegistrationRequest request
    ) {
        String normalizedEmail =
                normalizeAndValidateEmail(request.email());

        String passwordHash =
                passwordEncoder.encode(request.password());

        // Public users must not be able to grant themselves privileged roles.
        User user = new User(
                normalizedEmail,
                passwordHash,
                UserRole.STUDENT
        );

        User savedUser = userRepository.save(user);

        return new RegistrationResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    private boolean isAcceptedUniversityEmail(String email) {
        return email != null
                && email.toLowerCase(Locale.ROOT)
                .endsWith(ACCEPTED_UNIVERSITY_DOMAIN);
    }

    private String normalizeAndValidateEmail(String email) {
        if (email == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required"
            );
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        if (!isAcceptedUniversityEmail(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email must belong to uni.sydney.edu.au"
            );
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already registered"
            );
        }

        return normalizedEmail;
    }

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
