-- someone's choice for one type and channel, kept only where it differs from the type's default
-- (NotificationType in the backend)
CREATE TABLE notification_preference (
    recipient_id UUID NOT NULL,                    -- Keycloak subject
    type         VARCHAR(40) NOT NULL,
    channel      VARCHAR(16) NOT NULL,             -- EMAIL or PUSH; the in-app list can't be turned off
    enabled      BOOLEAN NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (recipient_id, type, channel)
);

-- for the retention purge (NotificationPurge)
CREATE INDEX notification_created_idx ON notification (created_at);
CREATE INDEX notification_delivery_created_idx ON notification_delivery (created_at);
