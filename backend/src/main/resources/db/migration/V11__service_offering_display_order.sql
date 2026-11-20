-- an employee may pin the order their treatments are listed in; unnumbered ones follow, alphabetically
ALTER TABLE service_offering
    ADD COLUMN display_order INTEGER CHECK (display_order >= 1);
