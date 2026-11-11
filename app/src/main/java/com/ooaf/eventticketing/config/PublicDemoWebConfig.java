package com.ooaf.eventticketing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serve the existing React router from the same origin as the real API. */
@Configuration
@Profile("public-demo")
public class PublicDemoWebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String path : new String[]{"/", "/auth", "/events", "/events/{id}/book",
                "/bookings", "/bookings/{id}/ticket", "/organiser", "/organiser/dashboard",
                "/organiser/new", "/organiser/events/{id}", "/organiser/events/{id}/edit"}) {
            registry.addViewController(path).setViewName("forward:/index.html");
        }
    }
}
