package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.entity.EventRegistrationStatus;

public record CloseRegistrationResponse(Long eventId, EventRegistrationStatus registrationStatus, long confirmedBookings, long groups, boolean readyForAllocation){
}
