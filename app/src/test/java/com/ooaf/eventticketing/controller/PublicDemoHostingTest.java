package com.ooaf.eventticketing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "public-demo"})
class PublicDemoHostingTest {
    @Autowired MockMvc mvc;
    @Test void applicationRoutesArePublicButApisStillRequireAuthentication() throws Exception {
        for (String path : new String[]{"/", "/auth", "/events", "/organiser/dashboard", "/bookings/1/ticket"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/api/organiser/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me/bookings")).andExpect(status().isUnauthorized());
    }
    @Test void healthCheckQueriesTheRealDatabase() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
