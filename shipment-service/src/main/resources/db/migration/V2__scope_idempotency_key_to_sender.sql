-- Idempotency-Key унікальний у межах відправника, а не глобально:
-- однаковий ключ двох різних клієнтів більше не конфліктує.
ALTER TABLE idempotency_records ADD COLUMN user_id bigint;

UPDATE idempotency_records r
SET user_id = s.user_id
FROM shipments s
WHERE s.shipment_id = r.shipment_id;

-- Записи без доставки повторити неможливо, тож вони не потрібні.
DELETE FROM idempotency_records WHERE user_id IS NULL;

ALTER TABLE idempotency_records ALTER COLUMN user_id SET NOT NULL;

-- Глобальне унікальне обмеження на idempotency_key; його назва залежить від версії Hibernate,
-- що створювала схему, тож шукаємо його за складом колонок.
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'idempotency_records'::regclass
          AND c.contype = 'u'
          AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute
                                WHERE attrelid = 'idempotency_records'::regclass
                                  AND attname = 'idempotency_key')]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE idempotency_records DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE idempotency_records
    ADD CONSTRAINT uq_idempotency_sender_key UNIQUE (user_id, idempotency_key);
