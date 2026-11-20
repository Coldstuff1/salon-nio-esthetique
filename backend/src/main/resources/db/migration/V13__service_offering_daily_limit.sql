-- an employee may cap how many bookings of a treatment they take per day; null means no cap
ALTER TABLE service_offering
    ADD COLUMN daily_limit INTEGER CHECK (daily_limit >= 1);
