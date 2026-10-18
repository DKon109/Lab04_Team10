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
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.stream.Collectors;

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
        return allocate(bookings, seats, AllocationStrategy.DEFAULT);
    }

    public AllocationResult allocate(List<AllocationBooking> bookings, List<AllocationSeat> seats,
            AllocationStrategy strategy) {
        java.util.Objects.requireNonNull(strategy, "strategy is required");
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
                selected = findNearestCluster(group, currentSeats, maxAccessible, anchors, strategy);
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
                .thenComparingInt(candidate -> AllocationTopology.isolatedSeats(available,
                        candidate.stream().map(AllocationSeat::seatId).collect(Collectors.toSet())))
                .thenComparing(candidate -> candidate.get(0), SEAT_ORDER);
    }

    private List<AllocationSeat> findNearestCluster(
            BookingGroup group,
            List<AllocationSeat> seats,
            long maxAccessible,
            List<AllocationSeat> anchors,
            AllocationStrategy strategy
    ) {
        int size = group.bookings().size();
        long accessibleNeeded = accessibleCount(group);
        List<AllocationSeat> ordered = seats.stream().sorted(SEAT_ORDER).toList();
        List<ClusterCandidate> candidates = new ArrayList<>();

        // Retain consecutive windows and add neighbourhoods so fixed group members
        // and same-row seats are considered even when sections interrupt sort order.
        for (int start = 0; start + size <= ordered.size(); start++) {
            addCluster(candidates, ordered.subList(start, start + size), anchors, seats,
                    accessibleNeeded, maxAccessible);
        }
        for (var seed : ordered) {
            var neighbourhood = ordered.stream().sorted(Comparator
                    .comparingInt((AllocationSeat seat) -> anchors.stream()
                            .anyMatch(anchor -> AllocationTopology.samePhysicalRow(anchor, seat)) ? 0 : 1)
                    .thenComparingInt(seat -> AllocationTopology.samePhysicalRow(seed, seat) ? 0 : 1)
                    .thenComparingDouble(seat -> seed.distanceTo(seat)
                            + anchors.stream().mapToDouble(seat::distanceTo).sum())
                    .thenComparing(SEAT_ORDER)).toList();
            var selected = selectFeasible(neighbourhood, size, accessibleNeeded, maxAccessible);
            if (selected.size() == size) addCluster(candidates, selected, anchors, seats,
                    accessibleNeeded, maxAccessible);
        }
        // Whole runs can produce two compact subgroups where nearest-seat growth
        // would otherwise choose three fragments (for example AAA-gap-A plus BBB).
        var blocks = AllocationTopology.blocks(ordered);
        for (var seedBlock : blocks) {
            var byBlock = blocks.stream().sorted(Comparator
                    .comparingInt((List<AllocationSeat> block) -> block == seedBlock ? 0 : 1)
                    .thenComparing(Comparator.comparingInt((List<AllocationSeat> block) -> block.size()).reversed())
                    .thenComparingDouble(block -> seedBlock.get(0).distanceTo(block.get(0)))
                    .thenComparing(block -> block.get(0), SEAT_ORDER))
                    .flatMap(List::stream).toList();
            var selected = selectFeasible(byBlock, size, accessibleNeeded, maxAccessible);
            if (selected.size() == size) addCluster(candidates, selected, anchors, seats,
                    accessibleNeeded, maxAccessible);
        }
        addBalancedClusters(candidates, ordered, anchors, size, accessibleNeeded, maxAccessible);
        Comparator<ClusterCandidate> ranking;
        if (strategy == AllocationStrategy.PREFER_SAME_ROW) {
            ranking = Comparator.comparingInt((ClusterCandidate candidate) -> candidate.rows() == 1 ? 0 : 1)
                    .thenComparingInt(candidate -> candidate.blocks() - candidate.rows())
                    .thenComparingInt(ClusterCandidate::rows)
                    .thenComparingInt(ClusterCandidate::imbalance)
                    .thenComparingDouble(ClusterCandidate::distance);
        } else {
            // Soft trade-off: extra rows cost 1.5, imbalance and extra fragments cost 1 each.
            ranking = Comparator.comparingDouble(this::balancedSplitPenalty)
                    .thenComparingInt(ClusterCandidate::rows)
                    .thenComparingInt(ClusterCandidate::blocks)
                    .thenComparingDouble(ClusterCandidate::distance);
        }
        return candidates.stream().min(ranking
                        .thenComparingInt(ClusterCandidate::isolated)
                        .thenComparingLong(ClusterCandidate::excessAccessible)
                        .thenComparing(ClusterCandidate::seats, this::compareSeatLists))
                .map(ClusterCandidate::seats).orElseGet(List::of);
    }

    /** Water-fill row quotas around fixed members, then choose valid seats for each quota. */
    private void addBalancedClusters(List<ClusterCandidate> candidates, List<AllocationSeat> available,
            List<AllocationSeat> anchors, int pendingCount, long accessibleNeeded, long maxAccessible) {
        Map<String, List<AllocationSeat>> freeRows = available.stream().collect(Collectors.groupingBy(
                AllocationTopology::physicalRowKey, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<AllocationSeat>> fixedRows = anchors.stream().collect(Collectors.groupingBy(
                AllocationTopology::physicalRowKey, LinkedHashMap::new, Collectors.toList()));
        var keys = new LinkedHashSet<>(freeRows.keySet()); keys.addAll(fixedRows.keySet());
        var rows = keys.stream().map(key -> new RowOption(key, freeRows.getOrDefault(key, List.of()),
                fixedRows.getOrDefault(key, List.of()))).toList();

        int total = pendingCount + anchors.size();
        for (var seed : rows) {
            var orderedRows = rows.stream().sorted(Comparator
                    .comparingInt((RowOption row) -> row.fixed().isEmpty() ? 1 : 0)
                    .thenComparingInt(row -> row == seed ? 0 : 1)
                    .thenComparing(Comparator.comparingInt(RowOption::capacity).reversed())
                    .thenComparingDouble(row -> seed.reference().distanceTo(row.reference()))
                    .thenComparing(RowOption::key)).toList();
            for (int count = Math.max(1, fixedRows.size()); count <= Math.min(rows.size(), total); count++) {
                var chosen = orderedRows.subList(0, count);
                if (chosen.stream().mapToInt(RowOption::capacity).sum() < total) continue;
                int[] quotas = chosen.stream().mapToInt(row -> Math.max(1, row.fixed().size())).toArray();
                int remaining = total - Arrays.stream(quotas).sum();
                if (remaining < 0) continue;
                while (remaining > 0) {
                    int next = -1;
                    for (int i = 0; i < count; i++) {
                        if (quotas[i] < chosen.get(i).capacity() && (next < 0 || quotas[i] < quotas[next])) next = i;
                    }
                    if (next < 0) break;
                    quotas[next]++; remaining--;
                }
                if (remaining > 0) continue;
                long[] required = new long[count];
                long maximum = 0, minimum = 0;
                for (int i = 0; i < count; i++) {
                    var row = chosen.get(i);
                    int pending = quotas[i] - row.fixed().size();
                    long regular = row.free().size() - accessibleCount(row.free());
                    required[i] = Math.max(0, pending - regular);
                    minimum += required[i];
                    maximum += Math.min(pending, accessibleCount(row.free()));
                }
                if (minimum > maxAccessible || maximum < accessibleNeeded) continue;
                long extra = Math.max(0, accessibleNeeded - minimum);
                for (int i = 0; i < count && extra > 0; i++) {
                    var row = chosen.get(i);
                    long room = Math.min(quotas[i] - row.fixed().size(), accessibleCount(row.free())) - required[i];
                    long take = Math.min(room, extra); required[i] += take; extra -= take;
                }
                var selected = new ArrayList<AllocationSeat>();
                for (int i = 0; i < count; i++) {
                    var row = chosen.get(i);
                    int pending = quotas[i] - row.fixed().size();
                    if (pending == 0) continue;
                    var inRow = findContiguousSeats(pending, required[i], row.free(), required[i], row.fixed());
                    if (inRow.isEmpty()) inRow = selectFeasible(row.free(), pending, required[i], required[i]);
                    selected.addAll(inRow);
                }
                if (selected.size() == pendingCount) addCluster(candidates, selected, anchors, available,
                        accessibleNeeded, maxAccessible);
            }
        }
    }

    private double balancedSplitPenalty(ClusterCandidate candidate) {
        double pairs = (double) candidate.members() * (candidate.members() - 1) / 2;
        double averageDistance = pairs == 0 ? 0 : candidate.distance() / pairs;
        return AllocationSeat.ROW_PENALTY * (candidate.rows() - 1)
                + candidate.imbalance() + candidate.blocks() - candidate.rows() + averageDistance;
    }

    private void addCluster(List<ClusterCandidate> candidates, List<AllocationSeat> selected,
            List<AllocationSeat> anchors, List<AllocationSeat> available, long accessibleNeeded, long maxAccessible) {
        long accessible = accessibleCount(selected);
        if (accessible < accessibleNeeded || accessible > maxAccessible) return;
        var whole = new ArrayList<>(anchors);
        whole.addAll(selected);
        candidates.add(new ClusterCandidate(selected.stream().sorted(SEAT_ORDER).toList(),
                AllocationTopology.rowsUsed(whole), AllocationTopology.blockCount(whole),
                AllocationTopology.rowSizeImbalance(whole), whole.size(), AllocationTopology.pairwiseDistance(whole),
                AllocationTopology.isolatedSeats(available,
                        selected.stream().map(AllocationSeat::seatId).collect(Collectors.toSet())),
                accessible - accessibleNeeded));
    }

    private List<AllocationSeat> selectFeasible(List<AllocationSeat> ordered, int size,
            long accessibleNeeded, long maxAccessible) {
        var selected = new ArrayList<>(ordered.stream().filter(AllocationSeat::accessible)
                .limit(accessibleNeeded).toList());
        ordered.stream().filter(seat -> !seat.accessible()).limit(size - selected.size()).forEach(selected::add);
        long extraAccessible = Math.min(size - selected.size(), maxAccessible - accessibleNeeded);
        ordered.stream().filter(AllocationSeat::accessible).filter(seat -> !selected.contains(seat))
                .limit(extraAccessible).forEach(selected::add);
        return selected;
    }

    private int compareSeatLists(List<AllocationSeat> first, List<AllocationSeat> second) {
        for (int i = 0; i < first.size(); i++) {
            int order = SEAT_ORDER.compare(first.get(i), second.get(i));
            if (order != 0) return order;
        }
        return 0;
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

    private record RowOption(String key, List<AllocationSeat> free, List<AllocationSeat> fixed) {
        int capacity() { return free.size() + fixed.size(); }
        AllocationSeat reference() { return fixed.isEmpty() ? free.get(0) : fixed.get(0); }
    }

    private record ClusterCandidate(List<AllocationSeat> seats, int rows, int blocks, int imbalance,
            int members, double distance, int isolated, long excessAccessible) {
    }
}
