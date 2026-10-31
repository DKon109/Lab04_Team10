package com.ooaf.eventticketing.dto;

import com.ooaf.eventticketing.allocation.AllocationEvaluation;

/** Existing evaluation fields plus the persisted runtime of the original allocation. */
public record AllocationMetricsResponse(Integer groupCount, Integer splitGroupCount,
        Double percentageGroupsTogether, Double averageSplitDistance, Double averageGroupContiguityPercentage,
        Integer isolatedSeatCount, int duplicateAllocationCount, long elapsedMillis) {
    public static AllocationMetricsResponse from(AllocationEvaluation evaluation, long elapsedMillis) {
        return new AllocationMetricsResponse(evaluation.groupCount(), evaluation.splitGroupCount(),
                evaluation.percentageGroupsTogether(), evaluation.averageSplitDistance(),
                evaluation.averageGroupContiguityPercentage(), evaluation.isolatedSeatCount(),
                evaluation.duplicateAllocationCount(), elapsedMillis);
    }
}
