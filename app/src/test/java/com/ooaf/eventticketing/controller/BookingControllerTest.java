package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.LoginResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.BookingRepository;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BookingControllerTest{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    private Long eventId;
    private String studentToken;
    private String secondStudentToken;
    private String committeeToken;

    @BeforeEach
    void seed() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        Event event = new Event("USYD Coding Society Hackathon Kickoff", "Team formation and problem statement reveal.", "https://example.com/hackathon.jpg",
                LocalDateTime.now().plusDays(5), "Susan Wakil Building", "Sydney", "USYD", "USYD Coding Society",
                OrganiserType.SOCIETY, "Technology", List.of("hackathon", "coding"));
        event.setCapacity(2);
        eventId = eventRepository.saveAndFlush(event).getId();
        studentToken = tokenFor(
                "booking.student@uni.sydney.edu.au",
                UserRole.STUDENT
        );
        secondStudentToken = tokenFor(
                "booking.friend@uni.sydney.edu.au",
                UserRole.STUDENT
        );
        committeeToken = tokenFor(
                "committee.member@uni.sydney.edu.au",
                UserRole.COMMITTEE_MEMBER,
                "USYD Coding Society"
        );
    }
    @Test
    void authenticatedUserCanBookEvent() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessibilityRequired": true,
                                  "idempotencyKey": "book-001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.bookingRef").isNotEmpty())
                .andExpect(jsonPath("$.accessibilityRequired").value(true))
                .andExpect(jsonPath("$.status").value("PENDING_ALLOCATION"));
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSameBooking() throws Exception {
        String body = """
                {
                  "idempotencyKey": "retry-001"
                }
                """;

        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingRef").isNotEmpty());

        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingRef").isNotEmpty());

        mockMvc.perform(get("/api/auth/me/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void duplicateBookingBySameUserIsRejected() throws Exception {
        createBooking(studentToken, "first-booking", false, null);

        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "second-booking"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void usersCanCreateAndJoinFriendGroup() throws Exception {
        String groupCode = createBooking(
                studentToken,
                "group-owner",
                true,
                null
        );

        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondStudentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "joinGroupCode": "%s",
                                  "idempotencyKey": "group-joiner"
                                }
                                """.formatted(groupCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupCode").value(groupCode));
    }

    @Test
    void bookingIsRejectedWhenCapacityIsFull() throws Exception {
        createBooking(studentToken, "capacity-one", false, null);
        createBooking(secondStudentToken, "capacity-two", false, null);

        String thirdToken = tokenFor("late.student@uni.sydney.edu.au", UserRole.STUDENT);

        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(thirdToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "capacity-three"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void closeRegistrationBlocksNewBookings() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/close-registration", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(committeeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationStatus")
                        .value("CLOSED"))
                .andExpect(jsonPath("$.readyForAllocation")
                        .value(true));

        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "after-close"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void allocationExportIsPrivateAndRequiresClosedRegistration()
            throws Exception {
        createBooking(studentToken, "export-booking", true, null);

        mockMvc.perform(get("/api/events/{eventId}/bookings/export", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(committeeToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/events/{eventId}/close-registration", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(committeeToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/{eventId}/bookings/export", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(committeeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingRef").isNotEmpty())
                .andExpect(jsonPath("$[0].groupCode").isNotEmpty())
                .andExpect(jsonPath("$[0].status")
                        .value("PENDING_ALLOCATION"))
                .andExpect(content().string(not(containsString(
                        "booking.student@uni.sydney.edu.au"
                ))));
    }

    @Test
    void committeeMemberCannotManageBookingsForEventFromAnotherSociety()
            throws Exception {
        createBooking(studentToken, "cross-society-booking", true, null);
        String otherSocietyCommitteeToken = tokenFor(
                "music.committee@uni.sydney.edu.au",
                UserRole.COMMITTEE_MEMBER,
                "USYD Music Society"
        );

        mockMvc.perform(get("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherSocietyCommitteeToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events/{eventId}/close-registration", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherSocietyCommitteeToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/events/{eventId}/bookings/export", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherSocietyCommitteeToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void committeeMemberWithoutSocietyCannotManageEventBookings()
            throws Exception {
        createBooking(studentToken, "unassigned-society-booking", true, null);
        String unassignedCommitteeToken = tokenFor(
                "unassigned.committee@uni.sydney.edu.au",
                UserRole.COMMITTEE_MEMBER
        );

        mockMvc.perform(get("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unassignedCommitteeToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotCloseRegistration() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/close-registration", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedBookingIsRejected() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "no-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String createBooking(String token, String idempotencyKey, boolean createGroup, String joinGroupCode) throws Exception {
        String groupLine = joinGroupCode == null ? "" : "\"joinGroupCode\": \"" + joinGroupCode + "\",";
        String response = mockMvc.perform(post("/api/events/{eventId}/bookings", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                   {
                                      "createGroup": %s,
                                       %s
                                      "idempotencyKey": "%s"
                                   }
                                 """.formatted(createGroup, groupLine, idempotencyKey)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int groupCodeIndex = response.indexOf("\"groupCode\":\"");
        if (groupCodeIndex < 0) {
            return null;
        }
        int start = groupCodeIndex + "\"groupCode\":\"".length();
        int end = response.indexOf("\"", start);
        return response.substring(start, end);
    }

    private String tokenFor(String email, UserRole role) {
        return tokenFor(email, role, null);
    }

    private String tokenFor(String email, UserRole role, String societyName) {
        User user = new User(email, passwordEncoder.encode("StrongPassword123!"), role, societyName);
        User savedUser = userRepository.saveAndFlush(user);
        LoginResponse loginResponse = tokenService.issueToken(savedUser);
        return loginResponse.token();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
