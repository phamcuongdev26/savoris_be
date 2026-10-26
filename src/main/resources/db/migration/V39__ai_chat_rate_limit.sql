CREATE TABLE ai_request_rate_window (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 0)
);
