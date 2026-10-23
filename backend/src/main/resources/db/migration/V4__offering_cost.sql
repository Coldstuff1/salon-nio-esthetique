-- each employee buys their own stock, so what a product costs is theirs to record, next to their price
ALTER TABLE product_offering
    ADD COLUMN cost_cents INTEGER NOT NULL DEFAULT 0 CHECK (cost_cents >= 0);

-- carry the salon-wide cost over to everyone already selling the product
UPDATE product_offering o
SET cost_cents = p.cost_cents
FROM product p
WHERE p.id = o.product_id;

ALTER TABLE product_offering
    ALTER COLUMN cost_cents DROP DEFAULT;

ALTER TABLE product
    DROP COLUMN cost_cents;
