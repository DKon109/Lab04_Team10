package com.ooaf.eventticketing.dto;

import jakarta.validation.constraints.Size;

/** A null or blank seat code removes the current assignment. */
public record SeatAssignmentRequest(@Size(max = 32) String seatCode) {
}
