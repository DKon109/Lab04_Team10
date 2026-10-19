package com.ooaf.eventticketing.entity;

/** Elements supported by the backend seating-map builder. */
public enum SeatingElementType {
    ROW,
    ROUND_TABLE,
    SQUARE_TABLE,
    RECTANGULAR_TABLE,
    AREA,
    OBJECT,
    TEXT;

    public boolean isTable() {
        return this == ROUND_TABLE || this == SQUARE_TABLE || this == RECTANGULAR_TABLE;
    }
}
