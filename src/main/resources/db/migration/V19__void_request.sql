-- Void/Return workflow: waiter flags a cooking/ready item for chef review
ALTER TABLE order_items
    ADD COLUMN void_reason        VARCHAR(255),
    ADD COLUMN void_resolution    VARCHAR(20),
    ADD COLUMN void_requested_by  INTEGER REFERENCES staff(id) ON DELETE SET NULL;
