-- one row per notification and outside channel (email for now); the in-app list needs none
CREATE TABLE notification_delivery (
    id              UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    channel         VARCHAR(16) NOT NULL,          -- EMAIL
    status          VARCHAR(16) NOT NULL,          -- PENDING, SENT, SKIPPED (nowhere to send it) or FAILED (gave up)
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,          -- when a PENDING delivery is due (again)
    last_error      VARCHAR(500),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INTEGER NOT NULL DEFAULT 0,
    UNIQUE (notification_id, channel)
);
CREATE INDEX notification_delivery_due_idx ON notification_delivery (next_attempt_at) WHERE status = 'PENDING';
