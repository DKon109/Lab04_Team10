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
            int lastSeat
    ) {
    }

    private static final List<YorkRowBlock> YORK_ROW_BLOCKS = List.of(
            // Block names are application labels; seat ranges follow the published plan.
            new YorkRowBlock("A", "Block1", 1, 9),
            new YorkRowBlock("A", "Block2", 13, 19),
            new YorkRowBlock("A", "Block3", 34, 40),
            new YorkRowBlock("A", "Block4", 57, 63),
            new YorkRowBlock("A", "Block5", 78, 86),

            new YorkRowBlock("B", "Block1", 1, 10),
            new YorkRowBlock("B", "Block2", 13, 20),
            new YorkRowBlock("B", "Block3", 34, 41),
            new YorkRowBlock("B", "Block4", 57, 64),
            new YorkRowBlock("B", "Block5", 78, 87),

            new YorkRowBlock("C", "Block1", 1, 10),
            new YorkRowBlock("C", "Block2", 13, 22),
            new YorkRowBlock("C", "Block3", 34, 43),
            new YorkRowBlock("C", "Block4", 57, 66),
            new YorkRowBlock("C", "Block5", 78, 87),

            new YorkRowBlock("D", "Block1", 1, 11),
            new YorkRowBlock("D", "Block2", 13, 23),
            new YorkRowBlock("D", "Block3", 34, 44),
            new YorkRowBlock("D", "Block4", 57, 67),
            new YorkRowBlock("D", "Block5", 78, 88),

            new YorkRowBlock("E", "Block1", 1, 11),
            new YorkRowBlock("E", "Block2", 13, 24),
            new YorkRowBlock("E", "Block3", 34, 45),
            new YorkRowBlock("E", "Block4", 57, 68),
            new YorkRowBlock("E", "Block5", 78, 88),

            new YorkRowBlock("F", "Block1", 1, 11),
            new YorkRowBlock("F", "Block2", 13, 26),
            new YorkRowBlock("F", "Block3", 34, 46),
            new YorkRowBlock("F", "Block4", 57, 70),
            new YorkRowBlock("F", "Block5", 78, 88),

            new YorkRowBlock("G", "Block1", 1, 11),
            new YorkRowBlock("G", "Block2", 13, 27),
            new YorkRowBlock("G", "Block3", 34, 48),
            new YorkRowBlock("G", "Block4", 57, 71),
            new YorkRowBlock("G", "Block5", 78, 88),

            new YorkRowBlock("H", "Block1", 1, 11),
            new YorkRowBlock("H", "Block2", 13, 28),
            new YorkRowBlock("H", "Block3", 34, 49),
            new YorkRowBlock("H", "Block4", 57, 73),
            new YorkRowBlock("H", "Block5", 78, 88),

            new YorkRowBlock("J", "Block1", 1, 11),
            new YorkRowBlock("J", "Block2", 13, 30),
            new YorkRowBlock("J", "Block3", 34, 51),
            new YorkRowBlock("J", "Block4", 57, 74),
            new YorkRowBlock("J", "Block5", 78, 88),

            new YorkRowBlock("K", "Block1", 1, 11),
            new YorkRowBlock("K", "Block2", 13, 32),
            new YorkRowBlock("K", "Block3", 34, 53),
            new YorkRowBlock("K", "Block4", 57, 76),
            new YorkRowBlock("K", "Block5", 78, 88),

            new YorkRowBlock("L", "Block1", 1, 12),
            new YorkRowBlock("L", "Block2", 13, 33),
            new YorkRowBlock("L", "Block3", 34, 54),
            new YorkRowBlock("L", "Block4", 57, 77),
            new YorkRowBlock("L", "Block5", 78, 89),

            new YorkRowBlock("M", "Block1", 1, 12),
            new YorkRowBlock("M", "Block2", 13, 33),
            new YorkRowBlock("M", "Block3", 34, 56),
            new YorkRowBlock("M", "Block4", 57, 77),
            new YorkRowBlock("M", "Block5", 78, 89)
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

            if (row.rightFirst() > 0) {
                elements.add(createRowBlock(
                        "Right", row, index,
                        row.rightFirst(), row.rightLast(), accessibleCodes
                ));
            }

            if ("S".equals(row.label()) || "T".equals(row.label())) {
                // Seats 17 to 21 are absent around the sound booth.
                elements.add(createRowBlock(
                        "Centre", row, index, 12, 16, accessibleCodes
                ));

                elements.add(createRowBlock(
                        "Centre", row, index, 22, 26, accessibleCodes
                ));
            } else {
                elements.add(createRowBlock(
                        "Centre", row, index,
                        row.centreFirst(), row.centreLast(), accessibleCodes
                ));
            }

            if (row.leftFirst() > 0) {
                elements.add(createRowBlock(
                        "Left", row, index,
                        row.leftFirst(), row.leftLast(), accessibleCodes
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

    // Everest geometry traced from the published plan (pixels, stage below row A), scaled so seats sit
    // about 20 units apart. The centre block faces the stage; the side blocks slope down towards the
    // outer walls, and row T starts one seat further out on both sides.
    private static final double EVEREST_SCALE = 20.0 / 39.6;

    private static double[] everestSeatPosition(EverestRow row, int rowIndex, String section, int number) {
        double rowY = 888.0 - rowIndex * 42.2;
        int shift = "T".equals(row.label()) ? 1 : 0;
        double x;
        double y = rowY;
        switch (section) {
            case "Right" -> {
                int fromAisle = 11 - number + shift;
                x = 1247.0 + fromAisle * 39.4;
                y = rowY + fromAisle * 8.6;
            }
            case "Left" -> {
                int fromAisle = number - 27 + shift;
                x = 378.0 - fromAisle * 39.4;
                y = rowY + fromAisle * 8.6;
            }
            default -> {
                // 14-seat rows (B and D) span the same width as the 15-seat rows.
                double pitch = (1092.0 - 535.0) / (row.centreLast() - 12);
                x = 535.0 + (row.centreLast() - number) * pitch;
            }
        }
        return new double[] {
                Math.round((x - 60.0) * EVEREST_SCALE * 10.0) / 10.0,
                Math.round((y - 190.0) * EVEREST_SCALE * 10.0) / 10.0
        };
    }

    private SeatMapRequest.ElementRequest createRowBlock(
            String section,
            EverestRow row,
            int rowIndex,
            int firstSeat,
            int lastSeat,
            Set<String> accessibleCodes
    ){
        var seats = new ArrayList<SeatMapRequest.SeatRequest>();
        String rowLabel = row.label();

        for(int number = firstSeat; number <= lastSeat; number++){
            String code = rowLabel + number;
            double[] position = everestSeatPosition(row, rowIndex, section, number);

            seats.add(createSeat(
                    section,
                    rowLabel,
                    rowIndex,
                    number,
                    position[0],
                    position[1],
                    accessibleCodes.contains(code)
            ));
        }

        double[] first = everestSeatPosition(row, rowIndex, section, firstSeat);
        return new SeatMapRequest.ElementRequest(
                section + "-" + rowLabel + "-" + firstSeat,
                SeatingElementType.ROW,
                rowLabel + " - " + section,
                null,
                first[0],
                first[1],
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
                "Seat positions follow the published seating plan; "
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
            // Row heights traced from the published plan (pixels, stage below row A; row J sits
            // behind a wider gap), scaled to the 20-unit seat pitch.
            double planY = "J".equals(rowLabel) ? 33.0 : 497.0 - index * 52.0;
            double positionY = Math.round(planY * 20.0 / 49.2 * 10.0) / 10.0;

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

    // York geometry traced from the published plan (pixels, stage at the bottom), scaled so seats sit
    // 20 units apart. Block3 faces the stage in straight rows, Blocks 2 and 4 run on diagonals and
    // Blocks 1 and 5 are vertical columns; left-hand blocks mirror the right-hand ones.
    private static final double YORK_SCALE = 20.0 / 27.0;
    private static final double YORK_MIRROR_X = 1820.0;
    private static final double[] YORK_COLUMN_SEAT_ONE_Y =
            {1140, 1137, 1125, 1123, 1105, 1080, 1060, 1037, 1015, 1003, 993, 970};

    private static double[] yorkSeatPosition(YorkRowBlock block, int rowIndex, int number) {
        double x;
        double y;
        switch (block.section()) {
            case "Block3" -> {
                x = 910.0 + ((block.firstSeat() + block.lastSeat()) / 2.0 - number) * 27.0;
                y = 686.0 - rowIndex * 46.8;
            }
            case "Block2", "Block4" -> {
                // Steps from the outer end of the diagonal: seat 13 on the right, the last seat on the left.
                int fromOuterEnd = "Block2".equals(block.section())
                        ? number - block.firstSeat() : block.lastSeat() - number;
                x = 1186.0 + rowIndex * 46.7 - fromOuterEnd * 19.0;
                y = 824.0 - rowIndex * 19.3 - fromOuterEnd * 19.4;
                if ("Block4".equals(block.section())) x = YORK_MIRROR_X - x;
            }
            default -> {
                // Columns rise from the rear wall: seat 1 on the right, the last seat on the left.
                int fromRear = "Block1".equals(block.section())
                        ? number - block.firstSeat() : block.lastSeat() - number;
                x = 1228.0 + rowIndex * 46.2;
                y = YORK_COLUMN_SEAT_ONE_Y[rowIndex] - fromRear * 27.3;
                if ("Block5".equals(block.section())) x = YORK_MIRROR_X - x;
            }
        }
        return new double[] {
                Math.round((x - 560.0) * YORK_SCALE * 10.0) / 10.0,
                Math.round((y - 160.0) * YORK_SCALE * 10.0) / 10.0
        };
    }

    private SeatMapRequest.ElementRequest createYorkRowBlock(
            YorkRowBlock block,
            int rowIndex
    ) {
        var seats = new ArrayList<SeatMapRequest.SeatRequest>();

        for (int number = block.firstSeat();
        number <= block.lastSeat(); number++){
            double[] position = yorkSeatPosition(block, rowIndex, number);

            seats.add(createSeat(
                    block.section(),
                    block.rowLabel(),
                    rowIndex,
                    number,
                    position[0],
                    position[1],
                    false
            ));
        }

        double[] first = yorkSeatPosition(block, rowIndex, block.firstSeat());
        return new SeatMapRequest.ElementRequest(
                "york-" + block.section() + "-" + block.rowLabel() + "-" + block.firstSeat(),
                SeatingElementType.ROW,
                block.rowLabel() + " - " + block.section(),
                "Seat positions traced from the published seating plan; "
                + "dimensions are approximate. Accessibility is not verified.",
                first[0],
                first[1],
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
