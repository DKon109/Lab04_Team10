package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.SeatMapRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;



class CampusVenueTemplateServiceTest {
    private final CampusVenueTemplateService service =
            new CampusVenueTemplateService();

    @Test
    void everestTemplateContains505UniqueSeats() {
        var template = service.listTemplates().stream()
                .filter(item -> "usyd-seymour-everest".equals(item.id()))
                .findFirst()
                .orElseThrow();

        var seats = template.map().elements().stream()
                .flatMap(element -> element.seats().stream())
                .toList();

        assertEquals(505, seats.size());

        long uniqueCodes = seats.stream()
                .map(SeatMapRequest.SeatRequest::code)
                .distinct()
                .count();

        assertEquals(505L, uniqueCodes);
        assertEquals(
                Integer.valueOf(505),
                template.map().ticketTypeCapacities().get("STANDARD")
        );
    }

    @Test
    void everestTemplatePreservesSeatGaps() {
        var template = service.listTemplates().stream()
                .filter(item -> "usyd-seymour-everest".equals(item.id()))
                .findFirst()
                .orElseThrow();

        Set<String> codes = template.map().elements().stream()
                .flatMap(element -> element.seats().stream())
                .map(SeatMapRequest.SeatRequest::code)
                .collect(Collectors.toSet());

        // Row D contains only centre seats 12 to 25.
        Set<String> rowD = codes.stream()
                .filter(code -> code.startsWith("D"))
                .collect(Collectors.toSet());

        assertEquals(14, rowD.size());
        for (int number = 12; number <= 25; number++) {
            assertTrue(rowD.contains("D" + number));
        }

        // The sound booth replaces seats 17 to 21 in rows S and T.
        for (String row : Set.of("S", "T")) {
            assertTrue(codes.contains(row + "16"));
            assertTrue(codes.contains(row + "22"));

            for (int number = 17; number <= 21; number++) {
                assertFalse(codes.contains(row + number));
            }
        }

        // These row labels are absent from the seating plan.
        assertFalse(codes.stream().anyMatch(code ->
                code.startsWith("I")
                        || code.startsWith("O")
                        || code.startsWith("Q")));
    }

    @Test
    void assumedAccessibleSeatsAreLabelledAndRemainUnconfirmed() {
        var template = service.listTemplates().stream()
                .filter(item -> "usyd-seymour-everest".equals(item.id()))
                .findFirst()
                .orElseThrow();

        var accessbleSeats = template.map().elements().stream()
                .flatMap(element -> element.seats().stream())
                .filter(SeatMapRequest.SeatRequest::accessible)
                .toList();

        Set<String> accessibleCodes = accessbleSeats.stream()
                .map(SeatMapRequest.SeatRequest::code)
                .collect(Collectors.toSet());

        assertEquals(Set.of("A12", "A26"), accessibleCodes);

        for (var seat : accessbleSeats) {
            assertEquals(
                    "Assumed accessible position for coursework demonstration; not venue-verified.",
                    seat.note()
            );
        }

        assertEquals(Boolean.FALSE,
                template.map().identifiersConfirmed());
        assertFalse(template.map().attendeesChooseSeats());
    }

}
