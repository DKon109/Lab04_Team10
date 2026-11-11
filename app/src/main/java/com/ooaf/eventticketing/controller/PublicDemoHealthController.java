package com.ooaf.eventticketing.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("public-demo")
public class PublicDemoHealthController {
    private final JdbcTemplate jdbc;
    public PublicDemoHealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/health")
    public Map<String, String> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP");
    }
}
