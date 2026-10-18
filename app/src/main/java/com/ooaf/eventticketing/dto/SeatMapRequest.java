package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.SeatingElementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

public record SeatMapRequest(
        @NotBlank @Size(max = 100) String name,
        boolean attendeesChooseSeats,
        boolean autoMatchTicketCapacities,
        Boolean identifiersConfirmed,
        @NotNull @Size(max = 30) Map<
                @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 _-]{0,39}") String,
                @Min(0) @Max(10000) Integer> ticketTypeCapacities,
        @NotEmpty @Size(max = 100) List<@Valid ElementRequest> elements
) {
    public record ElementRequest(
            @NotBlank @Size(max = 60) String key,
            @NotNull SeatingElementType type,
            @NotBlank @Size(max = 100) String label,
            @Size(max = 300) String note,
            @DecimalMin("-10000") @DecimalMax("10000") double positionX,
            @DecimalMin("-10000") @DecimalMax("10000") double positionY,
            @Min(-360) @Max(360) int rotationDegrees,
            @Min(0) @Max(10000) int displayOrder,
            boolean privateElement,
            @NotNull @Size(max = 500) List<@Valid SeatRequest> seats
    ) {}

    public record SeatRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 ._-]{0,31}") String code,
            @NotBlank @Size(max = 80) String label,
            @Size(max = 300) String note,
            @NotBlank @Size(max = 80) String section,
            @NotBlank @Size(max = 32) String rowLabel,
            @Min(0) @Max(999) int rowIndex,
            @Min(1) @Max(1000) int seatNumber,
            @DecimalMin("-10000") @DecimalMax("10000") double positionX,
            @DecimalMin("-10000") @DecimalMax("10000") double positionY,
            boolean accessible,
            boolean blocked,
            boolean privateSeat,
            @NotNull @Size(max = 30) List<
                    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9 _-]{0,39}") String> ticketTypes
    ) {}
}
