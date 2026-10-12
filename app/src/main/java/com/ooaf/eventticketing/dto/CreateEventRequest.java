package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.OrganiserType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Payload for an organiser posting a new event via POST /api/events. */
public record CreateEventRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl,

        @NotNull(message = "Date and time are required")
        @Future(message = "Event date must be in the future")
        LocalDateTime dateTime,

        @NotBlank(message = "Venue is required")
        String venue,

        String city,

        String university,

        @NotBlank(message = "Organiser name is required")
        String organiserName,

        @NotNull(message = "Organiser type is required")
        OrganiserType organiserType,

        @NotBlank(message = "Category is required")
        String category,

        List<String> tags,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(300) Integer capacity,
        Boolean seatIdentifiersConfirmed
) {
}
