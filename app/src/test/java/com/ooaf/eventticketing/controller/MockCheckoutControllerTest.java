package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import com.ooaf.eventticketing.service.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MockCheckoutControllerTest {
    @Autowired MockMvc mvc;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired BookingRepository bookings;
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired TokenService tokens;
    @Autowired AuthenticationTokenRepository authenticationTokens;
    Long eventId; String token; String otherToken;

    @BeforeEach void seed() {
        attempts.deleteAll(); bookings.deleteAll(); events.deleteAll(); authenticationTokens.deleteAllInBatch(); users.deleteAll();
        var event = new Event("Checkout demo", "Demo", null, LocalDateTime.now().plusDays(2), "Hall", "Sydney", "USYD", "Society", OrganiserType.SOCIETY, "Social", List.of());
        event.setCapacity(1); event.setStatus(EventStatus.PUBLISHED); event.setSeatIdentifiersConfirmed(true); eventId = events.saveAndFlush(event).getId();
        token = tokens.issueToken(users.saveAndFlush(new User("checkout@uni.sydney.edu.au", "unused", UserRole.STUDENT))).token();
        otherToken = tokens.issueToken(users.saveAndFlush(new User("other@uni.sydney.edu.au", "unused", UserRole.STUDENT))).token();
    }
    ResultActions checkout(String auth, String key, String outcome, String extras) throws Exception {
        return mvc.perform(post("/api/events/{id}/mock-checkout", eventId).header("Authorization", "Bearer " + auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"" + key + "\",\"outcome\":\"" + outcome + "\"" + extras + "}"));
    }
    @Test void successIsAtomicAndIdempotent() throws Exception {
        var first = checkout(token, "success-1", "SUCCESS", ",\"createGroup\":true,\"accessibilityRequired\":true")
                .andExpect(status().isOk()).andExpect(jsonPath("$.booking.status").value("PENDING_ALLOCATION"))
                .andExpect(jsonPath("$.booking.groupCode").isNotEmpty()).andReturn().getResponse().getContentAsString();
        var retry = checkout(token, "success-1", "SUCCESS", ",\"createGroup\":true,\"accessibilityRequired\":true")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(first, retry); assertEquals(1, bookings.count()); assertEquals(1, attempts.count());
    }
    @Test void unsuccessfulOutcomesDoNotConsumeCapacity() throws Exception {
        checkout(token, "decline", "DECLINED", "").andExpect(status().isOk()).andExpect(jsonPath("$.booking").doesNotExist());
        checkout(token, "cancel", "CANCELLED", "").andExpect(status().isOk());
        assertEquals(0, bookings.count()); assertEquals(2, attempts.count());
        checkout(token, "retry-success", "SUCCESS", "").andExpect(status().isOk());
    }
    @Test void changedPayloadOrOwnerCannotReuseKey() throws Exception {
        checkout(token, "used", "DECLINED", "").andExpect(status().isOk());
        checkout(token, "used", "SUCCESS", "").andExpect(status().isConflict());
        checkout(otherToken, "used", "DECLINED", "").andExpect(status().isConflict());
        assertEquals(0, bookings.count());
    }
    @Test void fullEventRejectsAnotherUser() throws Exception {
        checkout(token, "first", "SUCCESS", "").andExpect(status().isOk());
        checkout(otherToken, "second", "SUCCESS", "").andExpect(status().isConflict());
        assertEquals(1, attempts.count());
    }
    @Test void closedEventAndInvalidGroupCannotConfirm() throws Exception {
        checkout(token, "bad-group", "SUCCESS", ",\"joinGroupCode\":\"GRP-NOPE\"").andExpect(status().isNotFound());
        var event = events.findById(eventId).orElseThrow(); event.setRegistrationStatus(EventRegistrationStatus.CLOSED); events.saveAndFlush(event);
        checkout(token, "closed", "SUCCESS", "").andExpect(status().isConflict());
        assertEquals(0, bookings.count()); assertEquals(0, attempts.count());
    }
    @Test void cannotBookPastEvent() throws Exception {
        var event = events.findById(eventId).orElseThrow(); event.setDateTime(LocalDateTime.now().minusDays(1)); events.saveAndFlush(event);
        checkout(token, "past", "SUCCESS", "").andExpect(status().isConflict());
    }
    @Test void groupRequiresConfirmedPhysicalIdentifiers() throws Exception {
        var event = events.findById(eventId).orElseThrow(); event.setSeatIdentifiersConfirmed(false); events.saveAndFlush(event);
        checkout(token, "unmapped", "SUCCESS", ",\"createGroup\":true").andExpect(status().isConflict());
        assertEquals(0, bookings.count());
        checkout(token, "individual", "SUCCESS", "").andExpect(status().isOk());
    }
    @Test void authenticationAndOutcomeAreRequired() throws Exception {
        checkout("invalid", "anon", "SUCCESS", "").andExpect(status().isUnauthorized());
        checkout(token, "invalid", "REAL_PAYMENT", "").andExpect(status().isBadRequest());
        assertEquals(0, attempts.count());
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousCheckoutsCannotOverbook() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var one = pool.submit(() -> { start.await(); return checkout(token, "race-one", "SUCCESS", "").andReturn().getResponse().getStatus(); });
            var two = pool.submit(() -> { start.await(); return checkout(otherToken, "race-two", "SUCCESS", "").andReturn().getResponse().getStatus(); });
            start.countDown();
            var statuses = new java.util.ArrayList<>(List.of(one.get(15, java.util.concurrent.TimeUnit.SECONDS), two.get(15, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
            assertEquals(1, bookings.count()); assertEquals(1, attempts.count());
        } finally {
            pool.shutdownNow(); attempts.deleteAll(); bookings.deleteAll(); events.deleteAll(); authenticationTokens.deleteAllInBatch(); users.deleteAll();
        }
    }
}
