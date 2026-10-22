-- what the salon pays per unit; existing products start at zero until an admin sets it
ALTER TABLE product
    ADD COLUMN cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (cost_cents >= 0);
