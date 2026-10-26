-- A SePay event can be delivered more than once or reach multiple app instances concurrently.
-- The primary key is the cross-instance idempotency gate; it is written in the payment transaction.
CREATE TABLE sepay_transactions (
    transaction_id BIGINT PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(id),
    reference_code VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT now()
);

CREATE INDEX idx_sepay_transactions_order_id ON sepay_transactions(order_id);
