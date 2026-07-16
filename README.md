# P2P Clearing Desk

A local P2P transfer simulator and troubleshooting assistant built with Spring Boot, Spring AI, H2, and the logged-in Codex CLI. It does not require an OpenAI API key: `codex exec` uses the local ChatGPT account session.

## What is included

- Five mock MYR customers with different balances, KYC states, wallet states, risk tiers, and limits.
- Seven relational domain tables: `customer`, `wallet`, `p2p_transfer`, `transfer_attempt`, `risk_check`, `ledger_entry`, and `transfer_event`.
- Seeded success, insufficient-funds, restricted-recipient, risk-review, repair, and daily-limit cases.
- A transfer simulator that records lifecycle attempts, risk evidence, events, and balanced ledger entries.
- A responsive frontend for choosing users, posting mock transfers, viewing history, and chatting with the troubleshooter.
- A Spring AI `ChatModel` implementation backed by non-interactive `codex exec` rather than an API model.
- Five domain skills that progressively load only the relevant schema and operating rules.
- Expandable per-answer decision traces and database evidence showing fixed SQL, bound parameters, and redacted raw rows.
- A database-enforced, read-only troubleshooting path using a dedicated `DIAG_READER` identity and curated views.

## Architecture

```text
Browser transfer form
  -> /api/simulator/*
  -> P2pTransferService
  -> writer JdbcClient (SA)
  -> domain tables

Browser chat
  -> /api/troubleshooting/chat
  -> Spring AI ChatClient
  -> CodexCliChatModel
  -> codex exec (ChatGPT login, ephemeral, read-only sandbox)
  -> validated skill + identifier route
  -> DiagnosticQueryGateway
  -> DIAG_READER JdbcClient
  -> allow-listed DIAG views only
  -> selected SKILL.md + evidence
  -> Spring AI / Codex CLI diagnosis
```

The model never receives JDBC credentials, a write repository, a transfer service, or an arbitrary-SQL tool. It returns skill and identifier choices only. Java validates those choices and maps them to fixed, parameterized, row-bounded queries.

## Prerequisites

- Java 21 or newer
- Maven 3.9 or newer
- Codex CLI available as `codex`
- A ChatGPT login visible to the same OS user that runs Spring Boot

Confirm the local Codex session once:

```bash
codex login status
```

If needed, authenticate with the ChatGPT flow rather than an API key:

```bash
codex login
```

## Run

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.

The H2 database persists at `./data/p2p-sim.mv.db`. To return to the original seed state, stop the application and remove the `data` directory.

## Useful troubleshooting prompts

- `Why did P2P-000102 fail?`
- `Why can CUS-1003 not receive money?`
- `Was P2P-000105 debited without a credit?`
- `Why is P2P-000104 under review?`
- `Show the latest transfers for CUS-1001.`

## Agent skills

Skills live under `src/main/resources/agent-skills`:

- `p2p-triage`: selects the domain skills and extracts stable references.
- `customer-wallet-eligibility`: customer, KYC, account, wallet, balance, and daily-limit evidence.
- `transfer-lifecycle`: current transfer state, attempts, events, failures, and timeouts.
- `risk-compliance`: risk decisions, scores, blocks, and reviews.
- `ledger-reconciliation`: debit, credit, reversal, and balance consistency.

Each skill declares its allowed diagnostic views and rules. The application loads the triage skill first, validates its output, then loads only the selected skill documents for the answer call.

## Read-only boundary

The application uses two H2 identities:

- The simulator uses the primary writer data source.
- The chat path uses `DIAG_READER`, which has `SELECT` grants only on eight curated `DIAG` views.

Additional controls:

- No raw SQL is accepted from Codex or the browser.
- The UI can display server-owned fixed SELECT statements for explainability, but it cannot submit or modify SQL.
- Query identifiers are selected from a Java allow-list.
- All values use bound parameters and result sets are capped.
- The diagnostic pool is read-only and has a three-second H2 query timeout.
- PII is omitted from diagnostic views.
- `codex exec` runs with `--ephemeral --sandbox read-only` in an isolated temporary directory.
- Common secret-bearing environment variables are removed from the subprocess.

`read-only` is an application security property here, not just a prompt instruction.

## Configuration

Environment variables can override the local defaults:

| Variable | Default | Purpose |
| --- | --- | --- |
| `P2P_DB_URL` | `jdbc:h2:file:./data/p2p-sim;AUTO_SERVER=TRUE` | Local H2 database |
| `P2P_DB_USER` | `sa` | Simulator writer user |
| `P2P_DB_PASSWORD` | empty | Simulator writer password |
| `P2P_DIAG_USER` | `DIAG_READER` | Troubleshooting reader user |
| `P2P_DIAG_PASSWORD` | `diag-reader-local` | Troubleshooting reader password |
| `CODEX_EXECUTABLE` | `codex` | Codex CLI path |
| `CODEX_TIMEOUT` | `120s` | Per-call timeout |
| `CODEX_WORKSPACE` | OS temp directory | Isolated Codex working directory |

This is a local simulation. A production service should replace desktop subscription authentication, demo credentials, local H2 storage, and in-process authorization with production-grade equivalents.

## Framework references

- [Spring AI Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- [Spring AI Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI dependency management](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
