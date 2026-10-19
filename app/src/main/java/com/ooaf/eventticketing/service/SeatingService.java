package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.allocation.*;
import com.ooaf.eventticketing.dto.*;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatingService {
    public record SeatView(long id, String code, String section, int row, int column, boolean blocked, boolean accessible) {}
    public record LayoutView(String source, String template, String roomId, int rows, int seatsPerRow, int aisleAfter,
            int version, List<SeatView> seats) {}
    public record ManagementView(EventResponse event, LayoutView layout, AllocationRun allocation, long confirmedBookings, long unverifiedLegacyBookings) {}
    public record TicketView(BookingResponse booking, LocalDateTime eventDate, String venue, String paymentStatus,
            String seatCode, String splitReason, boolean generalAdmission, LayoutView layout, SeatingMapManagementService.SeatMapView managedMap) {}
    public record CampusProfile(String id, String name, String template, int rows, int seatsPerRow, String notice) {}
    public static final List<CampusProfile> CAMPUS_PROFILES = List.of(
            new CampusProfile("woolley-demo", "Woolley — demo subset", "FRONT", 6, 10, "Illustrative only; not a verified physical room map"),
            new CampusProfile("sit-demo", "SIT LT1 — demo subset", "FAN", 8, 12, "Illustrative only; not a verified physical room map"),
            new CampusProfile("manning-demo", "Manning — demo floor plan", "CENTRAL", 5, 12, "Illustrative only; not a verified physical room map"));
    private final EventRepository events;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final CheckoutAttemptRepository attempts;
    private final SeatingLayoutRepository layouts;
    private final ManagedSeatMapRepository managedMaps;
    private final VenueSeatRepository venueSeats;
    private final AllocationRunRepository runs;
    private final SeatAllocationService allocator;
    private final SeatingMapManagementService mapManagement;
    public SeatingService(EventRepository events, UserRepository users, BookingRepository bookings, CheckoutAttemptRepository attempts,
            SeatingLayoutRepository layouts, ManagedSeatMapRepository managedMaps,
            VenueSeatRepository venueSeats, AllocationRunRepository runs, SeatAllocationService allocator,
            SeatingMapManagementService mapManagement) {
        this.events = events; this.users = users; this.bookings = bookings; this.attempts = attempts;
        this.layouts = layouts; this.managedMaps = managedMaps; this.venueSeats = venueSeats;
        this.runs = runs; this.allocator = allocator; this.mapManagement = mapManagement;
    }

    @Transactional(readOnly = true)
    public ManagementView management(Long eventId, String email) {
        Event event = ownedEvent(eventId, email, false);
        return managementView(event);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ManagementView configure(Long eventId, String email, SeatingLayoutRequest request) {
        Event event = ownedEvent(eventId, email, true);
        if (event.getStatus() != EventStatus.DRAFT) throw conflict("Layout can only be changed while the event is a draft");
        if (managedMaps.existsById(eventId))
            throw conflict("Delete the managed seating map before using the legacy grid layout");
        if (bookings.countByEventIdAndStatusNot(eventId, BookingStatus.CANCELLED) > 0 || runs.existsById(eventId))
            throw conflict("Cannot change a layout after bookings or allocation exist");
        int rows = request.rows(), columns = request.seatsPerRow();
        String template = request.template().name();
        String roomId = null;
        if (request.source() == SeatingLayoutRequest.Source.CAMPUS_DEMO) {
            var profile = CAMPUS_PROFILES.stream().filter(p -> p.id().equals(request.roomId())).findFirst()
                    .orElseThrow(() -> badRequest("Unknown campus demo profile"));
            rows = profile.rows(); columns = profile.seatsPerRow(); template = profile.template(); roomId = profile.id();
        }
        if (rows * columns > 300 || request.aisleAfter() >= columns) throw badRequest("Use at most 300 seats and place the aisle within the row (0 means no aisle)");
        var validCodes = new HashSet<String>();
        for (int row = 0; row < rows; row++) for (int col = 1; col <= columns; col++) validCodes.add(code(row, col));
        var blocked = normaliseCodes(request.blockedCodes(), validCodes);
        var accessible = normaliseCodes(request.accessibleCodes(), validCodes);
        if (!Collections.disjoint(blocked, accessible)) throw badRequest("A blocked seat cannot also be accessible");
        int usable = validCodes.size() - blocked.size();
        if (usable == 0) throw badRequest("At least one seat must remain available");
        var layout = layouts.findById(eventId).orElseGet(SeatingLayout::new);
        if (layout.getEventId() != null) layout.setLayoutVersion(layout.getLayoutVersion() + 1);
        layout.setEventId(eventId); layout.setSource(request.source().name()); layout.setTemplate(template); layout.setRoomId(roomId);
        layout.setRowCount(rows); layout.setSeatsPerRow(columns); layout.setAisleAfter(request.aisleAfter());
        layout.setBlockedCodes(String.join(",", blocked)); layout.setAccessibleCodes(String.join(",", accessible));
        layouts.saveAndFlush(layout);
        event.setCapacity(usable); event.setSeatIdentifiersConfirmed(request.identifiersConfirmed());
        return managementView(event);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AllocationRun allocate(Long eventId, String email) {
        long started = System.nanoTime();
        Event event = ownedEvent(eventId, email, true);
        if (event.getRegistrationStatus() != EventRegistrationStatus.CLOSED && event.getStatus() != EventStatus.CLOSED)
            throw conflict("Close registration before allocating seats");
        var existing = runs.findById(eventId);
        if (existing.isPresent()) return existing.get();
        var confirmed = confirmedBookings(eventId);
        if (confirmed.isEmpty()) throw conflict("No mock-payment-confirmed bookings to allocate");
        int splitCount = 0, version = 0;
        if (event.isSeatIdentifiersConfirmed()) {
            if (managedMaps.existsById(eventId)) {
                var map = managedMaps.findById(eventId).orElseThrow();
                version = map.getMapVersion();
                var storedSeats = venueSeats.findByEventIdOrderByElementIdAscRowIndexAscSeatNumberAsc(eventId);
                Set<String> occupied = bookings.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                        .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED && booking.getSeatCode() != null)
                        .map(booking -> booking.getSeatCode().toUpperCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                var pending = confirmed.stream().filter(booking -> booking.getSeatCode() == null).toList();
                if (!pending.isEmpty()) {
                    var allocationSeats = storedSeats.stream().map(seat -> new AllocationSeat(
                            seat.getId(), seat.getSectionName(), seat.getRowLabel(), seat.getRowIndex(),
                            seat.getSeatNumber(), seat.isAccessible(), seat.isBlocked() || seat.isPrivateSeat()
                                    || occupied.contains(seat.getCode().toUpperCase(Locale.ROOT)))).toList();
                    try {
                        var result = allocator.allocate(pending.stream().map(booking -> new AllocationBooking(
                                booking.getId(), booking.getGroupCode(), booking.isAccessibilityRequired())).toList(), allocationSeats);
                        Map<Long, Booking> byId = pending.stream().collect(Collectors.toMap(Booking::getId, booking -> booking));
                        Map<Long, VenueSeat> bySeatId = storedSeats.stream().collect(Collectors.toMap(VenueSeat::getId, seat -> seat));
                        Map<String, String> reasons = result.splits().stream().collect(Collectors.toMap(GroupSplit::groupCode, GroupSplit::reason));
                        for (var assignment : result.assignments()) {
                            byId.get(assignment.bookingId()).assignSeat(bySeatId.get(assignment.seatId()).getCode(),
                                    assignment.split() ? reasons.get(assignment.groupCode()) : null);
                        }
                        splitCount = result.splits().size();
                    } catch (AllocationException e) { throw conflict(e.getMessage()); }
                }
            } else {
                var layout = layouts.findById(eventId).orElseThrow(() -> conflict("Save a seating layout before allocation"));
                version = layout.getLayoutVersion();
                var views = seatViews(layout);
                var seats = views.stream().map(s -> new AllocationSeat(s.id(), s.section(), String.valueOf((char)('A' + s.row())), s.row(), s.column(), s.accessible(), s.blocked())).toList();
                try {
                    var result = allocator.allocate(confirmed.stream().map(b -> new AllocationBooking(b.getId(), b.getGroupCode(), b.isAccessibilityRequired())).toList(), seats);
                    Map<Long, Booking> byId = confirmed.stream().collect(Collectors.toMap(Booking::getId, b -> b));
                    Map<Long, SeatView> seatsById = views.stream().collect(Collectors.toMap(SeatView::id, s -> s));
                    Map<String, String> reasons = result.splits().stream().collect(Collectors.toMap(GroupSplit::groupCode, GroupSplit::reason));
                    for (var assignment : result.assignments()) {
                        var booking = byId.get(assignment.bookingId());
                        booking.assignSeat(seatsById.get(assignment.seatId()).code(), assignment.split() ? reasons.get(assignment.groupCode()) : null);
                    }
                    splitCount = result.splits().size();
                } catch (AllocationException e) { throw conflict(e.getMessage()); }
            }
        } else {
            // General admission has a valid ticket but deliberately no seat label.
            confirmed.forEach(b -> b.assignSeat(null, null));
        }
        bookings.saveAllAndFlush(confirmed);
        if (event.isSeatIdentifiersConfirmed() && managedMaps.existsById(eventId)) {
            splitCount = mapManagement.refreshGroupSplits(eventId);
        }
        return runs.saveAndFlush(new AllocationRun(eventId, version, confirmed.size(), splitCount, (System.nanoTime() - started) / 1_000_000));
    }

    @Transactional(readOnly = true)
    public TicketView ticket(Long bookingId, String email) {
        var user = requireUser(email);
        var booking = bookings.findById(bookingId).filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        Event event = booking.getEvent();
        boolean paid = confirmedIds(event.getId()).contains(booking.getId());
        return new TicketView(BookingResponse.fromEntity(booking), event.getDateTime(), event.getVenue(), paid ? "MOCK_SUCCESS" : "UNVERIFIED_LEGACY",
                booking.getSeatCode(), booking.getSplitReason(), !event.isSeatIdentifiersConfirmed(),
                layouts.findById(event.getId()).map(this::layoutView).orElse(null),
                managedMaps.existsById(event.getId()) ? mapManagement.attendeeMap(event.getId()) : null);
    }

    @Transactional(readOnly = true)
    public String export(Long eventId, String email) {
        ownedEvent(eventId, email, false);
        if (!runs.existsById(eventId)) throw conflict("Run allocation before exporting seat assignments");
        var csv = new StringBuilder("booking_reference,group_reference,seat,split_reason\r\n");
        for (var booking : confirmedBookings(eventId)) {
            csv.append(csv(booking.getBookingRef())).append(',').append(csv(booking.getGroupCode())).append(',')
                    .append(csv(booking.getSeatCode() == null ? "GENERAL_ADMISSION" : booking.getSeatCode())).append(',')
                    .append(csv(booking.getSplitReason())).append("\r\n");
        }
        return csv.toString();
    }
    private String csv(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\""; }
    private ManagementView managementView(Event event) {
        long confirmed = confirmedBookings(event.getId()).size();
        long active = bookings.countByEventIdAndStatusNot(event.getId(), BookingStatus.CANCELLED);
        return new ManagementView(EventResponse.fromEntity(event), layouts.findById(event.getId()).map(this::layoutView).orElse(null),
                runs.findById(event.getId()).orElse(null), confirmed, Math.max(0, active - confirmed));
    }
    private Set<Long> confirmedIds(Long eventId) {
        return attempts.findByEventIdAndOutcome(eventId, MockCheckoutRequest.Outcome.SUCCESS).stream().map(CheckoutAttempt::getBookingId).filter(Objects::nonNull).collect(Collectors.toSet());
    }
    private List<Booking> confirmedBookings(Long eventId) {
        var ids = confirmedIds(eventId);
        return bookings.findByEventIdOrderByCreatedAtAsc(eventId).stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED && ids.contains(b.getId())).toList();
    }
    private User requireUser(String email) { return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
    private Event ownedEvent(Long eventId, String email, boolean lock) {
        var user = requireUser(email);
        var event = (lock ? events.findByIdForUpdate(eventId) : events.findById(eventId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (user.getRole() != UserRole.COMMITTEE_MEMBER || event.getOwner() == null || !event.getOwner().getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the event owner can manage seating");
        return event;
    }
    private SortedSet<String> normaliseCodes(List<String> input, Set<String> valid) {
        var codes = new TreeSet<String>();
        if (input != null) input.forEach(value -> codes.add(value.trim().toUpperCase(Locale.ROOT)));
        if (!valid.containsAll(codes)) throw badRequest("Seat codes must exist in this layout (for example A1, A2)");
        return codes;
    }
    private LayoutView layoutView(SeatingLayout layout) {
        return new LayoutView(layout.getSource(), layout.getTemplate(), layout.getRoomId(), layout.getRowCount(), layout.getSeatsPerRow(), layout.getAisleAfter(), layout.getLayoutVersion(), seatViews(layout));
    }
    private List<SeatView> seatViews(SeatingLayout layout) {
        Set<String> blocked = new HashSet<>(Arrays.asList(layout.getBlockedCodes().split(",")));
        Set<String> accessible = new HashSet<>(Arrays.asList(layout.getAccessibleCodes().split(",")));
        var result = new ArrayList<SeatView>();
        for (int row = 0; row < layout.getRowCount(); row++) for (int col = 1; col <= layout.getSeatsPerRow(); col++) {
            String section = "CENTRAL".equals(layout.getTemplate()) ? (row < (layout.getRowCount() + 1) / 2 ? "North" : "South") : "Main";
            if (layout.getAisleAfter() > 0) section += col <= layout.getAisleAfter() ? "-Left" : "-Right";
            String code = code(row, col);
            result.add(new SeatView((long) row * layout.getSeatsPerRow() + col, code, section, row, col, blocked.contains(code), accessible.contains(code)));
        }
        return result;
    }
    private String code(int row, int col) { return String.valueOf((char) ('A' + row)) + col; }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
