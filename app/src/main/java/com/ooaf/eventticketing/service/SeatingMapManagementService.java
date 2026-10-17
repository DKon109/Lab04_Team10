package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.MockCheckoutRequest;
import com.ooaf.eventticketing.dto.SeatAssignmentRequest;
import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeatingMapManagementService {
    public enum SeatStatus { AVAILABLE, ASSIGNED, BLOCKED, PRIVATE }

    public record SeatView(Long id, String code, String label, String note, String section,
            String rowLabel, int rowIndex, int seatNumber, double positionX, double positionY,
            boolean accessible, SeatStatus status, List<String> ticketTypes,
            Long assignedBookingId, String assignedBookingReference) {}

    public record ElementView(Long id, String key, SeatingElementType type, String label,
            String note, double positionX, double positionY, int rotationDegrees,
            int displayOrder, boolean privateElement, List<SeatView> seats) {}

    public record TicketTypeView(String code, int configuredCapacity, long mappedCapacity,
            boolean capacityMatches) {}

    public record SeatMapView(Long eventId, String name, boolean attendeesChooseSeats,
            int version, long totalSeats, long publicCapacity, long assignedSeats,
            List<TicketTypeView> ticketTypes, List<ElementView> elements) {}

    public record AssignmentView(Long bookingId, String bookingReference, String seatCode,
            BookingStatus status) {}

    private final EventRepository events;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final CheckoutAttemptRepository attempts;
    private final ManagedSeatMapRepository maps;
    private final SeatingMapElementRepository elements;
    private final VenueSeatRepository seats;
    private final SeatMapTicketTypeRepository ticketTypes;
    private final SeatingLayoutRepository legacyLayouts;
    private final AllocationRunRepository allocationRuns;

    public SeatingMapManagementService(EventRepository events, UserRepository users,
            BookingRepository bookings, CheckoutAttemptRepository attempts,
            ManagedSeatMapRepository maps, SeatingMapElementRepository elements,
            VenueSeatRepository seats, SeatMapTicketTypeRepository ticketTypes,
            SeatingLayoutRepository legacyLayouts, AllocationRunRepository allocationRuns) {
        this.events = events;
        this.users = users;
        this.bookings = bookings;
        this.attempts = attempts;
        this.maps = maps;
        this.elements = elements;
        this.seats = seats;
        this.ticketTypes = ticketTypes;
        this.legacyLayouts = legacyLayouts;
        this.allocationRuns = allocationRuns;
    }

    @Transactional(readOnly = true)
    public SeatMapView organiserMap(Long eventId, String email) {
        ownedEvent(eventId, email, false);
        return view(eventId, true);
    }

    @Transactional(readOnly = true)
    public SeatMapView attendeeMap(Long eventId) {
        Event event = events.findById(eventId)
                .filter(candidate -> candidate.getStatus() != EventStatus.DRAFT)
                .orElseThrow(() -> notFound("Event not found"));
        return view(event.getId(), false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeatMapView saveMap(Long eventId, String email, SeatMapRequest request) {
        Event event = ownedEvent(eventId, email, true);
        requireEditable(event);

        NormalisedMap normalised = normaliseAndValidate(request);
        int nextVersion = maps.findById(eventId).map(existing -> existing.getMapVersion() + 1).orElse(1);

        if (legacyLayouts.existsById(eventId)) {
            legacyLayouts.deleteById(eventId);
            legacyLayouts.flush();
        }
        seats.deleteByEventId(eventId);
        ticketTypes.deleteByEventId(eventId);
        elements.deleteByEventId(eventId);
        seats.flush();
        ticketTypes.flush();
        elements.flush();

        maps.save(new ManagedSeatMap(eventId, request.name().trim(),
                request.attendeesChooseSeats(), nextVersion));

        List<SeatingMapElement> savedElements = normalised.elements().stream()
                .map(item -> new SeatingMapElement(eventId, item.key(), item.type(), item.label(),
                        item.note(), item.positionX(), item.positionY(), item.rotationDegrees(),
                        item.displayOrder(), item.privateElement()))
                .toList();
        elements.saveAllAndFlush(savedElements);
        Map<String, SeatingMapElement> byKey = savedElements.stream()
                .collect(Collectors.toMap(SeatingMapElement::getElementKey, Function.identity()));

        List<VenueSeat> savedSeats = new ArrayList<>();
        for (NormalisedElement element : normalised.elements()) {
            Long elementId = byKey.get(element.key()).getId();
            for (NormalisedSeat seat : element.seats()) {
                savedSeats.add(new VenueSeat(eventId, elementId, seat.code(), seat.label(),
                        seat.note(), seat.section(), seat.rowLabel(), seat.rowIndex(),
                        seat.seatNumber(), seat.positionX(), seat.positionY(), seat.accessible(),
                        seat.blocked(), element.privateElement() || seat.privateSeat(),
                        String.join(",", seat.ticketTypes())));
            }
        }
        seats.saveAllAndFlush(savedSeats);

        Map<String, Long> mapped = mappedCapacities(savedSeats);
        List<SeatMapTicketType> savedTicketTypes = normalised.capacities().entrySet().stream()
                .map(entry -> new SeatMapTicketType(eventId, entry.getKey(),
                        request.autoMatchTicketCapacities()
                                ? Math.toIntExact(mapped.getOrDefault(entry.getKey(), 0L))
                                : entry.getValue()))
                .toList();
        ticketTypes.saveAllAndFlush(savedTicketTypes);

        event.setCapacity(Math.toIntExact(savedSeats.stream()
                .filter(seat -> !seat.isBlocked() && !seat.isPrivateSeat()).count()));
        event.setSeatIdentifiersConfirmed(Boolean.TRUE.equals(request.identifiersConfirmed()));
        return view(eventId, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteMap(Long eventId, String email) {
        Event event = ownedEvent(eventId, email, true);
        requireEditable(event);
        if (!maps.existsById(eventId)) throw notFound("Seating map not found");
        seats.deleteByEventId(eventId);
        ticketTypes.deleteByEventId(eventId);
        elements.deleteByEventId(eventId);
        maps.deleteById(eventId);
        if (!legacyLayouts.existsById(eventId)) event.setSeatIdentifiersConfirmed(false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentView assignByOrganiser(Long eventId, Long bookingId, String email,
            SeatAssignmentRequest request) {
        ownedEvent(eventId, email, true);
        Booking booking = bookingForEvent(eventId, bookingId);
        if (!attempts.existsByBookingIdAndOutcome(bookingId, MockCheckoutRequest.Outcome.SUCCESS)) {
            throw conflict("Only mock-payment-confirmed bookings can receive a seat");
        }
        assign(booking, request.seatCode(), true);
        bookings.saveAndFlush(booking);
        return assignmentView(booking);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentView chooseSeat(Long bookingId, String email, SeatAssignmentRequest request) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Booking booking = bookings.findById(bookingId)
                .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> notFound("Booking not found"));
        Event event = events.findByIdForUpdate(booking.getEvent().getId())
                .orElseThrow(() -> notFound("Event not found"));
        ManagedSeatMap map = maps.findById(event.getId())
                .orElseThrow(() -> notFound("Seating map not found"));
        if (!map.isAttendeesChooseSeats()) throw conflict("Attendee seat selection is disabled");
        if (event.getStatus() != EventStatus.PUBLISHED
                || event.getRegistrationStatus() != EventRegistrationStatus.OPEN) {
            throw conflict("Seat selection is closed for this event");
        }
        if (!attempts.existsByBookingIdAndOutcome(bookingId, MockCheckoutRequest.Outcome.SUCCESS)) {
            throw conflict("Complete mock checkout before selecting a seat");
        }
        assign(booking, request.seatCode(), false);
        bookings.saveAndFlush(booking);
        return assignmentView(booking);
    }

    private void assign(Booking booking, String requestedCode, boolean organiser) {
        if (booking.getStatus() == BookingStatus.CANCELLED) throw conflict("Cancelled bookings cannot receive a seat");
        if (!booking.getEvent().isSeatIdentifiersConfirmed()) {
            throw conflict("Confirm physical seat identifiers before assigning seats");
        }
        if (requestedCode == null || requestedCode.isBlank()) {
            if (allocationRuns.existsById(booking.getEvent().getId())) {
                throw conflict("Seats cannot be removed after allocation; assign a replacement seat instead");
            }
            booking.unassignSeat();
            return;
        }
        String code = requestedCode.trim().toUpperCase(Locale.ROOT);
        VenueSeat seat = seats.findByEventIdAndCodeIgnoreCase(booking.getEvent().getId(), code)
                .orElseThrow(() -> notFound("Seat not found"));
        if (seat.isBlocked()) throw conflict("Seat is blocked");
        if (!organiser && seat.isPrivateSeat()) throw conflict("Seat is private");
        if (!organiser && ticketTypes(seat).isEmpty()) throw conflict("Seat is not mapped to a ticket type");
        if ((booking.isAccessibilityRequired() && !seat.isAccessible())
                || (!organiser && !booking.isAccessibilityRequired() && seat.isAccessible())) {
            throw conflict(booking.isAccessibilityRequired()
                    ? "Choose an accessible seat" : "Accessible seats are reserved for accessibility requests");
        }
        bookings.findByEventIdAndSeatCodeAndStatusNot(booking.getEvent().getId(), seat.getCode(), BookingStatus.CANCELLED)
                .filter(existing -> !existing.getId().equals(booking.getId()))
                .ifPresent(existing -> { throw conflict("Seat is already assigned"); });
        booking.assignSeat(seat.getCode(), null);
    }

    private SeatMapView view(Long eventId, boolean organiser) {
        ManagedSeatMap map = maps.findById(eventId)
                .orElseThrow(() -> notFound("Seating map not found"));
        List<SeatingMapElement> storedElements = elements.findByEventIdOrderByDisplayOrderAscIdAsc(eventId);
        List<VenueSeat> storedSeats = seats.findByEventIdOrderByElementIdAscRowIndexAscSeatNumberAsc(eventId);
        Map<String, Booking> assigned = bookings.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED && booking.getSeatCode() != null)
                .collect(Collectors.toMap(booking -> booking.getSeatCode().toUpperCase(Locale.ROOT),
                        Function.identity(), (first, ignored) -> first));

        Map<Long, List<VenueSeat>> byElement = storedSeats.stream()
                .collect(Collectors.groupingBy(VenueSeat::getElementId));
        List<ElementView> elementViews = storedElements.stream().map(element -> new ElementView(
                element.getId(), element.getElementKey(), element.getType(), element.getLabel(),
                element.getNote(), element.getPositionX(), element.getPositionY(),
                element.getRotationDegrees(), element.getDisplayOrder(), element.isPrivateElement(),
                byElement.getOrDefault(element.getId(), List.of()).stream()
                        .map(seat -> seatView(seat, assigned.get(seat.getCode().toUpperCase(Locale.ROOT)), organiser))
                        .toList())).toList();

        Map<String, Long> mapped = mappedCapacities(storedSeats);
        List<TicketTypeView> ticketTypeViews = ticketTypes.findByEventIdOrderByCodeAsc(eventId).stream()
                .map(type -> new TicketTypeView(type.getCode(), type.getCapacity(),
                        mapped.getOrDefault(type.getCode(), 0L),
                        type.getCapacity() == mapped.getOrDefault(type.getCode(), 0L)))
                .toList();
        long publicCapacity = storedSeats.stream().filter(seat -> !seat.isBlocked() && !seat.isPrivateSeat()).count();
        return new SeatMapView(eventId, map.getName(), map.isAttendeesChooseSeats(), map.getMapVersion(),
                storedSeats.size(), publicCapacity, assigned.size(), ticketTypeViews, elementViews);
    }

    private SeatView seatView(VenueSeat seat, Booking booking, boolean organiser) {
        SeatStatus status = seat.isBlocked() ? SeatStatus.BLOCKED
                : seat.isPrivateSeat() ? SeatStatus.PRIVATE
                : booking != null ? SeatStatus.ASSIGNED : SeatStatus.AVAILABLE;
        return new SeatView(seat.getId(), seat.getCode(), seat.getLabel(), seat.getNote(),
                seat.getSectionName(), seat.getRowLabel(), seat.getRowIndex(), seat.getSeatNumber(),
                seat.getPositionX(), seat.getPositionY(), seat.isAccessible(), status,
                ticketTypes(seat), organiser && booking != null ? booking.getId() : null,
                organiser && booking != null ? booking.getBookingRef() : null);
    }

    private NormalisedMap normaliseAndValidate(SeatMapRequest request) {
        Set<String> elementKeys = new HashSet<>();
        Set<String> seatCodes = new HashSet<>();
        Map<String, Integer> capacities = new TreeMap<>();
        request.ticketTypeCapacities().forEach((key, value) -> {
            String code = normaliseTicketType(key);
            if (capacities.put(code, value) != null) throw badRequest("Duplicate ticket type: " + code);
        });

        List<NormalisedElement> normalisedElements = new ArrayList<>();
        int totalSeats = 0;
        int publicSeats = 0;
        for (SeatMapRequest.ElementRequest element : request.elements()) {
            String key = element.key().trim();
            if (!elementKeys.add(key.toUpperCase(Locale.ROOT))) throw badRequest("Element keys must be unique");
            boolean decorative = element.type() == SeatingElementType.OBJECT || element.type() == SeatingElementType.TEXT;
            if (decorative && !element.seats().isEmpty()) throw badRequest("Objects and text cannot contain seats");
            if (!decorative && element.seats().isEmpty()) throw badRequest("Seat-bearing elements must contain at least one seat");

            List<NormalisedSeat> normalisedSeats = new ArrayList<>();
            for (SeatMapRequest.SeatRequest seat : element.seats()) {
                String code = seat.code().trim().toUpperCase(Locale.ROOT);
                if (!seatCodes.add(code)) throw badRequest("Seat codes must be unique: " + code);
                SortedSet<String> mappings = seat.ticketTypes().stream()
                        .map(this::normaliseTicketType).collect(Collectors.toCollection(TreeSet::new));
                if (!capacities.keySet().containsAll(mappings)) {
                    throw badRequest("Seat " + code + " references an unknown ticket type");
                }
                boolean privateSeat = element.privateElement() || seat.privateSeat();
                if (!seat.blocked() && !privateSeat) {
                    publicSeats++;
                    if (request.attendeesChooseSeats() && mappings.isEmpty()) {
                        throw badRequest("Every public seat must map to a ticket type when attendees choose seats");
                    }
                }
                totalSeats++;
                normalisedSeats.add(new NormalisedSeat(code, seat.label().trim(), trimToNull(seat.note()),
                        seat.section().trim(), seat.rowLabel().trim(), seat.rowIndex(), seat.seatNumber(),
                        seat.positionX(), seat.positionY(), seat.accessible(), seat.blocked(),
                        seat.privateSeat(), List.copyOf(mappings)));
            }
            normalisedElements.add(new NormalisedElement(key, element.type(), element.label().trim(),
                    trimToNull(element.note()), element.positionX(), element.positionY(),
                    element.rotationDegrees(), element.displayOrder(), element.privateElement(),
                    List.copyOf(normalisedSeats)));
        }
        if (totalSeats > 1000) throw badRequest("A seating map supports at most 1000 seats");
        if (publicSeats == 0) throw badRequest("At least one public, unblocked seat is required");
        if (request.attendeesChooseSeats() && capacities.isEmpty()) {
            throw badRequest("Define at least one ticket type before enabling attendee seat selection");
        }
        return new NormalisedMap(Map.copyOf(capacities), List.copyOf(normalisedElements));
    }

    private Map<String, Long> mappedCapacities(List<VenueSeat> storedSeats) {
        Map<String, Long> result = new TreeMap<>();
        storedSeats.stream().filter(seat -> !seat.isBlocked() && !seat.isPrivateSeat())
                .forEach(seat -> ticketTypes(seat).forEach(type -> result.merge(type, 1L, Long::sum)));
        return result;
    }

    private List<String> ticketTypes(VenueSeat seat) {
        if (seat.getTicketTypes() == null || seat.getTicketTypes().isBlank()) return List.of();
        return Arrays.stream(seat.getTicketTypes().split(",")).filter(value -> !value.isBlank()).toList();
    }

    private Booking bookingForEvent(Long eventId, Long bookingId) {
        return bookings.findById(bookingId)
                .filter(booking -> booking.getEvent().getId().equals(eventId))
                .orElseThrow(() -> notFound("Booking not found"));
    }

    private AssignmentView assignmentView(Booking booking) {
        return new AssignmentView(booking.getId(), booking.getBookingRef(), booking.getSeatCode(), booking.getStatus());
    }

    private void requireEditable(Event event) {
        if (event.getStatus() != EventStatus.DRAFT) throw conflict("Seating maps can only be edited while an event is a draft");
        if (bookings.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED) > 0
                || allocationRuns.existsById(event.getId())) {
            throw conflict("Cannot replace a seating map after bookings or allocation exist");
        }
    }

    private Event ownedEvent(Long eventId, String email, boolean lock) {
        User user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Event event = (lock ? events.findByIdForUpdate(eventId) : events.findById(eventId))
                .orElseThrow(() -> notFound("Event not found"));
        if (user.getRole() != UserRole.COMMITTEE_MEMBER || event.getOwner() == null
                || !event.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event owner can manage seating");
        }
        return event;
    }

    private String normaliseTicketType(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record NormalisedMap(Map<String, Integer> capacities, List<NormalisedElement> elements) {}
    private record NormalisedElement(String key, SeatingElementType type, String label, String note,
            double positionX, double positionY, int rotationDegrees, int displayOrder,
            boolean privateElement, List<NormalisedSeat> seats) {}
    private record NormalisedSeat(String code, String label, String note, String section,
            String rowLabel, int rowIndex, int seatNumber, double positionX, double positionY,
            boolean accessible, boolean blocked, boolean privateSeat, List<String> ticketTypes) {}
}
