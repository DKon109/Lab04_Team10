package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    private Long musicEventId;
    private Long careersEventId;

    @BeforeEach
    void seed() {
        eventRepository.deleteAll();
        LocalDateTime now = LocalDateTime.now();
        musicEventId = eventRepository.save(new Event(
                "USYD Music Society Open Mic", "Weekly open mic night.",
                "https://example.com/img1.jpg", now.plusDays(3),
                "Hermann's Bar", "Sydney", "USYD", "USYD Music Society",
                OrganiserType.SOCIETY, "Music", List.of("live-music"))).getId();
        careersEventId = eventRepository.save(new Event(
                "Engineering Careers Fair", "Meet graduate recruiters.",
                "https://example.com/img2.jpg", now.plusDays(5),
                "Chau Chak Wing Building", "Sydney", "USYD", "Faculty of Engineering",
                OrganiserType.FACULTY, "Careers", List.of("networking"))).getId();
        eventRepository.save(new Event(
                "Sydney Night Noodle Markets", "Street food market.",
                "https://example.com/img3.jpg", now.plusDays(2),
                "Hyde Park", "Sydney", null, "Time Out Sydney",
                OrganiserType.OTHER, "Food & Drink", List.of("market")));
        eventRepository.save(new Event(
                "Old Welcome Bazaar", "Already happened.",
                "https://example.com/img4.jpg", now.minusDays(10),
                "Eastern Avenue", "Sydney", "USYD", "USU",
                OrganiserType.SOCIETY, "Community", List.of("welcome-week")));
    }

    @Test
    void listEvents_excludesPastEventsByDefault() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void listEvents_includePastTrue_returnsAllFour() throws Exception {
        mockMvc.perform(get("/api/events").param("includePast", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void listEvents_resultsAreSortedChronologically() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sydney Night Noodle Markets"))
                .andExpect(jsonPath("$[1].id").value(musicEventId))
                .andExpect(jsonPath("$[2].id").value(careersEventId));
    }

    @Test
    void listEvents_filterByDate() throws Exception {
        String targetDate = LocalDateTime.now().plusDays(3).toLocalDate().toString();
        mockMvc.perform(get("/api/events").param("date", targetDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(musicEventId));
    }

    @Test
    void listEvents_scopeUsyd_returnsOnlyUsydEvents() throws Exception {
        mockMvc.perform(get("/api/events").param("scope", "USYD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listEvents_filterByOrganiserType() throws Exception {
        mockMvc.perform(get("/api/events").param("organiserType", "FACULTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(careersEventId));
    }

    @Test
    void listEvents_filterByCategory() throws Exception {
        mockMvc.perform(get("/api/events").param("category", "Music"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(musicEventId));
    }

    @Test
    void listEvents_combinedScopeAndOrganiserType() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("scope", "USYD")
                        .param("organiserType", "SOCIETY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(musicEventId));
    }

    @Test
    void getEvent_returnsMatchingEvent() throws Exception {
        mockMvc.perform(get("/api/events/{id}", musicEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("USYD Music Society Open Mic"))
                .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    void getEvent_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/events/{id}", 999999))
                .andExpect(status().isNotFound());
    }
}
