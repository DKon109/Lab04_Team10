package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.EventLike;
import com.ooaf.eventticketing.repository.EventLikeCount;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.EventLikeRepository;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ooaf.eventticketing.entity.EventStatus;

class LikeServiceTest {
    private final EventLikeRepository likes = mock(EventLikeRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LikeService service = new LikeService(likes, events, users);
    private final User user = new User("student@uni.sydney.edu.au", "hash", UserRole.STUDENT);
    private final Event event = mock(Event.class);

    @BeforeEach
    void setUp() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(events.findById(10L)).thenReturn(Optional.of(event));
    }

    @Test
    void repeatedLikeSavesOnlyOnce() {
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(likes.existsByUserAndEvent(user, event)).thenReturn(false, true);
        service.like(user.getEmail(), 10L);
        service.like(user.getEmail(), 10L);
        verify(likes, times(1)).save(any());
    }

    @Test
    void repeatedUnlikeSucceedsWhenRelationshipIsAlreadyGone() {
        when(likes.deleteByUserAndEvent(user, event)).thenReturn(1L, 0L);
        assertDoesNotThrow(() -> {
            service.unlike(user.getEmail(), 10L);
            service.unlike(user.getEmail(), 10L);
        });
        verify(likes, times(2)).deleteByUserAndEvent(user, event);
    }

    @Test
    void unknownEventCannotBeLiked() {
        when(events.findById(10L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.like(user.getEmail(), 10L));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(likes, never()).save(any());
    }

    @Test
    void unknownUserCannotLike() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.like(user.getEmail(), 10L));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(likes);
    }

    @Test
    void likedEventsIncludePersonalStateAndTotalCount() {
        when(event.getId()).thenReturn(10L);
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(likes.findAllByUserEmailWithEvent(user.getEmail()))
                .thenReturn(List.of(new EventLike(user, event)));
        EventLikeCount count = mock(EventLikeCount.class);
        when(count.getEventId()).thenReturn(10L);
        when(count.getLikeCount()).thenReturn(3L);
        when(likes.countLikesByEvent()).thenReturn(List.of(count));

        var result = service.likedEvents(user.getEmail());

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
        assertTrue(result.get(0).isLikedByMe());
        assertEquals(3, result.get(0).getLikeCount());
        assertEquals("published", result.get(0).getStatus());
    }

    @Test
    void anonymousEventMetadataDoesNotQueryPersonalLikes() {
        assertTrue(service.likedEventIds(null).isEmpty());
        assertTrue(service.likedEventIds(" ").isEmpty());
        verifyNoInteractions(likes);
    }
}
