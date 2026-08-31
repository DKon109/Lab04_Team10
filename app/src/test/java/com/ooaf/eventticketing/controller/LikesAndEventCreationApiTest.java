package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LikesAndEventCreationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    private String studentToken;
    private String organiserToken;
    private Event techEvent;
    private Event similarTechEvent;
    private Event unrelatedEvent;

    @BeforeEach
    void setUp() {
        User student = userRepository.save(new User(
                "liker@uni.sydney.edu.au", passwordEncoder.encode("password123"), UserRole.STUDENT));
        User organiser = userRepository.save(new User(
                "organiser@test.local", passwordEncoder.encode("password123"), UserRole.COMMITTEE_MEMBER));
        studentToken = tokenService.issueToken(student).token();
        organiserToken = tokenService.issueToken(organiser).token();

        LocalDateTime future = LocalDateTime.now().plusDays(5);
        techEvent = eventRepository.save(new Event("Hackathon", "d", null, future,
                "Wakil", "Sydney", "USYD", "Coding Society", OrganiserType.SOCIETY,
                "Technology", List.of("coding", "hackathon")));
        similarTechEvent = eventRepository.save(new Event("AI Workshop", "d", null, future.plusDays(1),
                "Wakil", "Sydney", "USYD", "Coding Society", OrganiserType.SOCIETY,
                "Technology", List.of("coding")));
        unrelatedEvent = eventRepository.save(new Event("Jazz Night", "d", null, future.plusHours(1),
                "Opera House", "Sydney", null, "Opera House", OrganiserType.OTHER,
                "Music", List.of("jazz")));
    }

    @Test
    void likeUnlikeTogglePersistsAndUpdatesCounts() throws Exception {
        mockMvc.perform(post("/api/events/" + techEvent.getId() + "/like")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/" + techEvent.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.likedByMe").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(get("/api/users/me/liked-events")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(techEvent.getId()));

        mockMvc.perform(delete("/api/events/" + techEvent.getId() + "/like")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/" + techEvent.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    void likingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/events/" + techEvent.getId() + "/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forYouRanksSimilarEventsFirstAndExcludesLiked() throws Exception {
        mockMvc.perform(post("/api/events/" + techEvent.getId() + "/like")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        // Similar tech event (same category/organiser/tag) outranks the
        // unrelated one even though the unrelated event is sooner.
        mockMvc.perform(get("/api/events/for-you")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(similarTechEvent.getId()))
                .andExpect(jsonPath("$[1].id").value(unrelatedEvent.getId()));
    }

    @Test
    void forYouRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/events/for-you"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void organiserCanCreateEvent() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Trivia Night"))
                .andExpect(jsonPath("$.city").value("Sydney"));
    }

    @Test
    void createEventValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    private String validEventJson() {
        return """
                {
                  "title": "Trivia Night",
                  "description": "Weekly trivia",
                  "dateTime": "%s",
                  "venue": "Manning Bar",
                  "university": "USYD",
                  "organiserName": "USU",
                  "organiserType": "SOCIETY",
                  "category": "Games",
                  "tags": ["trivia"]
                }
                """.formatted(LocalDateTime.now().plusDays(3).withNano(0));
    }
}
