package com.ooaf.eventticketing.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for a user's like on an event.
 */
public class EventLikeId implements Serializable {

    private Long user;
    private Long event;

    public EventLikeId() {
    }

    public EventLikeId(Long user, Long event) {
        this.user = user;
        this.event = event;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EventLikeId that)) {
            return false;
        }
        return Objects.equals(user, that.user) && Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, event);
    }
}
