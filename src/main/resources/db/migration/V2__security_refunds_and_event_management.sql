ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN organizer_approved BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE app_users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE events ADD COLUMN category VARCHAR(80) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE orders ADD COLUMN refunded_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(500);
ALTER TABLE tickets ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE account_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_type VARCHAR(30) NOT NULL CHECK (token_type IN ('EMAIL_VERIFICATION','PASSWORD_RESET')),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES app_users(id),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_action_tokens_user_type ON account_action_tokens(user_id, token_type);
CREATE INDEX idx_events_search ON events(status, category, starts_at);
CREATE INDEX idx_audit_entity ON audit_events(entity_type, entity_id, created_at);
