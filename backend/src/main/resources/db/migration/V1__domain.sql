CREATE EXTENSION IF NOT EXISTS btree_gist;

-- catalog: shared definitions managed by admins

CREATE TABLE service_type (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    category    VARCHAR(60),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    version     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE product (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    sku         VARCHAR(60) NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    version     INTEGER NOT NULL DEFAULT 0
);

-- people: ids are the Keycloak subject

CREATE TABLE customer (
    id           UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email        VARCHAR(255),
    phone        VARCHAR(30),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE employee (
    id           UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    bio          TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    version      INTEGER NOT NULL DEFAULT 0
);

-- recurring weekly hours, interpreted in the salon time zone; day_of_week is ISO (1 = Monday)
CREATE TABLE availability (
    employee_id UUID NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    CHECK (start_time < end_time)
);
CREATE INDEX availability_employee_idx ON availability (employee_id);

CREATE TABLE time_off (
    id          UUID NOT NULL,
    employee_id UUID NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    reason      VARCHAR(255),
    CHECK (starts_at < ends_at)
);
CREATE INDEX time_off_employee_idx ON time_off (employee_id);

CREATE TABLE service_offering (
    employee_id      UUID NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    service_type_id  UUID NOT NULL REFERENCES service_type (id),
    price_cents      INTEGER NOT NULL CHECK (price_cents >= 0),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (employee_id, service_type_id)
);

CREATE TABLE product_offering (
    employee_id      UUID NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    product_id       UUID NOT NULL REFERENCES product (id),
    price_cents      INTEGER NOT NULL CHECK (price_cents >= 0),
    quantity_on_hand INTEGER NOT NULL CHECK (quantity_on_hand >= 0),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (employee_id, product_id)
);

-- bookings

CREATE TABLE booking (
    id             UUID PRIMARY KEY,
    customer_id    UUID NOT NULL REFERENCES customer (id),
    employee_id    UUID NOT NULL REFERENCES employee (id),
    starts_at      TIMESTAMPTZ NOT NULL,
    ends_at        TIMESTAMPTZ NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    subtotal_cents INTEGER NOT NULL CHECK (subtotal_cents >= 0),
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INTEGER NOT NULL DEFAULT 0,
    CHECK (starts_at < ends_at),
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (
        employee_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status = 'RESERVED')
);
CREATE INDEX booking_customer_idx ON booking (customer_id, starts_at);

-- line items keep a snapshot of name and price at booking time

CREATE TABLE booking_service (
    booking_id       UUID NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    position         INTEGER NOT NULL,
    service_type_id  UUID NOT NULL REFERENCES service_type (id),
    name_snapshot    VARCHAR(120) NOT NULL,
    price_cents      INTEGER NOT NULL,
    duration_minutes INTEGER NOT NULL,
    PRIMARY KEY (booking_id, position)
);

CREATE TABLE booking_product (
    booking_id       UUID NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    product_id       UUID NOT NULL REFERENCES product (id),
    name_snapshot    VARCHAR(120) NOT NULL,
    unit_price_cents INTEGER NOT NULL,
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (booking_id, product_id)
);
