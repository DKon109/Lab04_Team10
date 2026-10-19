package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.FrontStageTemplateRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.entity.SeatingElementType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;

@Service
public class FrontStageTemplateService {

    public SeatMapRequest generate(FrontStageTemplateRequest request ) {
        if (request == null || request.name() == null
        || request.name().isBlank() ||
        request.name().length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A template name of 1 to 100 characters is required"
            );
        }

        validateDimensions(request);

        Set<String> validCodes = createValidSeatCodes(
                request.rows(),
                request.seatsPerRow()
        );

        Set<String> blockedCodes = validateSeatCodes(
                request.blockedCodes(), validCodes, "blockedCodes"
        );
        Set<String> accessibleCodes = validateSeatCodes(
                request.accessibleCodes(), validCodes, "accessibleCodes"
        );

        validateSeatFlags(validCodes, blockedCodes, accessibleCodes);

        int capacity = validCodes.size() - blockedCodes.size();

        return new SeatMapRequest(
                request.name().trim(),
                false,
                true,
                false,
                Map.of("STANDARD", capacity),
                createRows(request, blockedCodes, accessibleCodes)
        );
    }

    private void validateDimensions(FrontStageTemplateRequest request) {
        int rows = request.rows();
        int columns = request.seatsPerRow();

        if (rows < 1 || rows > 20 || columns < 1 || columns > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rows and seats per row must be between 1 and 20"
            );
        }

        if (rows * columns > 300) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A front-stage template supports at most 300 seats"
            );
        }

        if (request.aisleAfter() < 0 || request.aisleAfter() >= columns) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The aisle must be within the row; use 0 for no aisle"
            );
        }
    }

    private Set<String> validateSeatCodes(
            List<String> codes,
            Set<String> validCodes,
            String fieldName
    ) {
        if (codes == null){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must be provided; use an empty list if needed"
            );
        }

        Set<String> result = new HashSet<>();

        for (String code : codes) {
            if (code == null || !validCodes.contains(code)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        fieldName + " contains an unknown seat code: " + code
                );
            }

            if (!result.add(code)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        fieldName + " contains a duplicate seat code: " + code
                );
            }
        }

        return result;
    }

    private String seatCode(int rowIndex, int seatNumber) {
        char rowLabel = (char) ('A' + rowIndex);
        return String.valueOf(rowLabel) + seatNumber;
    }

    private Set<String> createValidSeatCodes(int rows, int seatsPerRow) {
        Set<String> codes = new HashSet<>();

        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            for (int seatNumber = 1; seatNumber <= seatsPerRow; seatNumber++) {
                codes.add(seatCode(rowIndex, seatNumber));
            }
        }

        return codes;
    }

    private void validateSeatFlags(
            Set<String> validCodes,
            Set<String> blockedCodes,
            Set<String> accessibleCodes
    ) {
        if (!Collections.disjoint(blockedCodes, accessibleCodes)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A blocked seat cannot also be accessible"
            );
        }

        if (blockedCodes.size() == validCodes.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one seat must remain available"
            );
        }
    }

    private SeatMapRequest.SeatRequest createSeat(
            int rowIndex,
            int seatNumber,
            int aisleAfter,
            Set<String> blockCodes,
            Set<String> accessibleCodes
    ) {
        String code = seatCode(rowIndex, seatNumber);
        String rowLabel = String.valueOf((char) ('A' + rowIndex));

        boolean afterAisle = aisleAfter > 0 && seatNumber > aisleAfter;
        String section = aisleAfter == 0
                ? "Main"
                :afterAisle ? "Right" : "Left";

        double x = (seatNumber - 1) * 30.0;
        if  (afterAisle) {
            x += 40.0;
        }
        double y = (rowIndex + 1) * 40.0;

        return new SeatMapRequest.SeatRequest(
                code,
                code,
                null,
                section,
                rowLabel,
                rowIndex,
                seatNumber,
                x,
                y,
                accessibleCodes.contains(code),
                blockCodes.contains(code),
                false,
                List.of("STANDARD")
        );
    }

    private SeatMapRequest.ElementRequest createRowSection(
            int rowIndex,
            int firstSeat,
            int lastSeat,
            int aisleAfter,
            int displayOrder,
            Set<String> blockedCodes,
            Set<String> accessibleCodes
    ) {
        List<SeatMapRequest.SeatRequest> seats = new ArrayList<>();

        for (int seatNumber = firstSeat; seatNumber <= lastSeat; seatNumber++) {
            seats.add(createSeat(
                    rowIndex, seatNumber, aisleAfter, blockedCodes, accessibleCodes
            ));
        }

        var first = seats.get(0);

        return new SeatMapRequest.ElementRequest(
                "row-" + first.rowLabel() + "-" + firstSeat,
                SeatingElementType.ROW,
                first.rowLabel() + " - " + first.section(),
                null,
                first.positionX(),
                first.positionY(),
                0,
                displayOrder,
                false,
                List.copyOf(seats)
        );
    }

    private List<SeatMapRequest.ElementRequest> createRows(
            FrontStageTemplateRequest request,
            Set<String> blockCodes,
            Set<String> accessibleCodes
    ) {
        List<SeatMapRequest.ElementRequest> elements = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < request.rows(); rowIndex++) {
            int firstSectionEnd = request.aisleAfter() == 0
                    ? request.seatsPerRow()
                    : request.aisleAfter();

            elements.add(createRowSection(
                    rowIndex,
                    1,
                    firstSectionEnd,
                    request.aisleAfter(),
                    elements.size(),
                    blockCodes,
                    accessibleCodes
            ));

            if (request.aisleAfter() > 0) {
                elements.add(createRowSection(
                        rowIndex,
                        request.aisleAfter() + 1,
                        request.seatsPerRow(),
                        request.aisleAfter(),
                        elements.size(),
                        blockCodes,
                        accessibleCodes
                ));
            }
        }

        return List.copyOf(elements);
    }


}
