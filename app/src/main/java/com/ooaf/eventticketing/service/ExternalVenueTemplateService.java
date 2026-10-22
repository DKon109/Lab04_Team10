package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.ExternalVenueTemplateRequest;
import com.ooaf.eventticketing.dto.FrontStageTemplateRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.dto.SeatingLayoutRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExternalVenueTemplateService {

    private final FrontStageTemplateService frontStageTemplates;

    public ExternalVenueTemplateService(
            FrontStageTemplateService frontStageTemples
    ) {
        this.frontStageTemplates = frontStageTemples;
    }

    public SeatMapRequest generate(
            ExternalVenueTemplateRequest request
            ) {
        var baseMap = generateBaseMap(request);

        if (request.template()
        == SeatingLayoutRequest.Template.CENTRAL
        && request.rows() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A central-stage template requires at least 2 rows"
                    );
        }

        var elements = baseMap.elements().stream()
                .map(element -> transformElement(element, request))
                .toList();

        return new SeatMapRequest(
                baseMap.name(),
                baseMap.attendeesChooseSeats(),
                baseMap.autoMatchTicketCapacities(),
                baseMap.identifiersConfirmed(),
                baseMap.ticketTypeCapacities(),
                elements
        );
    }


    private SeatMapRequest generateBaseMap(
            ExternalVenueTemplateRequest request
    ) {
        if (request == null || request.template() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A layout template is required"
            );
        }

        return frontStageTemplates.generate(
                new FrontStageTemplateRequest(
                        request.name(),
                        request.rows(),
                        request.seatsPerRow(),
                        request.aisleAfter(),
                        request.blockedCodes(),
                        request.accessibleCodes()
                )
        );
    }

    private SeatMapRequest.SeatRequest repositionSeat(
            SeatMapRequest.SeatRequest seat,
            String section,
            double positionX,
            double positionY
    ) {
        return new SeatMapRequest.SeatRequest(
                seat.code(),
                seat.label(),
                seat.note(),
                section,
                seat.rowLabel(),
                seat.rowIndex(),
                seat.seatNumber(),
                positionX,
                positionY,
                seat.accessible(),
                seat.blocked(),
                seat.privateSeat(),
                seat.ticketTypes()
        );
    }

    private SeatMapRequest.SeatRequest createFanSeat(
            SeatMapRequest.SeatRequest seat,
            ExternalVenueTemplateRequest request
            ) {
        boolean hasAisle = request.aisleAfter() > 0;
        int gapSlots = hasAisle ? 2 : 0;
        int totalSlots = request.seatsPerRow() + gapSlots;

        int slot = seat.seatNumber() - 1;
        if (hasAisle && seat.seatNumber() > request.aisleAfter()) {
            slot += gapSlots;
        }

        double angleDegrees = totalSlots == 1
                ? 0.0
                : -60.0 + 120.0 * slot / (totalSlots - 1);

        double angle = Math.toRadians(angleDegrees);
        double radius = 240.0 + seat.rowIndex() * 40.0;

        double positionX = radius * Math.sin(angle);
        double positionY = radius * Math.cos(angle);

        return repositionSeat(
                seat,
                seat.section(),
                positionX,
                positionY
        );
    }

    private SeatMapRequest.SeatRequest createCentralSeat(
            SeatMapRequest.SeatRequest seat,
            ExternalVenueTemplateRequest request
    ) {
        int northRows = (request.rows() + 1)/ 2;
        boolean north = seat.rowIndex() < northRows;

        int sideRowIndex = north
                ? seat.rowIndex()
                : seat.rowIndex() - northRows;

        double width = (request.seatsPerRow() - 1) * 30.0;
        if (request.aisleAfter() > 0) {
            width += 40.0;
        }

        double positionX = seat.positionX() - width / 2.0;
        double distanceFromStage = 100.0 + sideRowIndex * 40.0;
        double positionY = north
                ? -distanceFromStage
                : distanceFromStage;

        String section = north ? "North" : "South";
        if (request.aisleAfter() > 0) {
            section += "-" + seat.section();
        }

        return repositionSeat(
                seat,
                section,
                positionX,
                positionY
        );
    }

    private SeatMapRequest.ElementRequest transformElement(
            SeatMapRequest.ElementRequest element,
            ExternalVenueTemplateRequest request
    ) {
        var seats = element.seats().stream()
                .map(seat -> switch (request.template()) {
                    case FRONT -> seat;
                    case FAN -> createFanSeat(seat, request);
                    case CENTRAL -> createCentralSeat(seat, request);
                })
                .toList();

        var firstSeat = seats.get(0);

        return new SeatMapRequest.ElementRequest(
                element.key(),
                element.type(),
                firstSeat.rowLabel() + " - " + firstSeat.section(),
                element.note(),
                firstSeat.positionX(),
                firstSeat.positionY(),
                element.rotationDegrees(),
                element.displayOrder(),
                element.privateElement(),
                seats
        );
    }
}
