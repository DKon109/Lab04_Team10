# Tixora database model (Sprint 3)

```mermaid
erDiagram
    USERS ||--o{ AUTHENTICATION_TOKENS : owns
    USERS ||--o{ EVENTS : organises
    USERS ||--o{ EVENT_LIKES : likes
    EVENTS ||--o{ EVENT_LIKES : receives
    EVENTS ||--o{ EVENT_TAGS : has
    USERS ||--o{ BOOKINGS : makes
    EVENTS ||--o{ BOOKINGS : accepts
    EVENTS ||--o| SEATING_LAYOUTS : configures
    EVENTS ||--o| ALLOCATION_RUNS : records
    EVENTS ||--o{ CHECKOUT_ATTEMPTS : audits
    BOOKINGS o|--o| CHECKOUT_ATTEMPTS : created_by

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar role
        varchar society_name
    }
    AUTHENTICATION_TOKENS {
        bigint id PK
        varchar token_hash UK
        bigint user_id FK
        timestamp expires_at
    }
    EVENTS {
        bigint id PK
        bigint owner_id FK
        varchar status
        varchar title
        timestamp date_time
        varchar venue
        varchar university
        varchar organiser_name
        varchar organiser_type
        varchar category
        integer capacity
        varchar registration_status
        boolean seat_identifiers_confirmed
    }
    EVENT_TAGS {
        bigint event_id FK
        varchar tag
    }
    EVENT_LIKES {
        bigint id PK
        bigint user_id FK
        bigint event_id FK
        timestamp created_at
    }
    BOOKINGS {
        bigint id PK
        varchar booking_ref UK
        bigint event_id FK
        bigint user_id FK
        varchar group_code
        boolean accessibility_required
        varchar idempotency_key UK
        varchar status
        timestamp created_at
        varchar seat_code
        varchar split_reason
    }
    CHECKOUT_ATTEMPTS {
        bigint id PK
        varchar idempotency_key UK
        varchar payment_ref UK
        bigint user_id
        bigint event_id
        varchar fingerprint
        varchar outcome
        bigint booking_id
        timestamp created_at
    }
    SEATING_LAYOUTS {
        bigint event_id PK
        varchar source
        varchar template
        varchar room_id
        integer row_count
        integer seats_per_row
        integer aisle_after
        varchar blocked_codes
        varchar accessible_codes
        integer layout_version
    }
    ALLOCATION_RUNS {
        bigint event_id PK
        integer layout_version
        integer booking_count
        integer split_group_count
        bigint elapsed_millis
        timestamp completed_at
    }
```

`EVENT_LIKES` also has a unique constraint on `(user_id, event_id)`. `BOOKINGS` has a unique constraint on `(event_id, seat_code)` to prevent duplicate seat assignments. Group codes are resolved within an event; there is no separate group table. `CHECKOUT_ATTEMPTS` stores scalar user, event and booking IDs, and `SEATING_LAYOUTS` and `ALLOCATION_RUNS` use the event ID as their primary key; their diagram links express the application relationship, not database foreign-key constraints. Historical bookings may have no checkout attempt.
