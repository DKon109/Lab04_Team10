package com.ooaf.eventticketing.allocation;

/** Preferences apply only after hard constraints and the full-group contiguous search. */
public enum AllocationStrategy {
    /** Balance row subgroup sizes with soft penalties for extra rows, fragments and weighted distance. */
    DEFAULT,
    /** Keep one row when feasible; otherwise prefer fewer contiguous row subgroups, balanced in size. */
    PREFER_SAME_ROW
}
