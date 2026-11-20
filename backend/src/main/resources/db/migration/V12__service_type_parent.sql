-- a sub-treatment can only be booked alongside one of its parents; only one level deep
CREATE TABLE service_type_parent (
    service_type_id UUID NOT NULL REFERENCES service_type (id) ON DELETE CASCADE,
    parent_id       UUID NOT NULL REFERENCES service_type (id),
    PRIMARY KEY (service_type_id, parent_id),
    CHECK (service_type_id <> parent_id)
);

CREATE INDEX service_type_parent_parent_idx ON service_type_parent (parent_id);
