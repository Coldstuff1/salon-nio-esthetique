-- who can be notified, copied from their token each time the site loads (GET /api/me); employees and admins
-- have no other row with their email or roles
CREATE TABLE notification_recipient (
    id           UUID PRIMARY KEY,                 -- Keycloak subject
    email        VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    roles        VARCHAR(64) NOT NULL,             -- app realm roles, comma-separated, e.g. 'employee,admin'
    locale       VARCHAR(10) NOT NULL DEFAULT 'fr',
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- what the bell lists; the client renders the text from the type and params in the reader's language
CREATE TABLE notification (
    id           UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,                    -- Keycloak subject; no foreign key, any signed-in user can get one
    type         VARCHAR(40) NOT NULL,
    params       TEXT NOT NULL,                    -- JSON object of strings, e.g. {"startsAt": "…", "employeeName": "…"}
    link         VARCHAR(255),                     -- site path to open, e.g. /account/bookings/<id>
    dedupe_key   VARCHAR(200) NOT NULL UNIQUE,     -- the same occurrence never notifies twice, even when replayed
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at      TIMESTAMPTZ
);
CREATE INDEX notification_recipient_idx ON notification (recipient_id, created_at DESC);
CREATE INDEX notification_unread_idx ON notification (recipient_id) WHERE read_at IS NULL;
