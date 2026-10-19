package com.ooaf.eventticketing.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record SeatingLayoutRequest(@NotNull Source source, @NotNull Template template, String roomId,
        @Min(1) @Max(20) int rows, @Min(1) @Max(20) int seatsPerRow,
        @Min(0) @Max(19) int aisleAfter, @Size(max = 300) List<@NotBlank @Size(max = 4) String> blockedCodes,
        @Size(max = 300) List<@NotBlank @Size(max = 4) String> accessibleCodes, boolean identifiersConfirmed) {
    public enum Source { EXTERNAL, CAMPUS_DEMO }
    public enum Template { FRONT, FAN, CENTRAL }
}
