package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.FrontStageTemplateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrontStageTemplateServiceTest {

    private final FrontStageTemplateService service =
            new FrontStageTemplateService();

    @Test
    void generatesSeatsWithFlagsAndUsableCapacityDeterministically() {
        var request = new FrontStageTemplateRequest(
                "Front-stage demo",
                2,6,3, List.of("B6"), List.of("A1")
        );

        var map = service.generate(request);
        var seats = map.elements().stream()
                .flatMap(element -> element.seats().stream()).toList();

        assertEquals(12, seats.size());
        assertEquals(12L, seats.stream()
                .map(seat -> seat.code())
                        .distinct()
                        .count());
        assertEquals(Integer.valueOf(11),
                map.ticketTypeCapacities().get("STANDARD"));

        var blockedSeat = seats.stream()
                .filter(seat -> seat.code().equals("B6"))
                .findFirst()
                .orElseThrow();
        assertTrue(blockedSeat.blocked());

        var accessibleSeat = seats.stream()
                .filter(seat -> seat.code().equals("A1"))
                .findFirst()
                .orElseThrow();
        assertTrue(accessibleSeat.accessible());
        assertFalse(accessibleSeat.blocked());

        assertEquals(Boolean.FALSE, map.identifiersConfirmed());
        assertEquals(map, service.generate(request));
    }

    @Test
    void separatesRowSectionsAtTheAisle() {
        var request = new FrontStageTemplateRequest(
                "Front-stage aisle demo",
                1, 6, 3, List.of(), List.of()
        );

        var map = service.generate(request);

        assertEquals(2, map.elements().size());

        var left = map.elements().get(0);
        var right = map.elements().get(1);

        assertNotEquals(left.key(), right.key());

        assertEquals(List.of("A1", "A2", "A3"),
                left.seats().stream().map(seat -> seat.code()).toList());
        assertEquals(List.of("A4", "A5", "A6"),
                right.seats().stream().map(seat -> seat.code()).toList());

        assertTrue(left.seats().stream().allMatch(seat -> seat.section().equals("Left")));
        assertTrue(right.seats().stream().allMatch(seat -> seat.section().equals("Right")));

        double normalGap = left.seats().get(1).positionX() - left.seats().get(0).positionX();
        double aisleGap = right.seats().get(0).positionX() - left.seats().get(2).positionX();

        assertTrue(aisleGap > normalGap);
    }

    @Test
    void rejectsInvalidDimensionsAndAislePositions() {
        var invalidRequests = List.of(
                new FrontStageTemplateRequest(
                        "Zero rows", 0, 6, 0, List.of(), List.of()),
                new FrontStageTemplateRequest(
                        "Too many rows", 21, 6, 0, List.of(), List.of()),
                new FrontStageTemplateRequest(
                        "Zero seats", 2, 0, 0, List.of(), List.of()),
                new FrontStageTemplateRequest(
                        "Too many columns", 2, 21, 0, List.of(), List.of()),
                new FrontStageTemplateRequest(
                        "Over capacity", 20, 16, 0, List.of(), List.of()),
                new FrontStageTemplateRequest(
                        "Negative aisle", 2, 6, -1, List.of(), List.of()),
                new FrontStageTemplateRequest(
                        "Aisle outside row", 2, 6, 6, List.of(), List.of())
        );

        for (var request : invalidRequests) {
            var error = assertThrows(
                    ResponseStatusException.class,
                    () -> service.generate(request),
                    request.name()
            );

            assertEquals(
                    HttpStatus.BAD_REQUEST,
                    error.getStatusCode(),
                    request.name()
            );
        }
    }

    @Test
    void rejectsInvalidSeatCodesAndFlags() {
        var invalidRequests = List.of(
                new FrontStageTemplateRequest(
                        "Unknown blocked seat", 1, 3, 0, List.of("A4"), List.of()),
                new FrontStageTemplateRequest(
                        "Unknown accessible seat", 1, 3, 0, List.of(), List.of("B1")),
                new FrontStageTemplateRequest(
                        "Duplicate blocked seat", 1, 3, 0, List.of("A1", "A1"), List.of()),
                new FrontStageTemplateRequest(
                        "Duplicate accessible seat", 1, 3, 0, List.of(), List.of("A1", "A1")),
                new FrontStageTemplateRequest(
                        "Conflicting flags", 1, 3, 0, List.of("A1"), List.of("A1")),
                new FrontStageTemplateRequest(
                        "All seats blocked", 1, 3, 0, List.of("A1", "A2", "A3"), List.of())
        );

        for (var request : invalidRequests) {
            var error = assertThrows(
                    ResponseStatusException.class,
                    () -> service.generate(request),
                    request.name()
            );

            assertEquals(
                    HttpStatus.BAD_REQUEST,
                    error.getStatusCode(),
                    request.name()
            );
        }
    }
}
