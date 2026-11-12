-- Preserve cancelled tickets as history while allowing refunded seats to be resold.
-- An event seat may still have at most one currently issued ticket.
ALTER TABLE tickets DROP CONSTRAINT tickets_event_seat_id_key;

CREATE UNIQUE INDEX uq_tickets_issued_event_seat
    ON tickets(event_seat_id) WHERE status = 'ISSUED';

CREATE INDEX idx_tickets_event_seat ON tickets(event_seat_id);
