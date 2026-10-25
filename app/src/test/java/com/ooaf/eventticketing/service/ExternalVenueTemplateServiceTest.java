package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.ExternalVenueTemplateRequest;
import com.ooaf.eventticketing.dto.FrontStageTemplateRequest;
import com.ooaf.eventticketing.dto.SeatingLayoutRequest;
import com.ooaf.eventticketing.entity.SeatingLayout;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalVenueTemplateServiceTest {

    private final FrontStageTemplateService frontStageTemplates =
            new FrontStageTemplateService();

    private final ExternalVenueTemplateService service =
            new ExternalVenueTemplateService(frontStageTemplates);

    @Test
    void frontTemplateMatchesExistingFrontStageTemplate() {
        var request = new ExternalVenueTemplateRequest(
                "Front-stage demo",
                SeatingLayoutRequest.Template.FRONT,
                2, 6, 3,
                List.of("B6"),
                List.of("A1")
        );

        var expected = frontStageTemplates.generate(
                new FrontStageTemplateRequest(
                        "Front-stage demo",
                        2, 6, 3,
                        List.of("B6"),
                        List.of("A1")
                )
        );

        var actual = service.generate(request);
        assertEquals(expected, actual);
    }

    @Test
    void fanTemplatePreservesSeatFlagsAndSeparatesSections() {
        var request = new ExternalVenueTemplateRequest(
                "Fan-stage demo",
                SeatingLayoutRequest.Template.FAN,
                2, 6, 3,
                List.of("B6"),
                List.of("A1")
        );

        var map = service.generate(request);
        var seats = map.elements().stream()
                .flatMap(element -> element.seats().stream())
                .toList();

        assertEquals(12, seats.size());
        assertEquals(12L, seats.stream()
                .map(seat -> seat.code()).distinct().count());
        assertEquals(Integer.valueOf(11),
                map.ticketTypeCapacities().get("STANDARD"));
        assertEquals(4, map.elements().size());

        var left = map.elements().get(0).seats();
        var right = map.elements().get(1).seats();

        assertEquals(List.of("A1", "A2", "A3"),
                left.stream().map(seat -> seat.code()).toList());
        assertEquals(List.of("A4", "A5", "A6"),
                right.stream().map(seat -> seat.code()).toList());

        assertTrue(left.stream().allMatch(seat -> seat.section().equals("Left")));
        assertTrue(right.stream().allMatch(seat -> seat.section().equals("Right")));

        var a1 = left.get(0);
        var a2 = left.get(1);
        var a3 = left.get(2);
        var a4 = right.get(0);
        var a6 = right.get(2);

        // Seats in the same row lie on an arc around the stage.
        double radius = Math.hypot(a1.positionX(), a1.positionY());
        for (var seat : left) {
            assertEquals(radius, Math.hypot(seat.positionX(), seat.positionY()),
                    0.000001);
        }

        assertEquals(-a1.positionX(), a6.positionX(), 0.000001);
        assertEquals(a1.positionY(), a6.positionY(), 0.000001);
        assertTrue(a3.positionY() > a1.positionY());

        double normalGap = Math.hypot(
                a2.positionX() - a1.positionX(),
                a2.positionY() - a1.positionY());
        double aisleGap = Math.hypot(
                a4.positionX() - a3.positionX(),
                a4.positionY() - a3.positionY());
        assertTrue(aisleGap > normalGap);

        assertTrue(a1.accessible());
        assertTrue(seats.stream()
                .filter(seat -> seat.code().equals("B6"))
                .findFirst().orElseThrow().blocked());
        assertEquals(Boolean.FALSE, map.identifiersConfirmed());
    }

    @Test
    void centralTemplatePlacesOddRowsBothSidesOfStage() {
        var request = new ExternalVenueTemplateRequest(
                "Central-stage demo",
                SeatingLayoutRequest.Template.CENTRAL,
                3,4,2,
                List.of("C4"),
                List.of("A1")
        );

        var map = service.generate(request);
        var seats = map.elements().stream()
                .flatMap(element -> element.seats().stream())
                .toList();

        assertEquals(12, seats.size());
        assertEquals(12L, seats.stream()
                .map(seat -> seat.code()).distinct().count());
        assertEquals(6, map.elements().size());
        assertEquals(Integer.valueOf(11),
                map.ticketTypeCapacities().get("STANDARD"));

        // With three rows, A and B are north; C is south.
        for (var seat : seats) {
            boolean north = !seat.rowLabel().equals("C");
            String side = north ? "North" : "South";
            String section = seat.seatNumber() <= 2
                    ? "Left" : "Right";

            assertEquals(side + "-" + section, seat.section());
            assertTrue(north
            ? seat.positionY() < 0
                    : seat.positionY() > 0);
        }

        var a1 = seats.stream()
                .filter(seat -> seat.code().equals("A1"))
                .findFirst().orElseThrow();
        var a4 = seats.stream()
                .filter(seat -> seat.code().equals("A4"))
                        .findFirst().orElseThrow();
        var b1 = seats.stream()
                .filter(seat -> seat.code().equals("B1"))
                .findFirst().orElseThrow();
        var c1 = seats.stream()
                .filter(seat -> seat.code().equals("C1"))
                .findFirst().orElseThrow();
        var c4 = seats.stream()
                .filter(seat -> seat.code().equals("C4"))
                .findFirst().orElseThrow();

        // Rows are centred horizontally and separated from the stage.
        assertTrue(a1.positionX() < 0);
        assertEquals(-a1.positionX(), a4.positionX(), 0.000001);
        assertTrue(b1.positionY() < a1.positionY());
        assertEquals(-a1.positionY(), c1.positionY(), 0.000001);

        assertTrue(a1.accessible());
        assertTrue(c4.blocked());
        assertEquals(Boolean.FALSE, map.identifiersConfirmed());
        assertEquals(map, service.generate(request));
    }

    @Test
    void rejectMissingTemplateAndCentralLayoutWithOnlyOneRow() {
        var missingRequest = assertThrows(
                ResponseStatusException.class,
                () -> service.generate(null)
        );
        assertEquals(HttpStatus.BAD_REQUEST,
                missingRequest.getStatusCode());

        var invalidRequests = List.of(
                new ExternalVenueTemplateRequest(
                        "Missing template",
                        null,
                        2,4, 0,
                        List.of(),
                        List.of()
                ),
                new ExternalVenueTemplateRequest(
                        "Central with one row",
                        SeatingLayoutRequest.Template.CENTRAL,
                        1, 4, 0,
                        List.of(),
                        List.of()
                )
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
    void rejectInvalidSeatCodesAndFlagsForEveryTemplate() {
        for (var template : SeatingLayoutRequest.Template.values()) {
            var invalidRequests = List.of(
                    new ExternalVenueTemplateRequest(
                            "Unknown blocked seat", template,
                            2, 3, 0,
                            List.of("A4"),
                            List.of()
                    ),
                    new ExternalVenueTemplateRequest(
                            "Unknown accessible seat", template,
                            2, 3, 0,
                            List.of(), List.of("C1")
                    ),
                    new ExternalVenueTemplateRequest(
                            "Duplicate blocked seat", template,
                            2, 3, 0,
                            List.of("A1", "A1"), List.of()
                    ),
                    new ExternalVenueTemplateRequest(
                            "Duplicate accessible seat", template,
                            2, 3, 0,
                            List.of(), List.of("A1", "A1")
                    ),
                    new ExternalVenueTemplateRequest(
                            "Conflicting flags", template,
                            2, 3, 0,
                            List.of("A1"), List.of("A1")
                    ),
                    new ExternalVenueTemplateRequest(
                            "All seats blocked", template,
                            2, 3, 0,
                            List.of("A1", "A2", "A3", "B1", "B2", "B3"), List.of()
                    )
            );

            for (var request : invalidRequests) {
                String scenario = template + ": " + request.name();

                var error = assertThrows(
                        ResponseStatusException.class,
                        () -> service.generate(request),
                        scenario
                );

                    assertEquals(
                            HttpStatus.BAD_REQUEST,
                            error.getStatusCode(),
                            scenario
                    );
            }
        }
    }
}
