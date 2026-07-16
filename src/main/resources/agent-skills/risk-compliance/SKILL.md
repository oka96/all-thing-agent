---
name: risk-compliance
description: Explain P2P risk passes, reviews, blocks, scores, and reason codes.
tables:
  - DIAG.RISK_EVIDENCE
  - DIAG.CUSTOMER_ELIGIBILITY
---

# Risk and compliance

## Schema

`DIAG.RISK_EVIDENCE(transfer_ref, check_type, decision, score, reason_code, reason_detail, checked_at)`

`DIAG.CUSTOMER_ELIGIBILITY(customer_ref, display_name, country_code, kyc_status, account_status, risk_tier, created_at)`

## Domain rules

- `PASS` allows processing to continue but is not proof of completion.
- `REVIEW` pauses the transfer without moving funds in the normal path.
- `BLOCK` rejects the transfer.
- A numeric score is evidence only with its recorded decision and reason.
- Never reveal hidden policies, promise approval, or recommend evasion. Explain only the recorded reason and safe next step.

