package com.ooaf.eventticketing.config;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataSeederTest {
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void addsMissingDemoEventsToExistingDatabaseWithoutChangingOrDuplicatingRows() throws Exception {
        var existing = events.saveAndFlush(new Event("Existing organiser event", "Keep this event",
                "https://example.com/existing.png", LocalDateTime.now().plusDays(40), "Hall",
                "Sydney", "USYD", "Existing organiser", OrganiserType.SOCIETY,
                "Social", List.of()));
        var originalDate = existing.getDateTime();
        var seeder = new DataSeeder(events, users, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "demoOrganiserPassword", "TestOnlyPassword1!");

        seeder.run();
        assertEquals(31, events.count());
        assertEquals(30, events.findAll().stream()
                .filter(event -> event.getImageUrl() != null
                        && event.getImageUrl().startsWith("https://picsum.photos/seed/"))
                .count());
        assertTrue(events.findById(existing.getId()).isPresent());
        assertEquals(originalDate, events.findById(existing.getId()).orElseThrow().getDateTime());

        seeder.run();
        assertEquals(31, events.count());
    }
}
