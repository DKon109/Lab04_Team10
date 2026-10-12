package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.EventRegistrationStatus;
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
    Integer capacity;
    EventRegistrationStatus registrationStatus;
    boolean seatIdentifiersConfirmed;

    public static EventResponse fromEntity(Event event) {
        return fromEntity(event, false, 0L);
    }

    public static EventResponse fromEntity(Event event, boolean likedByMe, long likeCount) {
        return new EventResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getImageUrl(),
                event.getDateTime(), event.getVenue(), event.getCity(), event.getUniversity(),
                event.getOrganiserName(), event.getOrganiserType(), event.getCategory(),
                event.getTags(), likedByMe, Math.toIntExact(likeCount), event.getCapacity(), event.getRegistrationStatus(), event.isSeatIdentifiersConfirmed());
    }
}
