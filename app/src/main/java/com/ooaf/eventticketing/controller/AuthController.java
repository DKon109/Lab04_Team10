package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.CurrentUserResponse;
import com.ooaf.eventticketing.dto.LoginRequest;
import com.ooaf.eventticketing.dto.LoginResponse;
import com.ooaf.eventticketing.dto.RegistrationRequest;
import com.ooaf.eventticketing.dto.RegistrationResponse;
import com.ooaf.eventticketing.service.AuthenticationService;
import com.ooaf.eventticketing.service.CurrentUserService;
import com.ooaf.eventticketing.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final CurrentUserService currentUserService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            CurrentUserService currentUserService
    ) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authenticationService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            Authentication authentication
    ) {
        return currentUserService.getCurrentUser(
                authentication.getName()
        );
    }
}