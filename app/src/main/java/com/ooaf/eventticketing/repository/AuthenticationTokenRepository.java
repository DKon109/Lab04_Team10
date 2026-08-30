package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.AuthenticationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthenticationTokenRepository
        extends JpaRepository<AuthenticationToken, Long> {

    Optional<AuthenticationToken> findByTokenHash(String tokenHash);
}