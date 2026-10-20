package com.ooaf.eventticketing.service;

import org.springframework.stereotype.Service;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.entity.SeatingElementType;
import com.ooaf.eventticketing.dto.CampusVenueTemplateResponse;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;

@Service
public class CampusVenueTemplateService {

    private static final String EVEREST_ID =
            "usyd-seymour-everest";

    private static final String EVEREST_NAME =
            "Seymour Centre - Everest Theatre";

    private static final String EVEREST_SOURCE_URL =
            "https://www.seymourcentre.com/media/probtcxn/"
                    + "everest-seating-plan-final.pdf";

    private static final String REGINALD_ID =
            "usyd-seymour-reginald";

    private static final String REGINALD_NAME =
            "Seymour Centre - Reginald Theatre";

    private static final String REGINALD_SOURCE_URL =
            "https://www.seymourcentre.com/media/3rjab23b/"
                    + "reginald-seating-plan.pdf";

    private static final List<String> REGINALD_ROWS =
            List.of("A", "B", "C", "D", "E", "F", "G", "H", "J");

    private record EverestRow(
            String label,
            int rightFirst,
            int rightLast,
            int centreFirst,
            int centreLast,
            int leftFirst,
            int leftLast
    ) {
    }

    private static final List<EverestRow> EVEREST_ROWS = List.of(
            new EverestRow("A", 4, 11, 12, 26, 27, 34),
            new EverestRow("B", 4, 11, 12, 25, 27, 34),
            new EverestRow("C", 4, 11, 12, 26, 27, 34),
            new EverestRow("D", 0, 0, 12, 25, 0, 0),
            new EverestRow("E", 6, 11, 12, 26, 27, 32),
            new EverestRow("F", 4, 11, 12, 26, 27, 34),
            new EverestRow("G", 4, 11, 12, 26, 27, 34),
            new EverestRow("H", 4, 11, 12, 26, 27, 34),
            new EverestRow("J", 4, 11, 12, 26, 27, 34),
            new EverestRow("K", 4, 11, 12, 26, 27, 34),
            new EverestRow("L", 4, 11, 12, 26, 27, 34),
            new EverestRow("M", 4, 11, 12, 26, 27, 34),
            new EverestRow("N", 4, 11, 12, 26, 27, 34),
            new EverestRow("P", 1, 11, 12, 26, 27, 34),
            new EverestRow("R", 1, 11, 12, 26, 27, 34),
            new EverestRow("S", 1, 11, 12, 26, 27, 34),
            new EverestRow("T", 2, 11, 12, 26, 27, 33)
    );

    public List<CampusVenueTemplateResponse> listTemplates() {
        var eventElements = createEverestRows();
        int everestCapacity = eventElements.stream()
                .mapToInt(element -> element.seats().size())
                .sum();

        var everestMap = new SeatMapRequest(
                EVEREST_NAME,
                false,
                true,
                false,
                Map.of("STANDARD", everestCapacity),
                eventElements
        );

        var reginaldElements = createReginaldRows();
        int reginaldCapacity = reginaldElements.stream()
                .mapToInt(element -> element.seats().size())
                .sum();

        var reginaldMap = new SeatMapRequest(
                REGINALD_NAME,
                false,
                true,
                false,
                Map.of("STANDARD", reginaldCapacity),
                reginaldElements
        );

        return List.of(new CampusVenueTemplateResponse(
                EVEREST_ID,
                EVEREST_NAME,
                EVEREST_SOURCE_URL,
                everestMap
        ),
                new CampusVenueTemplateResponse(
                        REGINALD_ID,
                        REGINALD_NAME,
                        REGINALD_SOURCE_URL,
                        reginaldMap
                )
        );
    }

    private List<SeatMapRequest.ElementRequest> createEverestRows() {
        var elements = new ArrayList<SeatMapRequest.ElementRequest>();

        // Assumed accessible positions for coursework demonstration only.
        Set<String> accessibleCodes = Set.of("A12", "A26");

        for (int index = 0; index < EVEREST_ROWS.size(); index++) {
            EverestRow row = EVEREST_ROWS.get(index);
            double positionY = index * 40.0;

            if (row.rightFirst() > 0) {
                double firstSeatX =
                        340.0 + (11 - row.rightFirst()) * 20.0;

                elements.add(createRowBlock(
                        "Right", row.label(), index,
                        row.rightFirst(), row.rightLast(),
                        firstSeatX, positionY, accessibleCodes
                ));
            }

            if ("S".equals(row.label()) || "T".equals(row.label())) {
                // Seats 17 to 21 are absent around the sound booth.
                elements.add(createRowBlock(
                        "Centre", row.label(), index,
                        12, 16,
                        280.0, positionY, accessibleCodes
                ));

                elements.add(createRowBlock(
                        "Centre", row.label(), index,
                        22, 26,
                        80.0, positionY, accessibleCodes
                ));
            } else {
                elements.add(createRowBlock(
                        "Centre", row.label(), index,
                        row.centreFirst(), row.centreLast(),
                        280.0, positionY, accessibleCodes
                ));
            }

            if (row.leftFirst() > 0) {
                elements.add(createRowBlock(
                        "Left", row.label(), index,
                        row.leftFirst(), row.leftLast(),
                        -60.0, positionY, accessibleCodes
                ));
            }
        }

        return List.copyOf(elements);
    }

    private SeatMapRequest.SeatRequest createSeat(
            String section,
            String  rowLabel,
            int rowIndex,
            int seatNumber,
            double positionX,
            double positionY,
            boolean accessible
    ){
        String code = rowLabel + seatNumber;

        String note = accessible
                ? "Assumed accessible position for coursework demonstration; not venue-verified."
                :null;

        return new SeatMapRequest.SeatRequest(
                code,
                code,
                note,
                section,
                rowLabel,
                rowIndex,
                seatNumber,
                positionX,
                positionY,
                accessible,
                false,
                false,
                List.of("STANDARD")
        );
    }

    private SeatMapRequest.ElementRequest createRowBlock(
            String section,
            String rowLabel,
            int rowIndex,
            int firstSeat,
            int lastSeat,
            double firstSeatX,
            double positionY,
            Set<String> accessibleCodes
    ){
        var seats = new ArrayList<SeatMapRequest.SeatRequest>();

        for(int number = firstSeat; number <= lastSeat; number++){
            String code = rowLabel + number;
            double positionX = firstSeatX - (number - firstSeat) * 20.0;

            seats.add(createSeat(
                    section,
                    rowLabel,
                    rowIndex,
                    number,
                    positionX,
                    positionY,
                    accessibleCodes.contains(code)
            ));
        }

        return new SeatMapRequest.ElementRequest(
                section + "-" + rowLabel + "-" + firstSeat,
                SeatingElementType.ROW,
                rowLabel + " - " + section,
                null,
                firstSeatX,
                positionY,
                0,
                rowIndex,
                false,
                List.copyOf(seats)
        );
    }

    private SeatMapRequest.ElementRequest createReginaldRowBlock(
            String section,
            String rowLabel,
            int rowIndex,
            int firstSeat,
            int lastSeat,
            double firstSeatX,
            double positionY
    ) {
        var seats = new ArrayList<SeatMapRequest.SeatRequest>();

        for (int number = firstSeat; number <= lastSeat; number++){
            double positionX = firstSeatX + (number - firstSeat) * 20.0;

            seats.add(createSeat(
                    section,
                    rowLabel,
                    rowIndex,
                    number,
                    positionX,
                    positionY,
                    false
            ));
        }

        return new SeatMapRequest.ElementRequest(
                "reginald-" + section + "-" + rowLabel + "-" + firstSeat,
                SeatingElementType.ROW,
                rowLabel + " - " + section,
                "Schematic coordinates based on the published seating plan; "
                + "physical accessibility is not verified.",
                firstSeatX,
                positionY,
                0,
                rowIndex,
                false,
                List.copyOf(seats)
        );
    }

    private List<SeatMapRequest.ElementRequest> createReginaldRows() {
        var elements = new ArrayList<SeatMapRequest.ElementRequest>();

        for (int index = 0; index < REGINALD_ROWS.size(); index++) {
            String rowLabel = REGINALD_ROWS.get(index);
            double positionY = index * 40.0;

            if ("J".equals(rowLabel)) {
                // The published plan shows a continuous rear row.
                elements.add(createReginaldRowBlock(
                        "Rear", rowLabel, index,
                        1, 20,
                        0.0, positionY
                ));
            } else {
                // Separate row blocks preserve the central aisle.
                elements.add(createReginaldRowBlock(
                        "Left", rowLabel, index,
                        1, 8,
                        20.0, positionY
                ));

                elements.add(createReginaldRowBlock(
                        "Right", rowLabel, index,
                        9, 16,
                        220.0, positionY
                ));
            }
        }
        return List.copyOf(elements);
    }
}
