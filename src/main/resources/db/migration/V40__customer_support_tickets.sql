CREATE TABLE customer_support_ticket (
    id BIGSERIAL PRIMARY KEY,
    reference_code VARCHAR(20) NOT NULL UNIQUE,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    branch_id INTEGER REFERENCES branches(id),
    issue TEXT NOT NULL,
    conversation_context TEXT,
    customer_access_token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    handled_by INTEGER REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_support_ticket_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED'))
);
CREATE INDEX idx_support_ticket_branch_status ON customer_support_ticket(branch_id, status, created_at DESC);

CREATE TABLE customer_support_message (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES customer_support_ticket(id) ON DELETE CASCADE,
    sender_type VARCHAR(10) NOT NULL CHECK (sender_type IN ('CUSTOMER', 'STAFF', 'ASSISTANT')),
    sender_staff_id INTEGER REFERENCES app_user(id),
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_support_message_ticket_created ON customer_support_message(ticket_id, created_at, id);
