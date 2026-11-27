CREATE TABLE notification_outbox (
    id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at TIMESTAMP(3) NULL,
    INDEX idx_outbox_pending (status, available_at),
    CONSTRAINT chk_outbox_attempt_count_non_negative
        CHECK (attempt_count >= 0),
    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE TABLE notification_delivery (
    id CHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    channel VARCHAR(30) NOT NULL,
    destination_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    delivered_at TIMESTAMP(3) NULL,
    CONSTRAINT chk_delivery_status
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED'))
);

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(100) NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(80) NULL,
    resource_id VARCHAR(100) NULL,
    request_id VARCHAR(80) NOT NULL,
    metadata JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_audit_actor_created (actor_id, created_at),
    INDEX idx_audit_action_created (action, created_at),
    INDEX idx_audit_request_id (request_id)
);
