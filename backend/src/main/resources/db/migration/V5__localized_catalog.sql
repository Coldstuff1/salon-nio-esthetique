-- catalog text is stored in English and, optionally, French; what's there today becomes the English
ALTER TABLE service_type RENAME COLUMN name TO name_en;
ALTER TABLE service_type RENAME COLUMN description TO description_en;
ALTER TABLE service_type RENAME COLUMN category TO category_en;
ALTER TABLE service_type
    ADD COLUMN name_fr        VARCHAR(120),
    ADD COLUMN description_fr TEXT,
    ADD COLUMN category_fr    VARCHAR(60);

ALTER TABLE product RENAME COLUMN name TO name_en;
ALTER TABLE product RENAME COLUMN description TO description_en;
ALTER TABLE product
    ADD COLUMN name_fr        VARCHAR(120),
    ADD COLUMN description_fr TEXT;

-- booking lines snapshot both languages so history follows the visitor's language
ALTER TABLE booking_service RENAME COLUMN name_snapshot TO name_snapshot_en;
ALTER TABLE booking_service ADD COLUMN name_snapshot_fr VARCHAR(120);

ALTER TABLE booking_product RENAME COLUMN name_snapshot TO name_snapshot_en;
ALTER TABLE booking_product ADD COLUMN name_snapshot_fr VARCHAR(120);
