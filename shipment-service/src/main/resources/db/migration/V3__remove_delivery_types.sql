ALTER TABLE shipments DROP COLUMN IF EXISTS delivery_type;

ALTER TABLE price_quotes
    DROP COLUMN IF EXISTS delivery_type,
    DROP COLUMN IF EXISTS delivery_type_multiplier;
