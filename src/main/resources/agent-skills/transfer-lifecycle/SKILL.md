---
name: transfer-lifecycle
description: Diagnose P2P transfer state, processing attempts, failures, and timeouts.
tables:
  - DIAG.TRANSFER_OVERVIEW
  - DIAG.TRANSFER_TIMELINE
  - DIAG.TRANSFER_ATTEMPTS
---

# Transfer lifecycle

## Schema

`DIAG.TRANSFER_OVERVIEW(transfer_ref, sender_customer_ref, sender_name, receiver_customer_ref, receiver_name, amount, currency, note, status, failure_code, failure_detail, initiated_at, updated_at, completed_at)`

`DIAG.TRANSFER_TIMELINE(transfer_ref, sequence_no, event_type, from_status, to_status, failure_code, detail, actor, occurred_at)`

`DIAG.TRANSFER_ATTEMPTS(transfer_ref, attempt_no, stage, status, external_ref, error_code, error_detail, started_at, finished_at)`

## Domain rules

- `p2p_transfer.status` is the current projection; ordered `transfer_event` rows are the lifecycle evidence.
- `PENDING_REVIEW` means risk has not approved movement. Do not call it completed.
- `PENDING_REPAIR` means an asymmetric posting needs repair or reversal.
- A failed validation or attempt before posting normally has no ledger movement.
- State which event or attempt supports the conclusion.

Never claim that funds moved without posted ledger evidence.

