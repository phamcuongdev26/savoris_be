ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS order_items_status_check;

ALTER TABLE order_items
    ADD CONSTRAINT order_items_status_check
        CHECK (status IN ('PENDING', 'COOKING', 'VOID_REQUESTED', 'READY', 'SERVED', 'CANCELLED'));
