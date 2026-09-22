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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

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
    private String unassignedOrganiserToken;
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
                "organiser@test.local", passwordEncoder.encode("password123"), UserRole.COMMITTEE_MEMBER,
                "Coding Society"));
        User unassignedOrganiser = userRepository.save(new User(
                "unassigned.organiser@test.local", passwordEncoder.encode("password123"), UserRole.COMMITTEE_MEMBER));
        studentToken = tokenService.issueToken(student).token();
        organiserToken = tokenService.issueToken(organiser).token();
        unassignedOrganiserToken = tokenService.issueToken(unassignedOrganiser).token();

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
                .andExpect(jsonPath("$.city").value("Sydney"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.organiserName").value("Coding Society"));

        Event created = eventRepository.findAll().stream()
                .filter(event -> "Trivia Night".equals(event.getTitle()))
                .findFirst()
                .orElseThrow();

        assertEquals("organiser@test.local", created.getOwner().getEmail());
        assertEquals(EventStatus.DRAFT, created.getStatus());
    }

    @Test
    void organiserCannotCreateEventForAnotherSociety() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()
                                .replace("Coding Society", "Other Society")))
                .andExpect(status().isForbidden());
    }

    @Test
    void organiserWithoutSocietyCannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + unassignedOrganiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());
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

    @Test
    void organiserCanFilterOwnEventsByStatus() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        // Prepare a draft and a published event owned by the caller.
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.DRAFT);
        eventRepository.saveAndFlush(techEvent);

        similarTechEvent.setOwner(owner);
        similarTechEvent.setStatus(EventStatus.PUBLISHED);
        eventRepository.saveAndFlush(similarTechEvent);

        // Prepare another organiser's draft.
        User otherOwner = userRepository.save(new User(
                "filter-other@test.local",
                passwordEncoder.encode("password123"),
                UserRole.COMMITTEE_MEMBER
        ));

        unrelatedEvent.setOwner(otherOwner);
        unrelatedEvent.setStatus(EventStatus.DRAFT);
        eventRepository.saveAndFlush(unrelatedEvent);

        // Request only the caller's draft events.
        mockMvc.perform(get("/api/organiser/events")
                .param("status", "draft")
                .header("Authorization", "Bearer " + organiserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(techEvent.getId()))
                .andExpect(jsonPath("$[0].status").value("draft"));
    }

    @Test
    void organiserEventsRejectInvalidStatus() throws Exception {
        // Reject an unsupported event status.
        mockMvc.perform(get("/api/organiser/events")
                .param("status", "unknown")
                        .header("Authorization", "Bearer " + organiserToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Status must be draft, published, or closed"));
    }

    @Test
    void organiserCanPublishOwnDraftEvent() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        // Prepare a future draft owned by the caller.
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.DRAFT);
        techEvent.setDateTime(LocalDateTime.now().plusDays(5));
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Publish the event through the API.
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                .header("Authorization", "Bearer " + organiserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "published"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.status").value("published"));

        // Reload from the database to verify persistence.
        entityManager.flush();
        entityManager.clear();

        Event published = eventRepository.findById(eventId).orElseThrow();
        assertEquals(EventStatus.PUBLISHED, published.getStatus());
        assertEquals(owner.getId(), published.getOwner().getId());
    }

    @Test
    void organiserCanCloseOwnPublishedEvent() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        // Prepare a published event whose date has already passed.
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.PUBLISHED);
        techEvent.setDateTime(LocalDateTime.now().minusDays(1));
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Close the event through the API.
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
        .header("Authorization", "Bearer " + organiserToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"status": "closed"}
        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.status").value("closed"));

        // Reload from the database to verify persistence.
        entityManager.flush();
        entityManager.clear();

        Event closed = eventRepository.findById(eventId).orElseThrow();
        assertEquals(EventStatus.CLOSED, closed.getStatus());
        assertEquals(owner.getId(), closed.getOwner().getId());
    }

    @Test
    void organiserCannotPublishAnotherOrganiserEvent() throws Exception {
        User otherOwner = userRepository.save(new User(
                "publish-other@test.local",
                passwordEncoder.encode("password123"),
                UserRole.COMMITTEE_MEMBER
        ));

        // Assign the draft to a different organiser.
        techEvent.setOwner(otherOwner);
        techEvent.setStatus(EventStatus.DRAFT);
        techEvent.setDateTime(LocalDateTime.now().plusDays(5));
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Attempt to publish using the original organiser's token.
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                .header("Authorization", "Bearer " + organiserToken)
        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "published"}
                        """))
                .andExpect(status().isForbidden());

        // Verify that the rejected request did not change the event.
        entityManager.flush();
        entityManager.clear();

        Event unchanged = eventRepository.findById(eventId).orElseThrow();
        assertEquals(EventStatus.DRAFT, unchanged.getStatus());
        assertEquals(otherOwner.getId(), unchanged.getOwner().getId());
    }

    @Test
    void organiserCannotPublishPastDraftEvent() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        // Prepare an owned draft whose event date has passed.
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.DRAFT);
        techEvent.setDateTime(LocalDateTime.now().minusDays(1));
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Reject publication after the event date.
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
        .header("Authorization", "Bearer " + organiserToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                    {"status": "published"}
        """))
                .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Event date must be in the future before publishing"));

        // Verify that the event remains a draft.
        entityManager.flush();
        entityManager.clear();

        Event unchanged = eventRepository.findById(eventId).orElseThrow();
        assertEquals(EventStatus.DRAFT, unchanged.getStatus());
    }

    @Test
    void organiserCannotCloseDraftEvent() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        // Prepare a draft owned by the caller.
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.DRAFT);
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Reject a direct transition from draft to closed.
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"status": "closed"}
        """))
                .andExpect(status().isConflict());

        // Verify that the rejected transition leaves the draft unchanged.
        entityManager.flush();
        entityManager.clear();

        Event unchanged = eventRepository.findById(eventId).orElseThrow();
        assertEquals(EventStatus.DRAFT, unchanged.getStatus());
    }

    @Test
    void organiserCannotRepublishClosedEvent() throws Exception {
        User owner = userRepository.findByEmail("organiser@test.local")
                .orElseThrow();

        // Prepare a closed event with a future date.
        techEvent.setOwner(owner);
        techEvent.setStatus(EventStatus.CLOSED);
        techEvent.setDateTime(LocalDateTime.now().plusDays(5));
        Long eventId = eventRepository.saveAndFlush(techEvent).getId();

        // Reject reopening a closed event.
        mockMvc.perform(patch("/api/events/{eventId}/status", eventId)
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"status": "published"}
        """))
                .andExpect(status().isConflict());

        // Verify that the event remains closed.
        entityManager.flush();
        entityManager.clear();

        Event unchanged = eventRepository.findById(eventId).orElseThrow();
        assertEquals(EventStatus.CLOSED, unchanged.getStatus());
    }

    @Test
    void changingEventStatusRequiresAuthentication() throws Exception {
        // Reject a status change without an authentication token.
        mockMvc.perform(patch("/api/events/{eventId}/status", techEvent.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "published"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotChangeEventStatus() throws Exception {
        // Reject a status change by a student.
        mockMvc.perform(patch("/api/events/{eventId}/status", techEvent.getId())
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"status": "published"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void changingEventStatusRequiresStatus() throws Exception {
        // Reject a request with no status field.
        mockMvc.perform(patch("/api/events/{eventId}/status", techEvent.getId())
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void changingEventStatusRejectsInvalidStatus() throws Exception {
        // Reject an unsupported target status.
        mockMvc.perform(patch("/api/events/{eventId}/status", techEvent.getId())
                        .header("Authorization", "Bearer " + organiserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"status": "unknown"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").value(
                        "Status must be published or closed"));
    }


    private String validEventJson() {
        return """
                {
                  "title": "Trivia Night",
                  "description": "Weekly trivia",
                  "dateTime": "%s",
                  "venue": "Manning Bar",
                  "university": "USYD",
                  "organiserName": "Coding Society",
                  "organiserType": "SOCIETY",
                  "category": "Games",
                  "tags": ["trivia"]
                }
                """.formatted(LocalDateTime.now().plusDays(3).withNano(0));
    }
}