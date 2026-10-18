package com.ooaf.eventticketing.allocation;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class SeatAllocationService {

    private static final Comparator<AllocationSeat> SEAT_ORDER = Comparator
            .comparing(AllocationSeat::section, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(AllocationSeat::rowIndex)
            .thenComparing(AllocationSeat::rowLabel, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(AllocationSeat::seatNumber)
            .thenComparing(AllocationSeat::seatId);

    private static final Comparator<AllocationBooking> BOOKING_ORDER = Comparator
            .comparing(AllocationBooking::accessibilityRequired).reversed()
            .thenComparing(AllocationBooking::bookingId);

    public AllocationResult allocate(
            List<AllocationBooking> bookings,
            List<AllocationSeat> seats
    ) {
        validateInputs(bookings, seats);

        Map<Long, AllocationSeat> byId = new HashMap<>();
        seats.forEach(seat -> byId.put(seat.seatId(), seat));
        Map<Long, AllocationSeat> fixed = new HashMap<>();
        var occupied = new HashSet<Long>();
        for (var booking : bookings) {
            if (booking.assignedSeatId() == null) continue;
            var seat = byId.get(booking.assignedSeatId());
            if (seat == null || seat.blocked() || (booking.accessibilityRequired() && !seat.accessible())
                    || !occupied.add(seat.seatId())) {
                throw new AllocationException("Existing seat assignment does not satisfy seating constraints");
            }
            fixed.put(booking.bookingId(), seat);
        }
        List<AllocationSeat> usableSeats = seats.stream().filter(seat -> !seat.blocked())
                .filter(seat -> !occupied.contains(seat.seatId())).sorted(SEAT_ORDER).toList();
        var pendingBookings = bookings.stream().filter(b -> b.assignedSeatId() == null).toList();
        requireCapacity(pendingBookings, usableSeats);
        Map<Long, AllocationSeat> available = new LinkedHashMap<>();
        usableSeats.forEach(seat -> available.put(seat.seatId(), seat));
        List<SeatAssignment> assignments = new ArrayList<>();
        List<GroupSplit> splits = new ArrayList<>();
        long remainingAccessibleBookings = pendingBookings.stream().filter(AllocationBooking::accessibilityRequired).count();

        for (BookingGroup wholeGroup : groupBookings(bookings)) {
            var pending = wholeGroup.bookings().stream().filter(b -> b.assignedSeatId() == null).toList();
            var anchors = wholeGroup.bookings().stream().filter(b -> b.assignedSeatId() != null)
                    .map(b -> fixed.get(b.bookingId())).sorted(SEAT_ORDER).toList();
            var group = new BookingGroup(wholeGroup.key(), wholeGroup.groupCode(), pending);
            List<AllocationSeat> currentSeats = new ArrayList<>(available.values());
            remainingAccessibleBookings -= accessibleCount(group);
            long maxAccessible = accessibleCount(currentSeats) - remainingAccessibleBookings;
            List<AllocationSeat> selected = pending.isEmpty() ? List.of()
                    : findContiguousBlock(group, currentSeats, maxAccessible, anchors);
            if (!pending.isEmpty() && selected.isEmpty()) {
                selected = findNearestCluster(group, currentSeats, maxAccessible, anchors);
            }
            if (selected.size() != pending.size()) {
                throw new AllocationException("No valid seats remain for group " + wholeGroup.displayName());
            }
            var allSeats = new ArrayList<>(anchors);
            allSeats.addAll(selected);
            boolean split = wholeGroup.groupCode() != null && !AllocationTopology.contiguous(allSeats);
            assignments.addAll(assignBookings(group, selected, split));
            for (var booking : wholeGroup.bookings()) {
                if (booking.assignedSeatId() != null) assignments.add(new SeatAssignment(
                        booking.bookingId(), booking.assignedSeatId(), booking.groupCode(), split));
            }
            selected.forEach(seat -> available.remove(seat.seatId()));
            if (split) splits.add(new GroupSplit(wholeGroup.groupCode(),
                    wholeGroup.bookings().stream().map(AllocationBooking::bookingId).toList(),
                    allSeats.stream().sorted(SEAT_ORDER).map(AllocationSeat::seatId).toList(),
                    "No contiguous valid block was available while preserving existing assignments; nearest valid seats were used"));
        }

        assignments.sort(Comparator.comparing(SeatAssignment::bookingId));
        splits.sort(Comparator.comparing(GroupSplit::groupCode, String.CASE_INSENSITIVE_ORDER));
        return new AllocationResult(assignments, splits);
    }

    private void validateInputs(List<AllocationBooking> bookings, List<AllocationSeat> seats) {
        if (bookings == null || seats == null) {
            throw new IllegalArgumentException("bookings and seats are required");
        }
        requireUniqueIds(
                bookings.stream().map(AllocationBooking::bookingId).toList(),
                "booking"
        );
        requireUniqueIds(
                seats.stream().map(AllocationSeat::seatId).toList(),
                "seat"
        );
    }

    private void requireUniqueIds(List<Long> ids, String label) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate " + label + " IDs are not allowed");
        }
    }

    private void requireCapacity(
            List<AllocationBooking> bookings,
            List<AllocationSeat> usableSeats
    ) {
        if (usableSeats.size() < bookings.size()) {
            throw new AllocationException("Not enough unblocked seats for all bookings");
        }
        long accessibleBookings = bookings.stream()
                .filter(AllocationBooking::accessibilityRequired)
                .count();
        long accessibleSeats = usableSeats.stream()
                .filter(AllocationSeat::accessible)
                .count();
        if (accessibleSeats < accessibleBookings) {
            throw new AllocationException("Not enough accessible seats for accessible bookings");
        }
    }

    private List<BookingGroup> groupBookings(List<AllocationBooking> bookings) {
        Map<String, List<AllocationBooking>> grouped = new HashMap<>();
        for (AllocationBooking booking : bookings) {
            String key = booking.groupCode() == null
                    ? "SOLO:" + booking.bookingId()
                    : "GROUP:" + booking.groupCode();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(booking);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<AllocationBooking> members = entry.getValue().stream()
                            .sorted(BOOKING_ORDER)
                            .toList();
                    return new BookingGroup(
                            entry.getKey(),
                            members.get(0).groupCode(),
                            members
                    );
                })
                .sorted(Comparator
                        .comparingInt((BookingGroup group) -> group.bookings().size())
                        .reversed()
                        .thenComparing(BookingGroup::key, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<AllocationSeat> findContiguousBlock(
            BookingGroup group,
            List<AllocationSeat> seats,
            long maxAccessible,
            List<AllocationSeat> anchors
    ) {
        return findContiguousSeats(group.bookings().size(), accessibleCount(group), seats, maxAccessible, anchors);
    }

    private List<AllocationSeat> findContiguousSeats(int pendingCount, long accessibleNeeded,
            List<AllocationSeat> seats, long maxAccessible, List<AllocationSeat> anchors) {
        int size = pendingCount + anchors.size();
        List<List<AllocationSeat>> candidates = new ArrayList<>();

        Map<String, List<AllocationSeat>> rows = new LinkedHashMap<>();
        var combined = new ArrayList<>(seats);
        combined.addAll(anchors);
        combined.stream().sorted(SEAT_ORDER).forEach(seat -> {
            String rowKey = seat.elementId() + "\u0000" + seat.section().toUpperCase(Locale.ROOT)
                    + "\u0000" + seat.rowIndex() + "\u0000" + seat.rowLabel().toUpperCase(Locale.ROOT);
            rows.computeIfAbsent(rowKey, ignored -> new ArrayList<>()).add(seat);
        });

        for (List<AllocationSeat> row : rows.values()) {
            for (int start = 0; start + size <= row.size(); start++) {
                List<AllocationSeat> block = row.subList(start, start + size);
                var unassigned = block.stream().filter(seat -> !anchors.contains(seat)).toList();
                if (block.containsAll(anchors) && isConsecutive(block)
                        && accessibleCount(unassigned) >= accessibleNeeded && accessibleCount(unassigned) <= maxAccessible) {
                    candidates.add(unassigned);
                }
            }
        }

        return candidates.stream()
                .min(candidateComparator(accessibleNeeded, seats))
                .orElseGet(List::of);
    }

    private boolean isConsecutive(List<AllocationSeat> seats) {
        for (int index = 1; index < seats.size(); index++) {
            if (seats.get(index).seatNumber() != seats.get(index - 1).seatNumber() + 1) {
                return false;
            }
        }
        return true;
    }

    private Comparator<List<AllocationSeat>> candidateComparator(long accessibleNeeded, List<AllocationSeat> available) {
        return Comparator
                .comparingLong((List<AllocationSeat> candidate) ->
                        accessibleCount(candidate) - accessibleNeeded)
                .thenComparing(candidate -> candidate.get(0), SEAT_ORDER);
    }

    private List<AllocationSeat> findNearestCluster(
            BookingGroup group,
            List<AllocationSeat> seats,
            long maxAccessible,
            List<AllocationSeat> anchors
    ) {
        int size = group.bookings().size();
        long accessibleNeeded = accessibleCount(group);
        List<AllocationSeat> ordered = seats.stream().sorted(SEAT_ORDER).toList();
        List<ClusterCandidate> candidates = new ArrayList<>();

        for (int start = 0; start + size <= ordered.size(); start++) {
            List<AllocationSeat> cluster = List.copyOf(ordered.subList(start, start + size));
            if (accessibleCount(cluster) >= accessibleNeeded && accessibleCount(cluster) <= maxAccessible) {
                candidates.add(new ClusterCandidate(cluster, clusterPenalty(cluster) + anchorPenalty(cluster, anchors)));
            }
        }

        List<AllocationSeat> nearest = candidates.stream()
                .min(Comparator
                        .comparingLong(ClusterCandidate::penalty)
                        .thenComparing(candidate -> candidate.seats().get(0), SEAT_ORDER))
                .map(ClusterCandidate::seats)
                .orElseGet(List::of);
        if (!nearest.isEmpty()) {
            return nearest;
        }

        // Accessible seats can be separated by more than one row. Capacity was
        // validated up front, so build a deterministic fallback rather than
        // failing a group that still has a valid (but necessarily split) result.
        List<AllocationSeat> fallback = new ArrayList<>(ordered.stream()
                .filter(AllocationSeat::accessible)
                .limit(accessibleNeeded)
                .toList());
        ordered.stream()
                .filter(seat -> !seat.accessible())
                .filter(seat -> !fallback.contains(seat))
                .limit(size - fallback.size())
                .forEach(fallback::add);
        ordered.stream().filter(AllocationSeat::accessible).filter(seat -> !fallback.contains(seat))
                .limit(size - fallback.size()).forEach(fallback::add);
        return fallback.stream().sorted(SEAT_ORDER).toList();
    }

    private long clusterPenalty(List<AllocationSeat> seats) {
        AllocationSeat first = seats.get(0);
        long penalty = 0;
        for (AllocationSeat seat : seats) {
            if (!first.section().equalsIgnoreCase(seat.section())) {
                penalty += 1_000_000;
            }
            penalty += Math.abs(first.rowIndex() - seat.rowIndex()) * 10_000L;
            penalty += Math.abs(first.seatNumber() - seat.seatNumber());
        }
        return penalty;
    }

    private long anchorPenalty(List<AllocationSeat> selected, List<AllocationSeat> anchors) {
        long penalty = 0;
        for (var seat : selected) for (var anchor : anchors) {
            if (!seat.section().equalsIgnoreCase(anchor.section())) penalty += 1_000_000;
            penalty += Math.abs(seat.rowIndex() - anchor.rowIndex()) * 10_000L;
            penalty += Math.abs(seat.seatNumber() - anchor.seatNumber());
        }
        return penalty;
    }

    private List<SeatAssignment> assignBookings(
            BookingGroup group,
            List<AllocationSeat> selected,
            boolean split
    ) {
        List<AllocationSeat> accessibleSeats = selected.stream()
                .filter(AllocationSeat::accessible)
                .sorted(SEAT_ORDER)
                .toList();
        List<AllocationSeat> regularSeats = selected.stream()
                .filter(seat -> !seat.accessible())
                .sorted(SEAT_ORDER)
                .toList();
        List<AllocationSeat> remainingAccessible = new ArrayList<>(accessibleSeats);
        List<AllocationSeat> remainingRegular = new ArrayList<>(regularSeats);
        List<SeatAssignment> result = new ArrayList<>();

        for (AllocationBooking booking : group.bookings()) {
            AllocationSeat seat;
            if (booking.accessibilityRequired()) {
                seat = remainingAccessible.remove(0);
            } else if (!remainingRegular.isEmpty()) {
                seat = remainingRegular.remove(0);
            } else {
                seat = remainingAccessible.remove(0);
            }
            result.add(new SeatAssignment(
                    booking.bookingId(),
                    seat.seatId(),
                    booking.groupCode(),
                    split
            ));
        }
        return result;
    }

    private long accessibleCount(BookingGroup group) {
        return group.bookings().stream()
                .filter(AllocationBooking::accessibilityRequired)
                .count();
    }

    private long accessibleCount(List<AllocationSeat> seats) {
        return seats.stream().filter(AllocationSeat::accessible).count();
    }

    private record BookingGroup(
            String key,
            String groupCode,
            List<AllocationBooking> bookings
    ) {
        private String displayName() {
            return groupCode == null ? key : groupCode;
        }
    }

    private record ClusterCandidate(List<AllocationSeat> seats, long penalty) {
    }
}
