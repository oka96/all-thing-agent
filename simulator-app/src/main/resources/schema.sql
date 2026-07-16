CREATE TABLE IF NOT EXISTS customer (
    customer_ref VARCHAR(24) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    country_code CHAR(2) NOT NULL,
    kyc_status VARCHAR(20) NOT NULL,
    account_status VARCHAR(20) NOT NULL,
    risk_tier VARCHAR(12) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_customer_kyc CHECK (kyc_status IN ('VERIFIED', 'PENDING', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_customer_status CHECK (account_status IN ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_customer_risk CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE TABLE IF NOT EXISTS wallet (
    wallet_ref VARCHAR(32) PRIMARY KEY,
    customer_ref VARCHAR(24) NOT NULL REFERENCES customer(customer_ref),
    currency CHAR(3) NOT NULL,
    available_balance DECIMAL(19, 2) NOT NULL,
    reserved_balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    daily_outbound_limit DECIMAL(19, 2) NOT NULL,
    outbound_today DECIMAL(19, 2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_wallet_customer_currency UNIQUE (customer_ref, currency),
    CONSTRAINT ck_wallet_available CHECK (available_balance >= 0),
    CONSTRAINT ck_wallet_reserved CHECK (reserved_balance >= 0),
    CONSTRAINT ck_wallet_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS p2p_transfer (
    transfer_ref VARCHAR(32) PRIMARY KEY,
    sender_wallet_ref VARCHAR(32) NOT NULL REFERENCES wallet(wallet_ref),
    receiver_wallet_ref VARCHAR(32) NOT NULL REFERENCES wallet(wallet_ref),
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    note VARCHAR(140),
    status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(48),
    failure_detail VARCHAR(500),
    idempotency_key VARCHAR(80) NOT NULL,
    initiated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_transfer_idempotency UNIQUE (sender_wallet_ref, idempotency_key),
    CONSTRAINT ck_transfer_wallets CHECK (sender_wallet_ref <> receiver_wallet_ref),
    CONSTRAINT ck_transfer_amount CHECK (amount > 0),
    CONSTRAINT ck_transfer_status CHECK (status IN (
        'CREATED', 'VALIDATING', 'PENDING_REVIEW', 'PROCESSING', 'PENDING_REPAIR',
        'COMPLETED', 'REJECTED', 'FAILED', 'REVERSED'))
);

CREATE TABLE IF NOT EXISTS transfer_attempt (
    attempt_ref VARCHAR(48) PRIMARY KEY,
    transfer_ref VARCHAR(32) NOT NULL REFERENCES p2p_transfer(transfer_ref),
    attempt_no INTEGER NOT NULL,
    stage VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    external_ref VARCHAR(80),
    error_code VARCHAR(48),
    error_detail VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_attempt UNIQUE (transfer_ref, stage, attempt_no),
    CONSTRAINT ck_attempt_stage CHECK (stage IN (
        'ELIGIBILITY', 'FUNDS_CHECK', 'RISK', 'RESERVE', 'DEBIT', 'CREDIT', 'REVERSAL')),
    CONSTRAINT ck_attempt_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'SKIPPED'))
);

CREATE TABLE IF NOT EXISTS risk_check (
    risk_check_ref VARCHAR(48) PRIMARY KEY,
    transfer_ref VARCHAR(32) NOT NULL REFERENCES p2p_transfer(transfer_ref),
    check_type VARCHAR(32) NOT NULL,
    decision VARCHAR(12) NOT NULL,
    score INTEGER NOT NULL,
    reason_code VARCHAR(48),
    reason_detail VARCHAR(500),
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_risk_decision CHECK (decision IN ('PASS', 'REVIEW', 'BLOCK')),
    CONSTRAINT ck_risk_score CHECK (score BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS ledger_entry (
    entry_ref VARCHAR(48) PRIMARY KEY,
    transfer_ref VARCHAR(32) NOT NULL REFERENCES p2p_transfer(transfer_ref),
    wallet_ref VARCHAR(32) NOT NULL REFERENCES wallet(wallet_ref),
    direction VARCHAR(8) NOT NULL,
    entry_kind VARCHAR(16) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(12) NOT NULL,
    paired_entry_ref VARCHAR(48),
    balance_after DECIMAL(19, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_kind CHECK (entry_kind IN ('RESERVE', 'RELEASE', 'TRANSFER', 'REVERSAL', 'FEE')),
    CONSTRAINT ck_ledger_status CHECK (status IN ('PENDING', 'POSTED', 'FAILED', 'REVERSED')),
    CONSTRAINT ck_ledger_amount CHECK (amount > 0)
);

CREATE TABLE IF NOT EXISTS transfer_event (
    event_ref VARCHAR(48) PRIMARY KEY,
    transfer_ref VARCHAR(32) NOT NULL REFERENCES p2p_transfer(transfer_ref),
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(48),
    detail VARCHAR(500),
    actor VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transfer_event UNIQUE (transfer_ref, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_transfer_sender_time ON p2p_transfer(sender_wallet_ref, initiated_at);
CREATE INDEX IF NOT EXISTS idx_transfer_receiver_time ON p2p_transfer(receiver_wallet_ref, initiated_at);
CREATE INDEX IF NOT EXISTS idx_event_transfer_time ON transfer_event(transfer_ref, occurred_at);
CREATE INDEX IF NOT EXISTS idx_ledger_transfer_wallet ON ledger_entry(transfer_ref, wallet_ref);

CREATE SCHEMA IF NOT EXISTS DIAG;

CREATE OR REPLACE VIEW DIAG.CUSTOMER_ELIGIBILITY AS
SELECT customer_ref, display_name, country_code, kyc_status, account_status, risk_tier, created_at
FROM customer;

CREATE OR REPLACE VIEW DIAG.WALLET_SNAPSHOT AS
SELECT wallet_ref, customer_ref, currency, available_balance, reserved_balance,
       daily_outbound_limit, outbound_today, status AS wallet_status, version
FROM wallet;

CREATE OR REPLACE VIEW DIAG.TRANSFER_OVERVIEW AS
SELECT t.transfer_ref,
       sender.customer_ref AS sender_customer_ref,
       sender.display_name AS sender_name,
       receiver.customer_ref AS receiver_customer_ref,
       receiver.display_name AS receiver_name,
       t.amount, t.currency, t.note, t.status, t.failure_code, t.failure_detail,
       t.initiated_at, t.updated_at, t.completed_at
FROM p2p_transfer t
JOIN wallet sw ON sw.wallet_ref = t.sender_wallet_ref
JOIN customer sender ON sender.customer_ref = sw.customer_ref
JOIN wallet rw ON rw.wallet_ref = t.receiver_wallet_ref
JOIN customer receiver ON receiver.customer_ref = rw.customer_ref;

CREATE OR REPLACE VIEW DIAG.TRANSFER_TIMELINE AS
SELECT transfer_ref, sequence_no, event_type, from_status, to_status,
       failure_code, detail, actor, occurred_at
FROM transfer_event;

CREATE OR REPLACE VIEW DIAG.TRANSFER_ATTEMPTS AS
SELECT transfer_ref, attempt_no, stage, status, external_ref,
       error_code, error_detail, started_at, finished_at
FROM transfer_attempt;

CREATE OR REPLACE VIEW DIAG.RISK_EVIDENCE AS
SELECT transfer_ref, check_type, decision, score, reason_code, reason_detail, checked_at
FROM risk_check;

CREATE OR REPLACE VIEW DIAG.LEDGER_EVIDENCE AS
SELECT l.transfer_ref, l.entry_ref, l.wallet_ref, w.customer_ref, l.direction,
       l.entry_kind, l.amount, l.status, l.paired_entry_ref, l.balance_after, l.created_at
FROM ledger_entry l
JOIN wallet w ON w.wallet_ref = l.wallet_ref;

CREATE OR REPLACE VIEW DIAG.RECONCILIATION_RESULT AS
SELECT t.transfer_ref,
       t.amount AS expected_amount,
       COALESCE(SUM(CASE WHEN l.direction = 'DEBIT' AND l.entry_kind = 'TRANSFER' AND l.status = 'POSTED'
                         THEN l.amount ELSE 0 END), 0) AS posted_debit,
       COALESCE(SUM(CASE WHEN l.direction = 'CREDIT' AND l.entry_kind = 'TRANSFER' AND l.status = 'POSTED'
                         THEN l.amount ELSE 0 END), 0) AS posted_credit,
       CASE
           WHEN COALESCE(SUM(CASE WHEN l.direction = 'DEBIT' AND l.entry_kind = 'TRANSFER' AND l.status = 'POSTED'
                                  THEN l.amount ELSE 0 END), 0) = t.amount
            AND COALESCE(SUM(CASE WHEN l.direction = 'CREDIT' AND l.entry_kind = 'TRANSFER' AND l.status = 'POSTED'
                                  THEN l.amount ELSE 0 END), 0) = t.amount THEN 'BALANCED'
           WHEN COALESCE(SUM(CASE WHEN l.direction = 'DEBIT' AND l.entry_kind = 'TRANSFER' AND l.status = 'POSTED'
                                  THEN l.amount ELSE 0 END), 0) = t.amount THEN 'MISSING_CREDIT'
           WHEN COALESCE(SUM(CASE WHEN l.direction = 'CREDIT' AND l.entry_kind = 'TRANSFER' AND l.status = 'POSTED'
                                  THEN l.amount ELSE 0 END), 0) = t.amount THEN 'MISSING_DEBIT'
           ELSE 'NO_POSTED_MOVEMENT'
       END AS reconciliation_status
FROM p2p_transfer t
LEFT JOIN ledger_entry l ON l.transfer_ref = t.transfer_ref
GROUP BY t.transfer_ref, t.amount;

CREATE USER IF NOT EXISTS DIAG_READER PASSWORD 'diag-reader-local';
GRANT SELECT ON DIAG.CUSTOMER_ELIGIBILITY TO DIAG_READER;
GRANT SELECT ON DIAG.WALLET_SNAPSHOT TO DIAG_READER;
GRANT SELECT ON DIAG.TRANSFER_OVERVIEW TO DIAG_READER;
GRANT SELECT ON DIAG.TRANSFER_TIMELINE TO DIAG_READER;
GRANT SELECT ON DIAG.TRANSFER_ATTEMPTS TO DIAG_READER;
GRANT SELECT ON DIAG.RISK_EVIDENCE TO DIAG_READER;
GRANT SELECT ON DIAG.LEDGER_EVIDENCE TO DIAG_READER;
GRANT SELECT ON DIAG.RECONCILIATION_RESULT TO DIAG_READER;

