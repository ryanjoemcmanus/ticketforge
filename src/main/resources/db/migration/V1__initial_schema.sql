CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','ORGANIZER','ADMIN')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE venues (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(240) NOT NULL,
    city VARCHAR(120) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL REFERENCES venues(id),
    section_name VARCHAR(80) NOT NULL,
    row_label VARCHAR(40) NOT NULL,
    seat_number VARCHAR(40) NOT NULL,
    UNIQUE (venue_id, section_name, row_label, seat_number)
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES app_users(id),
    venue_id UUID NOT NULL REFERENCES venues(id),
    title VARCHAR(180) NOT NULL,
    description VARCHAR(2000),
    starts_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED','COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE seat_holds (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_users(id),
    event_id UUID NOT NULL REFERENCES events(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED','CHECKED_OUT')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE event_seats (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    seat_id UUID NOT NULL REFERENCES seats(id),
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE','HELD','SOLD')),
    hold_id UUID REFERENCES seat_holds(id),
    hold_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (event_id, seat_id),
    CHECK ((status = 'HELD' AND hold_id IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR (status <> 'HELD' AND hold_id IS NULL AND hold_expires_at IS NULL))
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_users(id),
    hold_id UUID NOT NULL UNIQUE REFERENCES seat_holds(id),
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PAYMENT_CONFIRMED','REFUNDED','CANCELLED')),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    payment_reference VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (customer_id, idempotency_key)
);

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    event_seat_id UUID NOT NULL UNIQUE REFERENCES event_seats(id),
    ticket_code VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ISSUED','CANCELLED')),
    issued_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_events_starts_at ON events(starts_at);
CREATE INDEX idx_event_seats_event_status ON event_seats(event_id, status);
CREATE INDEX idx_event_seats_expiry ON event_seats(hold_expires_at) WHERE status = 'HELD';
CREATE INDEX idx_holds_customer ON seat_holds(customer_id, created_at DESC);
CREATE INDEX idx_orders_customer ON orders(customer_id, created_at DESC);
