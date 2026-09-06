CREATE TABLE payment_webhook_receipts (
    id UUID PRIMARY KEY,
    provider_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(120) NOT NULL,
    payment_reference VARCHAR(120),
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_webhook_reference ON payment_webhook_receipts(payment_reference);
