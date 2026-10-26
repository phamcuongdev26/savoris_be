ALTER TABLE order_items
    ADD COLUMN cooking_started_at timestamp(6);

-- For dishes already in the cooking lane at migration time, updated_at is the time they entered that status.
UPDATE order_items
SET cooking_started_at = updated_at
WHERE status = 'COOKING' AND cooking_started_at IS NULL;
