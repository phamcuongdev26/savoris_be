CREATE TABLE order_item_void_history (
    id              SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    order_item_id   INTEGER NOT NULL REFERENCES order_items(id) ON DELETE RESTRICT,
    requested_by    INTEGER REFERENCES app_user(id) ON DELETE SET NULL,
    requested_at    TIMESTAMP NOT NULL,
    request_reason  VARCHAR(255),
    previous_status VARCHAR(20) NOT NULL,
    resolution      VARCHAR(20),
    resolved_by     INTEGER REFERENCES app_user(id) ON DELETE SET NULL,
    resolved_at     TIMESTAMP
);

CREATE INDEX idx_order_item_void_history_item_requested
    ON order_item_void_history(order_item_id, requested_at DESC);
