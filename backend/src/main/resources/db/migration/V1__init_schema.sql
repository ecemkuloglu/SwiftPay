-- V1: core schema for SwiftPay money transfer
-- Money: NUMERIC(19,2). Timestamps: TIMESTAMPTZ (UTC). Limits are NOT stored here (application.yml).

CREATE TABLE customer (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_username UNIQUE (username)
);

CREATE TABLE account (
    id           BIGSERIAL     PRIMARY KEY,
    customer_id  BIGINT        NOT NULL REFERENCES customer (id),
    iban         VARCHAR(26)   NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'TRY',
    balance      NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_account_iban UNIQUE (iban),
    CONSTRAINT ck_account_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_account_currency CHECK (currency = 'TRY'),
    CONSTRAINT ck_account_iban_format CHECK (iban ~ '^TR[0-9]{24}$')
);
CREATE INDEX idx_account_customer ON account (customer_id);

CREATE TABLE transfer (
    id                    BIGSERIAL     PRIMARY KEY,
    customer_id           BIGINT        NOT NULL REFERENCES customer (id),
    source_account_id     BIGINT        NOT NULL REFERENCES account (id),
    -- nullable: a transfer to an unknown IBAN is stored as FAILED (RECEIVER_NOT_FOUND)
    receiver_account_id   BIGINT        REFERENCES account (id),
    receiver_iban         VARCHAR(26)   NOT NULL,
    amount                NUMERIC(19,2) NOT NULL,
    currency              CHAR(3)       NOT NULL DEFAULT 'TRY',
    description           VARCHAR(140),
    status                VARCHAR(16)   NOT NULL,
    failure_code          VARCHAR(64),
    idempotency_key       VARCHAR(64)   NOT NULL,
    request_hash          CHAR(64)      NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,

    -- duplicate protection: one key per customer
    CONSTRAINT uq_transfer_customer_idem UNIQUE (customer_id, idempotency_key),

    CONSTRAINT ck_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_transfer_currency CHECK (currency = 'TRY'),
    -- state consistency: completed_at only for COMPLETED, failure_code only for FAILED
    CONSTRAINT ck_transfer_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_transfer_failure_code CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL)
    )
);

-- daily/monthly limit queries and statistics (only COMPLETED, filtered by completed_at)
CREATE INDEX idx_transfer_limit_stats
    ON transfer (customer_id, completed_at)
    WHERE status = 'COMPLETED';

-- transfer list (newest first) and date-range filter
CREATE INDEX idx_transfer_customer_created
    ON transfer (customer_id, created_at DESC);

CREATE INDEX idx_transfer_source_account ON transfer (source_account_id);

-- audit trail: every attempt, success or failure, with the trace id
CREATE TABLE audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    customer_id  BIGINT       REFERENCES customer (id),
    transfer_id  BIGINT       REFERENCES transfer (id),
    event_type   VARCHAR(40)  NOT NULL,   -- e.g. TRANSFER_COMPLETED, TRANSFER_FAILED, LOGIN_FAILED
    trace_id     VARCHAR(64)  NOT NULL,
    details      VARCHAR(500),            -- never store full IBANs, tokens or passwords
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_customer_created ON audit_log (customer_id, created_at DESC);
CREATE INDEX idx_audit_transfer ON audit_log (transfer_id);
