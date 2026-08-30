package com.ooaf.eventticketing.repository;

import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        User user = new User(
                "repository-test@uni.sydney.edu.au",
                "hashed-password",
                UserRole.STUDENT
        );

        userRepository.saveAndFlush(user);

        Optional<User> savedUser =
                userRepository.findByEmail("repository-test@uni.sydney.edu.au");

        assertTrue(savedUser.isPresent());
        assertEquals("repository-test@uni.sydney.edu.au",
                savedUser.get().getEmail());
        assertEquals(UserRole.STUDENT, savedUser.get().getRole());
    }
}