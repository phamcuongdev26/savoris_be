-- Older development databases applied V44 before the request timestamp was added to that migration.
-- Keep this additive so both clean installs and upgraded databases validate the same entity shape.
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS void_requested_at timestamp;
