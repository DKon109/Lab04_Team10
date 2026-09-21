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
import com.ooaf.eventticketing.entity.EventStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.EntityManager;

import javax.print.attribute.standard.Media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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

    @Autowired
    private EntityManager entityManager;

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
        // Repeating a like must still leave exactly one persisted relationship.
        mockMvc.perform(post("/api/events/" + techEvent.getId() + "/like")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message")
                        .value("You do not have permission to perform this action"))
                .andExpect(jsonPath("$.path").value("/api/events"));
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
        Event created = eventRepository.findAll().stream().filter(event ->
                "Trivia Night".equals(event.getTitle())).findFirst().orElseThrow();
        assertEquals("organiser@test.local",
                created.getOwner().getEmail()
        );
        assertEquals(EventStatus.DRAFT, created.getStatus());
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

    @Test
    void organiserEventsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/organiser/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotListOrganiserEvents() throws Exception {
        mockMvc.perform(get("/api/organiser/events")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void organiserCanListOnlyOwnEvents() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        techEvent.setOwner(owner);
        eventRepository.saveAndFlush(techEvent);

        User otherOwner = userRepository.save(new User(
                "other-organiser@test.local",
                passwordEncoder.encode("password123"),
                UserRole.COMMITTEE_MEMBER
        ));

        similarTechEvent.setOwner(otherOwner);
        eventRepository.saveAndFlush(similarTechEvent);

        mockMvc.perform(get("/api/organiser/events")
                        .header("Authorization", "Bearer " + organiserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(techEvent.getId()));
    }

    @Test
    void organiserCanUpdateOwnDraftEvent() throws Exception {
        // Assign the event to the authenticated organiser.
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.DRAFT);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Send a full update using the existing valid request data.
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Trivia Night"))
                .andExpect(jsonPath("$.venue").value("Manning Bar"));

        // Flush changes to the database and clear the persistence context.
        entityManager.flush();
        entityManager.clear();

        // Reload the event and verify its updated values, owner, and status.
        Event updated = eventRepository.findById(eventId).orElseThrow();
        assertEquals("Trivia Night", updated.getTitle());
        assertEquals("Manning Bar", updated.getVenue());
        assertEquals(owner.getId(), updated.getOwner().getId());
        assertEquals(EventStatus.DRAFT, updated.getStatus());
    }

    @Test
    void organiserCannotUpdateAnotherOrganiserEvent() throws Exception {
        // Assign the event to a different organiser.
        User otherOwner = userRepository.save(new User(
                "other-owner@test.local",
                passwordEncoder.encode("password123"),
                UserRole.COMMITTEE_MEMBER
        ));
        techEvent.setOwner(otherOwner);
        techEvent.setStatus(EventStatus.DRAFT);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();
        String originalTitle = techEvent.getTitle();

        // Attempt the update using the original organiser's token.
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());

        // Verify that the rejected request did not change the title.
        assertEquals(
                originalTitle,
                eventRepository.findById(eventId).orElseThrow().getTitle()
        );
    }

    @Test
    void organiserCannotUpdateEventWithoutOwner() throws Exception {
        // Prepare a draft event without an owner.
        techEvent.setOwner(null);
        techEvent.setStatus(EventStatus.DRAFT);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Verify that an organiser cannot edit an unowned event.
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotUpdateEvent()  throws Exception {
        // Send an update request using a student's token.
        mockMvc.perform(put("/api/events/{eventId}", techEvent.getId())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEventJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void organiserCannotUpdatePublishedEvent() throws Exception {
        // Prepare a published event owned by the authenticated organiser.
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.PUBLISHED);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        //Verify that editing a published event is rejected.
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                .header("Authorization", "Bearer " + organiserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEventJson()))
                .andExpect(status().isConflict());
    }

    @Test
    void updateEventRequiresAuthentication() throws Exception {
        // Send an update request without an authentication token.
        mockMvc.perform(put("/api/events/{eventId}", techEvent.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validEventJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void organiserCannotUpdateClosedEvent() throws Exception {
        // Prepare a closed event owned by the authenticated organiser.
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.CLOSED);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        //Verify that editing a closed event is rejected.
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingEventValidatesRequiredFields() throws Exception {
        // Prepare a draft event owned by the authenticated organiser.
        User owner = userRepository.findByEmail("organiser@test.local")
                        .orElseThrow();
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.DRAFT);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Submit an otherwise valid request with an empty title.
        String invalidRequest = validEventJson()
                .replace("\"Trivia Night\"","\"\"");

        mockMvc.perform(put("/api/events/{eventId}", eventId)
        .header("Authorization", "Bearer " + organiserToken)
        .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
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