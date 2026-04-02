-- PostgreSQL DDL for the `routes` table (run manually or wire up Flyway if added later).
-- Column names match JPA @AttributeOverrides and Route field mappings.

CREATE TABLE routes (
    id              BIGSERIAL PRIMARY KEY,
    start_latitude  DOUBLE PRECISION NOT NULL,
    start_longitude DOUBLE PRECISION NOT NULL,
    end_latitude    DOUBLE PRECISION NOT NULL,
    end_longitude   DOUBLE PRECISION NOT NULL,
    distance        INTEGER NOT NULL,
    duration        INTEGER NOT NULL,
    travel_mode     VARCHAR(32) NOT NULL
);
