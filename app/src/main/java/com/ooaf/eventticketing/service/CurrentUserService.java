package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.CurrentUserResponse;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user no longer exists"
                ));

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}