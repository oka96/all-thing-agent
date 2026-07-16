INSERT INTO customer (customer_ref, display_name, email, phone, country_code, kyc_status, account_status, risk_tier, created_at)
SELECT 'CUS-1001', 'Aisha Rahman', 'aisha@example.test', '+60-11-1001', 'MY', 'VERIFIED', 'ACTIVE', 'LOW', TIMESTAMP WITH TIME ZONE '2026-01-05 09:00:00+08:00'
WHERE NOT EXISTS (SELECT 1 FROM customer WHERE customer_ref = 'CUS-1001');

INSERT INTO customer (customer_ref, display_name, email, phone, country_code, kyc_status, account_status, risk_tier, created_at)
SELECT 'CUS-1002', 'Ben Lim', 'ben@example.test', '+60-11-1002', 'MY', 'VERIFIED', 'ACTIVE', 'LOW', TIMESTAMP WITH TIME ZONE '2026-01-08 09:00:00+08:00'
WHERE NOT EXISTS (SELECT 1 FROM customer WHERE customer_ref = 'CUS-1002');

INSERT INTO customer (customer_ref, display_name, email, phone, country_code, kyc_status, account_status, risk_tier, created_at)
SELECT 'CUS-1003', 'Chen Wei', 'chen@example.test', '+60-11-1003', 'MY', 'PENDING', 'RESTRICTED', 'MEDIUM', TIMESTAMP WITH TIME ZONE '2026-04-11 09:00:00+08:00'
WHERE NOT EXISTS (SELECT 1 FROM customer WHERE customer_ref = 'CUS-1003');

INSERT INTO customer (customer_ref, display_name, email, phone, country_code, kyc_status, account_status, risk_tier, created_at)
SELECT 'CUS-1004', 'Devi Nair', 'devi@example.test', '+60-11-1004', 'MY', 'VERIFIED', 'ACTIVE', 'LOW', TIMESTAMP WITH TIME ZONE '2026-02-15 09:00:00+08:00'
WHERE NOT EXISTS (SELECT 1 FROM customer WHERE customer_ref = 'CUS-1004');

INSERT INTO customer (customer_ref, display_name, email, phone, country_code, kyc_status, account_status, risk_tier, created_at)
SELECT 'CUS-1005', 'Farah Aziz', 'farah@example.test', '+60-11-1005', 'MY', 'VERIFIED', 'ACTIVE', 'MEDIUM', TIMESTAMP WITH TIME ZONE '2026-03-20 09:00:00+08:00'
WHERE NOT EXISTS (SELECT 1 FROM customer WHERE customer_ref = 'CUS-1005');

INSERT INTO wallet (wallet_ref, customer_ref, currency, available_balance, reserved_balance, daily_outbound_limit, outbound_today, status, version)
SELECT 'WAL-1001-MYR', 'CUS-1001', 'MYR', 1250.00, 0.00, 2000.00, 120.00, 'ACTIVE', 0
WHERE NOT EXISTS (SELECT 1 FROM wallet WHERE wallet_ref = 'WAL-1001-MYR');

INSERT INTO wallet (wallet_ref, customer_ref, currency, available_balance, reserved_balance, daily_outbound_limit, outbound_today, status, version)
SELECT 'WAL-1002-MYR', 'CUS-1002', 'MYR', 85.00, 0.00, 500.00, 25.00, 'ACTIVE', 0
WHERE NOT EXISTS (SELECT 1 FROM wallet WHERE wallet_ref = 'WAL-1002-MYR');

INSERT INTO wallet (wallet_ref, customer_ref, currency, available_balance, reserved_balance, daily_outbound_limit, outbound_today, status, version)
SELECT 'WAL-1003-MYR', 'CUS-1003', 'MYR', 500.00, 0.00, 500.00, 0.00, 'FROZEN', 0
WHERE NOT EXISTS (SELECT 1 FROM wallet WHERE wallet_ref = 'WAL-1003-MYR');

INSERT INTO wallet (wallet_ref, customer_ref, currency, available_balance, reserved_balance, daily_outbound_limit, outbound_today, status, version)
SELECT 'WAL-1004-MYR', 'CUS-1004', 'MYR', 2040.00, 0.00, 1000.00, 980.00, 'ACTIVE', 0
WHERE NOT EXISTS (SELECT 1 FROM wallet WHERE wallet_ref = 'WAL-1004-MYR');

INSERT INTO wallet (wallet_ref, customer_ref, currency, available_balance, reserved_balance, daily_outbound_limit, outbound_today, status, version)
SELECT 'WAL-1005-MYR', 'CUS-1005', 'MYR', 740.00, 0.00, 1500.00, 50.00, 'ACTIVE', 0
WHERE NOT EXISTS (SELECT 1 FROM wallet WHERE wallet_ref = 'WAL-1005-MYR');

INSERT INTO p2p_transfer (transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency, note, status, failure_code, failure_detail, idempotency_key, initiated_at, updated_at, completed_at, version)
SELECT 'P2P-000101', 'WAL-1001-MYR', 'WAL-1002-MYR', 50.00, 'MYR', 'Lunch split', 'COMPLETED', NULL, NULL, 'seed-000101', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:03+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:03+08:00', 3
WHERE NOT EXISTS (SELECT 1 FROM p2p_transfer WHERE transfer_ref = 'P2P-000101');

INSERT INTO p2p_transfer (transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency, note, status, failure_code, failure_detail, idempotency_key, initiated_at, updated_at, completed_at, version)
SELECT 'P2P-000102', 'WAL-1002-MYR', 'WAL-1001-MYR', 150.00, 'MYR', 'Rent contribution', 'FAILED', 'INSUFFICIENT_FUNDS', 'Ben had MYR 85.00 available for a MYR 150.00 transfer.', 'seed-000102', TIMESTAMP WITH TIME ZONE '2026-07-16 11:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 11:00:01+08:00', NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM p2p_transfer WHERE transfer_ref = 'P2P-000102');

INSERT INTO p2p_transfer (transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency, note, status, failure_code, failure_detail, idempotency_key, initiated_at, updated_at, completed_at, version)
SELECT 'P2P-000103', 'WAL-1001-MYR', 'WAL-1003-MYR', 100.00, 'MYR', 'Allowance', 'REJECTED', 'RECEIVER_RESTRICTED', 'Chen has pending KYC and a restricted account.', 'seed-000103', TIMESTAMP WITH TIME ZONE '2026-07-16 12:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 12:00:01+08:00', NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM p2p_transfer WHERE transfer_ref = 'P2P-000103');

INSERT INTO p2p_transfer (transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency, note, status, failure_code, failure_detail, idempotency_key, initiated_at, updated_at, completed_at, version)
SELECT 'P2P-000104', 'WAL-1001-MYR', 'WAL-1005-MYR', 950.00, 'MYR', 'Deposit', 'PENDING_REVIEW', 'RISK_REVIEW_REQUIRED', 'Velocity policy requires manual review; no funds moved.', 'seed-000104', TIMESTAMP WITH TIME ZONE '2026-07-16 13:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 13:00:02+08:00', NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM p2p_transfer WHERE transfer_ref = 'P2P-000104');

INSERT INTO p2p_transfer (transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency, note, status, failure_code, failure_detail, idempotency_key, initiated_at, updated_at, completed_at, version)
SELECT 'P2P-000105', 'WAL-1001-MYR', 'WAL-1005-MYR', 40.00, 'MYR', 'Shared taxi', 'PENDING_REPAIR', 'CREDIT_POSTING_FAILED', 'Sender debit posted but receiver credit failed.', 'seed-000105', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:04+08:00', NULL, 3
WHERE NOT EXISTS (SELECT 1 FROM p2p_transfer WHERE transfer_ref = 'P2P-000105');

INSERT INTO p2p_transfer (transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency, note, status, failure_code, failure_detail, idempotency_key, initiated_at, updated_at, completed_at, version)
SELECT 'P2P-000106', 'WAL-1004-MYR', 'WAL-1002-MYR', 50.00, 'MYR', 'Groceries', 'REJECTED', 'DAILY_LIMIT_EXCEEDED', 'Devi already used MYR 980.00 of a MYR 1000.00 daily limit.', 'seed-000106', TIMESTAMP WITH TIME ZONE '2026-07-16 15:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 15:00:01+08:00', NULL, 2
WHERE NOT EXISTS (SELECT 1 FROM p2p_transfer WHERE transfer_ref = 'P2P-000106');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000101-1', 'P2P-000101', 1, 'ELIGIBILITY', 'SUCCEEDED', NULL, 'Both parties eligible.', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000101-1');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000101-2', 'P2P-000101', 1, 'FUNDS_CHECK', 'SUCCEEDED', NULL, 'Funds available.', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:01+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000101-2');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000102-1', 'P2P-000102', 1, 'FUNDS_CHECK', 'FAILED', 'INSUFFICIENT_FUNDS', 'Available MYR 85.00; requested MYR 150.00.', TIMESTAMP WITH TIME ZONE '2026-07-16 11:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 11:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000102-1');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000103-1', 'P2P-000103', 1, 'ELIGIBILITY', 'FAILED', 'RECEIVER_RESTRICTED', 'Receiver KYC pending and wallet frozen.', TIMESTAMP WITH TIME ZONE '2026-07-16 12:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 12:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000103-1');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000104-1', 'P2P-000104', 1, 'RISK', 'SUCCEEDED', 'RISK_REVIEW_REQUIRED', 'Risk decision REVIEW.', TIMESTAMP WITH TIME ZONE '2026-07-16 13:00:01+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 13:00:02+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000104-1');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000105-1', 'P2P-000105', 1, 'DEBIT', 'SUCCEEDED', NULL, 'Sender debit posted.', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:02+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:03+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000105-1');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000105-2', 'P2P-000105', 1, 'CREDIT', 'FAILED', 'CREDIT_POSTING_FAILED', 'Receiver ledger posting failed.', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:03+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:04+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000105-2');

INSERT INTO transfer_attempt (attempt_ref, transfer_ref, attempt_no, stage, status, error_code, error_detail, started_at, finished_at)
SELECT 'ATT-000106-1', 'P2P-000106', 1, 'FUNDS_CHECK', 'FAILED', 'DAILY_LIMIT_EXCEEDED', 'MYR 980.00 already used of MYR 1000.00.', TIMESTAMP WITH TIME ZONE '2026-07-16 15:00:00+08:00', TIMESTAMP WITH TIME ZONE '2026-07-16 15:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_attempt WHERE attempt_ref = 'ATT-000106-1');

INSERT INTO risk_check (risk_check_ref, transfer_ref, check_type, decision, score, reason_code, reason_detail, checked_at)
SELECT 'RSK-000101', 'P2P-000101', 'P2P_POLICY', 'PASS', 14, NULL, 'Low risk transfer.', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:02+08:00'
WHERE NOT EXISTS (SELECT 1 FROM risk_check WHERE risk_check_ref = 'RSK-000101');

INSERT INTO risk_check (risk_check_ref, transfer_ref, check_type, decision, score, reason_code, reason_detail, checked_at)
SELECT 'RSK-000104', 'P2P-000104', 'VELOCITY', 'REVIEW', 74, 'RISK_REVIEW_REQUIRED', 'Amount and receiver risk profile require review.', TIMESTAMP WITH TIME ZONE '2026-07-16 13:00:02+08:00'
WHERE NOT EXISTS (SELECT 1 FROM risk_check WHERE risk_check_ref = 'RSK-000104');

INSERT INTO ledger_entry (entry_ref, transfer_ref, wallet_ref, direction, entry_kind, amount, status, paired_entry_ref, balance_after, created_at)
SELECT 'LED-000101-D', 'P2P-000101', 'WAL-1001-MYR', 'DEBIT', 'TRANSFER', 50.00, 'POSTED', 'LED-000101-C', 1200.00, TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:03+08:00'
WHERE NOT EXISTS (SELECT 1 FROM ledger_entry WHERE entry_ref = 'LED-000101-D');

INSERT INTO ledger_entry (entry_ref, transfer_ref, wallet_ref, direction, entry_kind, amount, status, paired_entry_ref, balance_after, created_at)
SELECT 'LED-000101-C', 'P2P-000101', 'WAL-1002-MYR', 'CREDIT', 'TRANSFER', 50.00, 'POSTED', 'LED-000101-D', 235.00, TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:03+08:00'
WHERE NOT EXISTS (SELECT 1 FROM ledger_entry WHERE entry_ref = 'LED-000101-C');

INSERT INTO ledger_entry (entry_ref, transfer_ref, wallet_ref, direction, entry_kind, amount, status, paired_entry_ref, balance_after, created_at)
SELECT 'LED-000105-D', 'P2P-000105', 'WAL-1001-MYR', 'DEBIT', 'TRANSFER', 40.00, 'POSTED', NULL, 1160.00, TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:03+08:00'
WHERE NOT EXISTS (SELECT 1 FROM ledger_entry WHERE entry_ref = 'LED-000105-D');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000101-1', 'P2P-000101', 1, 'TRANSFER_CREATED', NULL, 'CREATED', NULL, 'Transfer created.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:00+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000101-1');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000101-2', 'P2P-000101', 2, 'TRANSFER_COMPLETED', 'PROCESSING', 'COMPLETED', NULL, 'Debit and credit posted.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 10:00:03+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000101-2');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000102-1', 'P2P-000102', 1, 'TRANSFER_FAILED', 'VALIDATING', 'FAILED', 'INSUFFICIENT_FUNDS', 'Funds check failed; no ledger movement.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 11:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000102-1');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000103-1', 'P2P-000103', 1, 'TRANSFER_REJECTED', 'VALIDATING', 'REJECTED', 'RECEIVER_RESTRICTED', 'Receiver eligibility failed.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 12:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000103-1');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000104-1', 'P2P-000104', 1, 'RISK_REVIEW_QUEUED', 'VALIDATING', 'PENDING_REVIEW', 'RISK_REVIEW_REQUIRED', 'No funds moved while waiting for review.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 13:00:02+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000104-1');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000105-1', 'P2P-000105', 1, 'DEBIT_POSTED', 'PROCESSING', 'PROCESSING', NULL, 'Sender debit posted.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:03+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000105-1');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000105-2', 'P2P-000105', 2, 'REPAIR_REQUIRED', 'PROCESSING', 'PENDING_REPAIR', 'CREDIT_POSTING_FAILED', 'Receiver credit missing; repair required.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 14:00:04+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000105-2');

INSERT INTO transfer_event (event_ref, transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)
SELECT 'EVT-000106-1', 'P2P-000106', 1, 'TRANSFER_REJECTED', 'VALIDATING', 'REJECTED', 'DAILY_LIMIT_EXCEEDED', 'Daily limit check failed.', 'SIMULATOR', TIMESTAMP WITH TIME ZONE '2026-07-16 15:00:01+08:00'
WHERE NOT EXISTS (SELECT 1 FROM transfer_event WHERE event_ref = 'EVT-000106-1');

