package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.EventResponse;
import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.entity.EventStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void usesDocumentedCategoryOrganiserAndTagWeights() throws Exception {
        Event liked = event(1L, "Liked", "Technology", "Coding Society", "USYD",
                LocalDateTime.now().plusDays(1), List.of("coding", "social"));
        Event candidate = event(2L, "Candidate", "Technology", "Coding Society", "USYD",
                LocalDateTime.now().plusDays(2), List.of("coding"));

        assertEquals(6, RecommendationService.score(candidate, List.of(liked)));
    }

    @Test
    void preferenceMatchOutranksPopularityAndFiltersPastLikedAndScope() throws Exception {
        EventRepository eventRepository = mock(EventRepository.class);
        LikeService likeService = mock(LikeService.class);
        RecommendationService service = new RecommendationService(eventRepository, likeService);
        String email = "student@uni.sydney.edu.au";

        Event liked = event(1L, "Liked", "Technology", "Coding Society", "USYD",
                LocalDateTime.now().plusDays(1), List.of("coding"));
        Event match = event(2L, "Matching", "Technology", "Coding Society", "USYD",
                LocalDateTime.now().plusDays(5), List.of("coding"));
        Event popular = event(3L, "Popular unrelated", "Games", "Chess Club", "USYD",
                LocalDateTime.now().plusDays(2), List.of("chess"));
        Event past = event(4L, "Past match", "Technology", "Coding Society", "USYD",
                LocalDateTime.now().minusDays(1), List.of("coding"));
        Event outsideScope = event(5L, "Sydney match", "Technology", "Coding Society", null,
                LocalDateTime.now().plusDays(1), List.of("coding"));

        when(likeService.likedEventEntities(email)).thenReturn(List.of(liked));
        when(likeService.likeCounts()).thenReturn(Map.of(3L, 100L));
        when(eventRepository.findAll())
                .thenReturn(List.of(past, liked, outsideScope, popular, match));

        List<String> titles = service.recommendations(email, "USYD").stream()
                .map(EventResponse::getTitle).toList();

        assertEquals(List.of("Matching", "Popular unrelated"), titles);
    }

    @Test
    void coldStartUsesPopularityThenDate() throws Exception {
        EventRepository eventRepository = mock(EventRepository.class);
        LikeService likeService = mock(LikeService.class);
        RecommendationService service = new RecommendationService(eventRepository, likeService);
        String email = "new@uni.sydney.edu.au";
        Event soon = event(2L, "Soon", "Arts", "A", "USYD",
                LocalDateTime.now().plusDays(1), List.of());
        Event popular = event(3L, "Popular", "Games", "B", "USYD",
                LocalDateTime.now().plusDays(5), List.of());

        Event past = event(4L, "Past", "Arts", "A", "USYD",
                LocalDateTime.now().minusDays(1), List.of());
        when(likeService.likedEventEntities(email)).thenReturn(List.of());
        when(likeService.likeCounts()).thenReturn(Map.of(2L, 2L, 3L, 20L));
        when(eventRepository.findAll()).thenReturn(List.of(past, soon, popular));

        assertEquals(List.of("Popular", "Soon"), service.recommendations(email, "USYD").stream()
                .map(EventResponse::getTitle).toList());
    }

    @Test
    void scoringIgnoresCaseAndCountsEachSharedTagOnce() throws Exception {
        Event liked = event(1L, "Liked", "TECHNOLOGY", "CODING SOCIETY", "USYD",
                LocalDateTime.now().plusDays(1), List.of("CODING", "coding"));
        Event candidate = event(2L, "Candidate", "technology", "coding society", "USYD",
                LocalDateTime.now().plusDays(2), List.of("coding", "CODING"));
        assertEquals(6, RecommendationService.score(candidate, List.of(liked)));
    }

    @Test
    void equalScoresUsePopularityThenDateThenId() throws Exception {
        EventRepository events = mock(EventRepository.class);
        LikeService likes = mock(LikeService.class);
        RecommendationService service = new RecommendationService(events, likes);
        LocalDateTime date = LocalDateTime.now().plusDays(5);
        Event liked = event(1L, "Liked", "Arts", "A", "USYD", date, List.of());
        Event first = event(2L, "First", "Arts", "B", "USYD", date, List.of());
        Event second = event(3L, "Second", "Arts", "B", "USYD", date, List.of());
        Event later = event(4L, "Later", "Arts", "B", "USYD", date.plusDays(1), List.of());
        Event lessPopular = event(5L, "Less popular", "Arts", "B", "USYD", date.minusDays(1), List.of());
        when(likes.likedEventEntities("student")).thenReturn(List.of(liked));
        when(likes.likeCounts()).thenReturn(Map.of(2L, 10L, 3L, 10L, 4L, 10L, 5L, 1L));
        when(events.findAll()).thenReturn(List.of(lessPopular, later, second, first, liked));

        assertEquals(List.of(2L, 3L, 4L, 5L), service.recommendations("student", "USYD")
                .stream().map(EventResponse::getId).toList());
    }

    @Test
    void sydneyScopeIncludesCommunityEventsAndExcludesOtherCities() throws Exception {
        EventRepository events = mock(EventRepository.class);
        LikeService likes = mock(LikeService.class);
        RecommendationService service = new RecommendationService(events, likes);
        Event community = event(1L, "Community", "Arts", "A", null,
                LocalDateTime.now().plusDays(1), List.of());
        Event elsewhere = new Event("Melbourne", "d", null, LocalDateTime.now().plusDays(2),
                "venue", "Melbourne", null, "A", OrganiserType.OTHER, "Arts", List.of());
        elsewhere.setStatus(EventStatus.PUBLISHED);
        when(events.findAll()).thenReturn(List.of(elsewhere, community));
        when(likes.likedEventEntities("student")).thenReturn(List.of());
        when(likes.likeCounts()).thenReturn(Map.of());

        for (String scope : new String[] {null, "", "ALL", "sydney"}) {
            assertEquals(List.of("Community"), service.recommendations("student", scope)
                    .stream().map(EventResponse::getTitle).toList());
        }
    }

    private Event event(Long id, String title, String category, String organiser, String university,
                        LocalDateTime dateTime, List<String> tags) throws Exception {
        Event event = new Event(title, "description", null, dateTime, "venue", "Sydney",
                university, organiser, OrganiserType.SOCIETY, category, tags);
        Field idField = Event.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(event, id);
        event.setStatus(EventStatus.PUBLISHED);
        return event;
    }
}
