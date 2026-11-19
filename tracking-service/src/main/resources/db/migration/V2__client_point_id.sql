-- Ідентифікатор GPS-точки від клієнта: повторне надсилання тієї самої точки (після таймауту)
-- повертає вже збережену замість дубля. NULL — точки з gRPC-потоків, для них ключ не потрібен.
ALTER TABLE locations ADD COLUMN client_point_id uuid;

CREATE UNIQUE INDEX uq_locations_shipment_client_point ON locations (shipment_id, client_point_id);
