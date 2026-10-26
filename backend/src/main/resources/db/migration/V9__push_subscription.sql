-- browsers where someone turned on push notifications (the Push API's PushSubscription), one row per browser;
-- the push service's address is unique, so a browser that changes hands belongs to whoever subscribed last
CREATE TABLE push_subscription (
    id              UUID PRIMARY KEY,
    recipient_id    UUID NOT NULL,                 -- Keycloak subject
    endpoint        VARCHAR(1024) NOT NULL UNIQUE, -- the push service's URL for this browser
    p256dh          VARCHAR(100) NOT NULL,         -- the browser's P-256 public key, base64url
    auth            VARCHAR(50) NOT NULL,          -- the browser's authentication secret, base64url
    user_agent      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ                    -- the push service last accepted a message for it
);
CREATE INDEX push_subscription_recipient_idx ON push_subscription (recipient_id);
