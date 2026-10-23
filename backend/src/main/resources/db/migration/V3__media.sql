-- uploaded photos and videos; the bytes live in the object store under media/<id>
CREATE TABLE media (
    id           UUID PRIMARY KEY,
    owner_id     UUID NOT NULL,                -- Keycloak subject of the uploader
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    status       VARCHAR(16) NOT NULL,         -- PENDING until the upload is verified, then READY
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX media_pending_idx ON media (created_at) WHERE status = 'PENDING';

-- optional profile photo
ALTER TABLE employee ADD COLUMN photo_id UUID REFERENCES media (id) ON DELETE SET NULL;
