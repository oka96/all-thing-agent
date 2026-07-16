# P2P Clearing Desk

A local P2P platform split into three independently runnable applications. The simulator owns transfer state and H2, the intelligence service owns Spring AI and Codex orchestration, and the frontend owns the browser experience.

## Applications

| Application | Default URL | Responsibility |
| --- | --- | --- |
| `simulator-app` | `http://127.0.0.1:8081` | Transfer commands, customer/history queries, H2 schema and data, read-only diagnostic evidence |
| `intelligence-app` | `http://127.0.0.1:8082` | Troubleshooting chat, skill routing, Codex CLI integration, evidence validation |
| `frontend-app` | `http://localhost:8080` | Transfer workbench, chat, decision trace, expandable SQL and raw-row evidence |

The root Maven project aggregates the two Spring Boot applications. The frontend is an independent Vite application.

## Architecture

```text
Browser
  -> frontend-app :8080
       -> /api/simulator/**       -> simulator-app :8081
       -> /api/troubleshooting/** -> intelligence-app :8082
       -> /api/system/**          -> intelligence-app :8082

intelligence-app
  -> POST /internal/diagnostics/snapshot
  -> bearer-authenticated server-to-server request
  -> simulator-app
  -> DIAG_READER
  -> allow-listed DIAG views
  -> fixed SELECT statements and redacted rows
```

Only `simulator-app` contains JDBC, H2, schema initialization, or transfer mutation code. The intelligence service cannot submit SQL and fails closed when diagnostic evidence is unavailable, malformed, non-`SELECT`, or outside the `DIAG.*` views.

## Prerequisites

- Java 21 or newer
- Maven 3.9 or newer
- Node.js 20 or newer
- Codex CLI available as `codex`
- A ChatGPT login visible to the OS user running `intelligence-app`

Confirm the Codex session:

```bash
codex login status
```

## Run locally

Start each application in a separate terminal from the repository root.

### 1. Simulator

```bash
mvn -pl simulator-app spring-boot:run
```

### 2. Intelligence

```bash
mvn -pl intelligence-app spring-boot:run
```

### 3. Frontend

```bash
cd frontend-app
npm install
npm run dev
```

Open `http://localhost:8080`.

The Vite development server proxies simulator calls to port `8081` and intelligence calls to port `8082`. The H2 database persists at `./data/p2p-sim.mv.db` when the simulator is launched from the repository root.

## Build

Build both Spring Boot applications:

```bash
mvn package
```

Build the frontend:

```bash
cd frontend-app
npm install
npm run build
```

The frontend production output is written to `frontend-app/dist`.

## Public APIs

### Simulator

- `GET /api/simulator/users`
- `GET /api/simulator/transfers?limit=14`
- `POST /api/simulator/transfers`

### Intelligence

- `GET /api/system/status`
- `POST /api/troubleshooting/chat`

### Internal diagnostics

- `POST /internal/diagnostics/snapshot`

The internal endpoint accepts customer/transfer references and allow-listed diagnostic domains only. It never accepts SQL. It requires the same `SIMULATOR_DIAGNOSTIC_TOKEN` in both backend applications and is not exposed through the frontend proxy.

## Configuration

### Simulator environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `SIMULATOR_PORT` | `8081` | HTTP port |
| `SIMULATOR_BIND` | `127.0.0.1` | Bind address |
| `P2P_DB_URL` | `jdbc:h2:file:./data/p2p-sim;AUTO_SERVER=TRUE` | H2 database |
| `P2P_DB_USER` | `sa` | Simulator writer |
| `P2P_DB_PASSWORD` | empty | Writer password |
| `P2P_DIAG_USER` | `DIAG_READER` | Diagnostic reader |
| `P2P_DIAG_PASSWORD` | `diag-reader-local` | Diagnostic reader password |
| `SIMULATOR_DIAGNOSTIC_TOKEN` | `local-diagnostic-token` | Internal API bearer token |
| `P2P_FRONTEND_ORIGINS` | `http://localhost:8080` | Allowed public API origins |

### Intelligence environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `INTELLIGENCE_PORT` | `8082` | HTTP port |
| `SIMULATOR_BASE_URL` | `http://127.0.0.1:8081` | Simulator service URL |
| `SIMULATOR_DIAGNOSTIC_TOKEN` | `local-diagnostic-token` | Internal API bearer token |
| `SIMULATOR_CONNECT_TIMEOUT` | `2s` | Simulator connection timeout |
| `SIMULATOR_READ_TIMEOUT` | `5s` | Simulator response timeout |
| `CODEX_EXECUTABLE` | `codex` | Codex CLI path |
| `CODEX_TIMEOUT` | `120s` | Per-call timeout |
| `CODEX_WORKSPACE` | OS temp directory | Isolated Codex workspace |
| `P2P_FRONTEND_ORIGINS` | `http://localhost:8080` | Allowed chat API origins |

### Frontend environment

Create `frontend-app/.env` when the backend targets differ:

```dotenv
P2P_SIMULATOR_URL=http://127.0.0.1:8081
P2P_INTELLIGENCE_URL=http://127.0.0.1:8082
```

For a static deployment without a reverse proxy, set `simulatorBaseUrl` and `intelligenceBaseUrl` in `frontend-app/runtime-config.js`. Leave both empty for same-origin routing or the Vite development proxies.

## Agent skills

The intelligence service owns the skills under `intelligence-app/src/main/resources/agent-skills`:

- `p2p-triage`
- `customer-wallet-eligibility`
- `transfer-lifecycle`
- `risk-compliance`
- `ledger-reconciliation`

The service loads triage first, validates its route, fetches bounded evidence from the simulator, and loads only the selected domain skills for the final diagnosis.

## Security boundaries

- Browser writes go only to `/api/simulator/transfers`.
- Browser chat goes only to `intelligence-app`.
- The internal diagnostic endpoint is bearer protected and excluded from CORS.
- Diagnostic SQL is fixed inside `simulator-app`.
- `DIAG_READER` has `SELECT` grants only on curated diagnostic views.
- Intelligence validates returned SQL, views, counts, and row structure before prompting Codex.
- Codex receives no database credentials or transfer command capability.
- Raw query evidence shown in the UI is server-owned and cannot be edited or executed.

This remains a local simulation. Production deployments should replace local bearer tokens, desktop subscription authentication, demo H2 credentials, and process-local authorization with managed secrets and service identity.
