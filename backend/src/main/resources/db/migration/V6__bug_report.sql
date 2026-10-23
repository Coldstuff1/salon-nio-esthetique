-- problems people report from the site; the reporter is copied from their token when they send it
CREATE TABLE bug_report (
    id                UUID PRIMARY KEY,
    reporter_id       UUID NOT NULL,                -- Keycloak subject of the reporter
    reporter_username VARCHAR(255) NOT NULL,
    reporter_name     VARCHAR(255) NOT NULL,
    reporter_email    VARCHAR(255),
    reporter_roles    VARCHAR(64) NOT NULL,         -- app realm roles, comma-separated, e.g. 'client'
    title             VARCHAR(120) NOT NULL,
    description       TEXT NOT NULL,
    page_path         VARCHAR(2048) NOT NULL,       -- route the reporter was on
    user_agent        VARCHAR(512),
    locale            VARCHAR(10),
    viewport          VARCHAR(20),                  -- e.g. 390x844
    app_version       VARCHAR(40),
    screenshot_id     UUID REFERENCES media (id) ON DELETE SET NULL,
    status            VARCHAR(16) NOT NULL,         -- NEW, IN_PROGRESS, RESOLVED or DISMISSED
    admin_note        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,                  -- last time an admin changed the status or note
    version           INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX bug_report_reporter_idx ON bug_report (reporter_id, created_at);
