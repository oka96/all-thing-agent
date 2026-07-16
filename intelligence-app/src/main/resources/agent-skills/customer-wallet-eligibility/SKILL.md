---
name: customer-wallet-eligibility
description: Diagnose customer, KYC, account, wallet, balance, and outbound-limit eligibility.
tables:
  - DIAG.CUSTOMER_ELIGIBILITY
  - DIAG.WALLET_SNAPSHOT
---

# Customer and wallet eligibility

## Schema

`DIAG.CUSTOMER_ELIGIBILITY(customer_ref, display_name, country_code, kyc_status, account_status, risk_tier, created_at)`

`DIAG.WALLET_SNAPSHOT(wallet_ref, customer_ref, currency, available_balance, reserved_balance, daily_outbound_limit, outbound_today, wallet_status, version)`

## Domain rules

- Sending or receiving requires `account_status=ACTIVE`, `kyc_status=VERIFIED`, and `wallet_status=ACTIVE`.
- Available funds are `available_balance`; never add `reserved_balance`.
- Remaining daily capacity is `daily_outbound_limit - outbound_today`.
- A sufficient balance does not override KYC, account, wallet, currency, limit, or risk controls.

Use only the supplied rows. Do not expose masked PII, infer a balance change without ledger evidence, or suggest bypassing compliance controls.

