UPDATE shipments SET status = 'ASSIGNED' WHERE status = 'TO_PICKUP';

DELETE FROM shipment_status_history WHERE to_status = 'TO_PICKUP';
UPDATE shipment_status_history SET from_status = 'ASSIGNED' WHERE from_status = 'TO_PICKUP';

ALTER TABLE shipments DROP COLUMN IF EXISTS trip_started_at;

ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_status_check;
ALTER TABLE shipments ADD CONSTRAINT shipments_status_check CHECK (status IN
    ('WAITING_FOR_COURIER', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));

ALTER TABLE shipment_status_history DROP CONSTRAINT IF EXISTS shipment_status_history_from_status_check;
ALTER TABLE shipment_status_history ADD CONSTRAINT shipment_status_history_from_status_check CHECK (from_status IN
    ('WAITING_FOR_COURIER', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));

ALTER TABLE shipment_status_history DROP CONSTRAINT IF EXISTS shipment_status_history_to_status_check;
ALTER TABLE shipment_status_history ADD CONSTRAINT shipment_status_history_to_status_check CHECK (to_status IN
    ('WAITING_FOR_COURIER', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));
