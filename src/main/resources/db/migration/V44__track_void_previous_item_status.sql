ALTER TABLE order_items
    ADD COLUMN void_previous_status VARCHAR(20),
    ADD COLUMN void_requested_at TIMESTAMP;
