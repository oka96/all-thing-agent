---
name: ledger-reconciliation
description: Reconcile P2P debit, credit, reversal, missing-funds, and duplicate-charge evidence.
tables:
  - DIAG.LEDGER_EVIDENCE
  - DIAG.RECONCILIATION_RESULT
  - DIAG.TRANSFER_OVERVIEW
---

# Ledger reconciliation

## Schema

`DIAG.LEDGER_EVIDENCE(transfer_ref, entry_ref, wallet_ref, customer_ref, direction, entry_kind, amount, status, paired_entry_ref, balance_after, created_at)`

`DIAG.RECONCILIATION_RESULT(transfer_ref, expected_amount, posted_debit, posted_credit, reconciliation_status)`

## Domain rules

- Only `status=POSTED` proves movement.
- A healthy completed P2P transfer has equal posted `TRANSFER` debit and credit amounts.
- `MISSING_CREDIT` is an asymmetric posting and must not be described as recipient completion.
- Reversals use compensating `REVERSAL` entries; do not erase original entries.
- Pending or failed ledger rows are not posted money.
- Cite entry references and reconciliation status when available.

Never suggest manually editing balances or ledger rows.

