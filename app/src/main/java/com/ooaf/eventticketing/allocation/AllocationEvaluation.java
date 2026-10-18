package com.ooaf.eventticketing.allocation;

import java.util.*;
import java.util.stream.Collectors;

/** Evaluation in logical row/seat steps, not physical distance. Only named groups
 * with at least two confirmed members enter group metrics. No groups => null percentage.
 * General admission => null seat/group metrics. A split's distance includes all
 * unordered member pairs, averaged across all split-group pairs. Mean group contiguity
 * averages retained adjacent connections across groups with at least two members. Duplicate count is
 * the sum of excess booking assignments and excess seat assignments.
 */
public record AllocationEvaluation(Integer groupCount, Integer splitGroupCount,
        Double percentageGroupsTogether, Double averageSplitDistance, Double averageGroupContiguityPercentage,
        Integer isolatedSeatCount, int duplicateAllocationCount) {

    public static AllocationEvaluation generalAdmission() {
        return new AllocationEvaluation(null, null, null, null, null, null, 0);
    }

    public static AllocationEvaluation evaluate(List<AllocationBooking> bookings,
            List<AllocationSeat> seats, List<SeatAssignment> assignments) {
        var seatById = seats.stream().collect(Collectors.toMap(AllocationSeat::seatId, s -> s));
        Map<Long, AllocationSeat> assigned = new HashMap<>();
        Set<Long> occupied = new HashSet<>();
        int duplicates = 0;
        for (var assignment : assignments) {
            var seat = seatById.get(assignment.seatId());
            if (seat == null) throw new AllocationException("Assigned seat does not exist");
            if (assigned.put(assignment.bookingId(), seat) != null) duplicates++;
            if (!occupied.add(assignment.seatId())) duplicates++;
        }
        Map<String, List<AllocationSeat>> groups = new HashMap<>();
        for (var booking : bookings) {
            var seat = assigned.get(booking.bookingId());
            if (seat == null) throw new AllocationException("Confirmed booking has no assignment");
            if (booking.groupCode() != null) groups.computeIfAbsent(booking.groupCode(), ignored -> new ArrayList<>()).add(seat);
        }
        int groupCount = 0, splitCount = 0;
        double distance = 0, contiguity = 0;
        long pairs = 0;
        for (var group : groups.values()) {
            if (group.size() < 2) continue;
            groupCount++;
            contiguity += AllocationTopology.contiguityPercentage(group);
            if (AllocationTopology.contiguous(group)) continue;
            splitCount++;
            for (int i = 0; i < group.size(); i++) for (int j = i + 1; j < group.size(); j++) {
                distance += group.get(i).distanceTo(group.get(j)); pairs++;
            }
        }
        return new AllocationEvaluation(groupCount, splitCount,
                groupCount == 0 ? null : 100.0 * (groupCount - splitCount) / groupCount,
                pairs == 0 ? 0.0 : distance / pairs, groupCount == 0 ? null : contiguity / groupCount,
                AllocationTopology.isolatedSeats(seats, occupied), duplicates);
    }
}
