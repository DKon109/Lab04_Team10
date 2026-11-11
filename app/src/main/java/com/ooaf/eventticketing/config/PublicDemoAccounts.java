package com.ooaf.eventticketing.config;

import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Fictional shared account, enabled only for the personal public demo. */
@Component
@Profile("public-demo & !test")
public class PublicDemoAccounts implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public PublicDemoAccounts(UserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    @Override public void run(String... args) {
        String email = "attendee.demo@uni.sydney.edu.au";
        if (!users.existsByEmail(email)) {
            users.save(new User(email, encoder.encode("TixoraDemo3!"), UserRole.STUDENT));
        }
    }
}
