package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
public class EventResponse {
    Long id;
    String title;
    String description;
    String imageUrl;
    LocalDateTime dateTime;
    String venue;
    String city;
    String university;
    String organiserName;
    OrganiserType organiserType;
    String category;
    List<String> tags;
    boolean likedByMe;
    Integer likeCount;

    public static EventResponse fromEntity(Event event) {
        return new EventResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getImageUrl(),
                event.getDateTime(), event.getVenue(), event.getCity(), event.getUniversity(),
                event.getOrganiserName(), event.getOrganiserType(), event.getCategory(),
                event.getTags(), false, null
        );
    }
}
