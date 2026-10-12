package com.ooaf.eventticketing.dto;

public record MockCheckoutResponse(String paymentRef, MockCheckoutRequest.Outcome outcome, BookingResponse booking) {}
