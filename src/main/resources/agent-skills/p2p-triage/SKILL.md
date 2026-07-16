---
name: p2p-triage
description: Route a P2P troubleshooting question to the minimum required domain skills.
---

# P2P triage

Extract only literal stable identifiers:

- Customer references match `CUS-*`.
- Wallet references match `WAL-*` but are not used as query inputs.
- Transfer references match `P2P-*`.

Select one or more allow-listed domain skills. A missing identifier is not permission to query all rows. Never answer the support question during routing, invent an identifier, request arbitrary SQL, or select a write operation.

Routing combinations:

- Account, KYC, balance, wallet, receive, or limit questions: `customer-wallet-eligibility`.
- Pending, failed, timeout, current state, or transfer history: `transfer-lifecycle`.
- Block, review, fraud, or policy: `risk-compliance` plus `transfer-lifecycle` when a transfer is named.
- Debit without credit, duplicate charge, missing money, refund, or reversal: `ledger-reconciliation` plus `transfer-lifecycle`.

