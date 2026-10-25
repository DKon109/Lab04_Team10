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

    private static final String YORK_ID =
            "usyd-seymour-york";

    private static final String YORK_NAME =
            "Seymour Centre - York Theatre";

    private static final String YORK_SOURCE_URL =
            "https://www.seymourcentre.com/media/vyfjqbae/"
                    + "york-theatre-seating-plan.pdf";

    private record YorkRowBlock(
            String rowLabel,
            String section,
            int firstSeat,
            int lastSeat,
            double firstSeatX,
            double positionY
    ) {
    }

    private static final List<YorkRowBlock> YORK_ROW_BLOCKS = List.of(
            // Application block names and coordinates are schematic.
            new YorkRowBlock("A", "Block1", 1, 9, 0.0, 0.0),
            new YorkRowBlock("A", "Block2", 13, 19, 300.0, 0.0),
            new YorkRowBlock("A", "Block3", 34, 40, 800.0, 0.0),
            new YorkRowBlock("A", "Block4", 57, 63, 1300.0, 0.0),
            new YorkRowBlock("A", "Block5", 78, 86, 1800.0, 0.0),

            new YorkRowBlock("B", "Block1", 1, 10, 0.0, 40.0),
            new YorkRowBlock("B", "Block2", 13, 20, 300.0, 40.0),
            new YorkRowBlock("B", "Block3", 34, 41, 800.0, 40.0),
            new YorkRowBlock("B", "Block4", 57, 64, 1300.0, 40.0),
            new YorkRowBlock("B", "Block5", 78, 87, 1800.0, 40.0),

            new YorkRowBlock("C", "Block1", 1, 10, 0.0, 80.0),
            new YorkRowBlock("C", "Block2", 13, 22, 300.0, 80.0),
            new YorkRowBlock("C", "Block3", 34, 43, 800.0, 80.0),
            new YorkRowBlock("C", "Block4", 57, 66, 1300.0, 80.0),
            new YorkRowBlock("C", "Block5", 78, 87, 1800.0, 80.0),

            new YorkRowBlock("D", "Block1", 1, 11, 0.0, 120.0),
            new YorkRowBlock("D", "Block2", 13, 23, 300.0, 120.0),
            new YorkRowBlock("D", "Block3", 34, 44, 800.0, 120.0),
            new YorkRowBlock("D", "Block4", 57, 67, 1300.0, 120.0),
            new YorkRowBlock("D", "Block5", 78, 88, 1800.0, 120.0),

            new YorkRowBlock("E", "Block1", 1, 11, 0.0, 160.0),
            new YorkRowBlock("E", "Block2", 13, 24, 300.0, 160.0),
            new YorkRowBlock("E", "Block3", 34, 45, 800.0, 160.0),
            new YorkRowBlock("E", "Block4", 57, 68, 1300.0, 160.0),
            new YorkRowBlock("E", "Block5", 78, 88, 1800.0, 160.0),

            new YorkRowBlock("F", "Block1", 1, 11, 0.0, 200.0),
            new YorkRowBlock("F", "Block2", 13, 26, 300.0, 200.0),
            new YorkRowBlock("F", "Block3", 34, 46, 800.0, 200.0),
            new YorkRowBlock("F", "Block4", 57, 70, 1300.0, 200.0),
            new YorkRowBlock("F", "Block5", 78, 88, 1800.0, 200.0),

            new YorkRowBlock("G", "Block1", 1, 11, 0.0, 240.0),
            new YorkRowBlock("G", "Block2", 13, 27, 300.0, 240.0),
            new YorkRowBlock("G", "Block3", 34, 48, 800.0, 240.0),
            new YorkRowBlock("G", "Block4", 57, 71, 1300.0, 240.0),
            new YorkRowBlock("G", "Block5", 78, 88, 1800.0, 240.0),

            new YorkRowBlock("H", "Block1", 1, 11, 0.0, 280.0),
            new YorkRowBlock("H", "Block2", 13, 28, 300.0, 280.0),
            new YorkRowBlock("H", "Block3", 34, 49, 800.0, 280.0),
            new YorkRowBlock("H", "Block4", 57, 73, 1300.0, 280.0),
            new YorkRowBlock("H", "Block5", 78, 88, 1800.0, 280.0),

            new YorkRowBlock("J", "Block1", 1, 11, 0.0, 320.0),
            new YorkRowBlock("J", "Block2", 13, 30, 300.0, 320.0),
            new YorkRowBlock("J", "Block3", 34, 51, 800.0, 320.0),
            new YorkRowBlock("J", "Block4", 57, 74, 1300.0, 320.0),
            new YorkRowBlock("J", "Block5", 78, 88, 1800.0, 320.0),

            new YorkRowBlock("K", "Block1", 1, 11, 0.0, 360.0),
            new YorkRowBlock("K", "Block2", 13, 32, 300.0, 360.0),
            new YorkRowBlock("K", "Block3", 34, 53, 800.0, 360.0),
            new YorkRowBlock("K", "Block4", 57, 76, 1300.0, 360.0),
            new YorkRowBlock("K", "Block5", 78, 88, 1800.0, 360.0),

            new YorkRowBlock("L", "Block1", 1, 12, 0.0, 400.0),
            new YorkRowBlock("L", "Block2", 13, 33, 300.0, 400.0),
            new YorkRowBlock("L", "Block3", 34, 54, 800.0, 400.0),
            new YorkRowBlock("L", "Block4", 57, 77, 1300.0, 400.0),
            new YorkRowBlock("L", "Block5", 78, 89, 1800.0, 400.0),

            new YorkRowBlock("M", "Block1", 1, 12, 0.0, 440.0),
            new YorkRowBlock("M", "Block2", 13, 33, 300.0, 440.0),
            new YorkRowBlock("M", "Block3", 34, 56, 800.0, 440.0),
            new YorkRowBlock("M", "Block4", 57, 77, 1300.0, 440.0),
            new YorkRowBlock("M", "Block5", 78, 89, 1800.0, 440.0)
    );

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

        var yorkElements = createYorkRows();
        int yorkCapacity = yorkElements.stream()
                .mapToInt(element -> element.seats().size())
                .sum();

        var  yorkMap = new SeatMapRequest(
                YORK_NAME,
                false,
                true,
                false,
                Map.of("STANDARD", yorkCapacity),
                yorkElements
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
                ),

                new CampusVenueTemplateResponse(
                        YORK_ID,
                        YORK_NAME,
                        YORK_SOURCE_URL,
                        yorkMap
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

    private SeatMapRequest.ElementRequest createYorkRowBlock(
            YorkRowBlock block,
            int rowIndex
    ) {
        var seats = new ArrayList<SeatMapRequest.SeatRequest>();

        for (int number = block.firstSeat();
        number <= block.lastSeat(); number++){
            double positionX = block.firstSeatX() + (number - block.firstSeat()) * 20.0;

            seats.add(createSeat(
                    block.section(),
                    block.rowLabel(),
                    rowIndex,
                    number,
                    positionX,
                    block.positionY(),
                    false
            ));
        }

        return new SeatMapRequest.ElementRequest(
                "york-" + block.section() + "-" + block.rowLabel() + "-" + block.firstSeat(),
                SeatingElementType.ROW,
                block.rowLabel() + " - " + block.section(),
                "Schematic block layout; venue angles and dimensions "
                + "are not reproduced. Accessibility is not verified.",
                block.firstSeatX(),
                block.positionY(),
                0,
                rowIndex,
                false,
                List.copyOf(seats)
        );
    }

    private List<SeatMapRequest.ElementRequest> createYorkRows() {
        var rowLabels = YORK_ROW_BLOCKS.stream()
                .map(YorkRowBlock::rowLabel)
                .distinct()
                .toList();

        var elements = new ArrayList<SeatMapRequest.ElementRequest>();

        for (var block : YORK_ROW_BLOCKS) {
            int rowIndex = rowLabels.indexOf(block.rowLabel());
            elements.add(createYorkRowBlock(block, rowIndex));
        }

        return List.copyOf(elements);
    }
}
