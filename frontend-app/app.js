const state = {
    users: [],
    transfers: [],
    selectedCustomerRef: null,
    lastTransferRef: null,
    busy: false,
};

const elements = {
    accountStrip: document.querySelector("#account-strip"),
    sender: document.querySelector("#sender"),
    receiver: document.querySelector("#receiver"),
    amount: document.querySelector("#amount"),
    note: document.querySelector("#note"),
    transferForm: document.querySelector("#transfer-form"),
    transferSubmit: document.querySelector("#transfer-submit"),
    formError: document.querySelector("#form-error"),
    preview: document.querySelector("#transfer-preview"),
    receipt: document.querySelector("#receipt"),
    historyList: document.querySelector("#history-list"),
    refreshHistory: document.querySelector("#refresh-history"),
    systemStatus: document.querySelector("#system-status"),
    chatForm: document.querySelector("#chat-form"),
    chatInput: document.querySelector("#chat-input"),
    chatSubmit: document.querySelector("#chat-submit"),
    chatTranscript: document.querySelector("#chat-transcript"),
    chatContext: document.querySelector("#chat-context"),
    diagSkills: document.querySelector("#diag-skills"),
    diagTables: document.querySelector("#diag-tables"),
    diagRequest: document.querySelector("#diag-request"),
};

document.addEventListener("DOMContentLoaded", init);

async function init() {
    bindEvents();
    await Promise.allSettled([loadSystemStatus(), loadUsers(), loadTransfers()]);
}

function bindEvents() {
    elements.transferForm.addEventListener("submit", postTransfer);
    elements.sender.addEventListener("change", () => selectCustomer(elements.sender.value));
    elements.receiver.addEventListener("change", updatePreview);
    elements.amount.addEventListener("input", updatePreview);
    elements.refreshHistory.addEventListener("click", loadTransfers);
    elements.chatForm.addEventListener("submit", sendChat);
    elements.chatInput.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            elements.chatForm.requestSubmit();
        }
    });
    document.querySelectorAll("[data-amount]").forEach(button => {
        button.addEventListener("click", () => {
            elements.amount.value = button.dataset.amount;
            updatePreview();
            elements.amount.focus();
        });
    });
    document.querySelectorAll("[data-prompt]").forEach(button => {
        button.addEventListener("click", () => {
            elements.chatInput.value = button.dataset.prompt;
            elements.chatInput.focus();
        });
    });
}

async function loadSystemStatus() {
    try {
        const status = await api("/api/system/status");
        elements.systemStatus.classList.add("online");
        elements.systemStatus.lastChild.textContent = ` ${status.intelligence}`;
    } catch (error) {
        elements.systemStatus.lastChild.textContent = " API unavailable";
    }
}

async function loadUsers() {
    try {
        const response = await api("/api/simulator/users");
        state.users = response.users;
        if (!state.selectedCustomerRef && state.users.length) {
            state.selectedCustomerRef = state.users[0].customerRef;
        }
        renderUsers();
        enableTransferForm();
    } catch (error) {
        elements.accountStrip.innerHTML = `<div class="empty-list">${escapeHtml(error.message)} <button type="button" onclick="location.reload()">Retry</button></div>`;
    }
}

async function loadTransfers() {
    elements.refreshHistory.disabled = true;
    try {
        const response = await api("/api/simulator/transfers?limit=14");
        state.transfers = response.transfers;
        renderHistory();
    } catch (error) {
        elements.historyList.innerHTML = `<div class="empty-list">${escapeHtml(error.message)}</div>`;
    } finally {
        elements.refreshHistory.disabled = false;
    }
}

function renderUsers() {
    elements.accountStrip.innerHTML = state.users.map(user => `
        <button class="account-card ${user.customerRef === state.selectedCustomerRef ? "selected" : ""}"
                type="button" data-customer-ref="${escapeHtml(user.customerRef)}"
                aria-pressed="${user.customerRef === state.selectedCustomerRef}">
            <span class="account-card-top">
                <span class="avatar">${escapeHtml(user.initials)}</span>
                <i class="state-dot ${user.accountStatus === "ACTIVE" && user.walletStatus === "ACTIVE" ? "" : "restricted"}" title="${escapeHtml(user.accountStatus)}"></i>
            </span>
            <h3>${escapeHtml(user.displayName)}</h3>
            <span class="account-ref">${escapeHtml(user.customerRef)}</span>
            <div class="account-balance">${formatMoney(user.availableBalance)}</div>
            <span class="account-meta">
                <span>${escapeHtml(user.kycStatus)}</span>
                <span>${escapeHtml(user.walletStatus)}</span>
                <span>${escapeHtml(user.riskTier)} risk</span>
            </span>
        </button>
    `).join("");

    elements.accountStrip.querySelectorAll("[data-customer-ref]").forEach(card => {
        card.addEventListener("click", () => selectCustomer(card.dataset.customerRef));
    });

    const options = state.users.map(user =>
        `<option value="${escapeHtml(user.customerRef)}">${escapeHtml(user.displayName)} · ${formatMoney(user.availableBalance)}</option>`
    ).join("");
    elements.sender.innerHTML = options;
    elements.sender.value = state.selectedCustomerRef;
    renderReceiverOptions();
    updateChatContext();
    updatePreview();
}

function renderReceiverOptions() {
    const previous = elements.receiver.value;
    const eligible = state.users.filter(user => user.customerRef !== state.selectedCustomerRef);
    elements.receiver.innerHTML = `<option value="">Select recipient</option>` + eligible.map(user =>
        `<option value="${escapeHtml(user.customerRef)}">${escapeHtml(user.displayName)} · ${escapeHtml(user.accountStatus)}</option>`
    ).join("");
    if (eligible.some(user => user.customerRef === previous)) {
        elements.receiver.value = previous;
    }
}

function selectCustomer(customerRef) {
    state.selectedCustomerRef = customerRef;
    elements.sender.value = customerRef;
    renderUsers();
}

function enableTransferForm() {
    [elements.sender, elements.receiver, elements.amount, elements.note, elements.transferSubmit]
        .forEach(element => element.disabled = false);
}

function setTransferBusy(busy) {
    state.busy = busy;
    [elements.sender, elements.receiver, elements.amount, elements.note, elements.transferSubmit]
        .forEach(element => element.disabled = busy);
    elements.transferSubmit.querySelector("span").textContent = busy ? "Posting transfer..." : "Post mock transfer";
}

function updatePreview() {
    const sender = state.users.find(user => user.customerRef === state.selectedCustomerRef);
    const amount = parseAmount(elements.amount.value);
    if (!sender) return;
    const projected = Number(sender.availableBalance) - amount;
    elements.preview.querySelector("strong").textContent = formatMoney(projected);
    elements.preview.classList.toggle("warning", projected < 0);
    elements.formError.textContent = projected < 0
        ? "This intentionally simulates an insufficient-funds failure; the backend will record diagnostic evidence."
        : "";
}

async function postTransfer(event) {
    event.preventDefault();
    const amount = elements.amount.value.trim();
    const receiver = elements.receiver.value;
    elements.formError.textContent = "";
    if (!receiver) {
        elements.formError.textContent = "Select a recipient.";
        return;
    }
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) {
        elements.formError.textContent = "Enter a positive amount with at most two decimals.";
        return;
    }

    setTransferBusy(true);
    try {
        const result = await api("/api/simulator/transfers", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Idempotency-Key": crypto.randomUUID(),
            },
            body: JSON.stringify({
                senderCustomerRef: state.selectedCustomerRef,
                receiverCustomerRef: receiver,
                amount,
                note: elements.note.value.trim() || null,
            }),
        });
        state.lastTransferRef = result.transfer.transferRef;
        renderReceipt(result.transfer);
        elements.amount.value = "";
        elements.note.value = "";
        await Promise.all([loadUsers(), loadTransfers()]);
        updateChatContext();
    } catch (error) {
        elements.formError.textContent = `${error.code || "ERROR"}: ${error.message}`;
    } finally {
        setTransferBusy(false);
        updatePreview();
    }
}

function renderReceipt(transfer) {
    const statusClass = transfer.status.toLowerCase();
    const detail = transfer.failureDetail || "All simulator checks and ledger postings completed.";
    elements.receipt.innerHTML = `
        <div class="receipt-result">
            <div>
                <p class="eyebrow">${escapeHtml(transfer.transferRef)} · ${formatDate(transfer.initiatedAt)}</p>
                <h3>${escapeHtml(transfer.status.replaceAll("_", " "))}</h3>
                <div class="receipt-route">
                    <span>${escapeHtml(transfer.senderName)}</span><i>→</i><span>${escapeHtml(transfer.receiverName)}</span>
                </div>
                <p><strong>${formatMoney(transfer.amount)}</strong> · ${escapeHtml(detail)}</p>
            </div>
            <div class="status-stamp ${statusClass}">${escapeHtml(transfer.status.replaceAll("_", " "))}</div>
        </div>`;
}

function renderHistory() {
    if (!state.transfers.length) {
        elements.historyList.innerHTML = `<div class="empty-list">No transfers yet.</div>`;
        return;
    }
    elements.historyList.innerHTML = state.transfers.map(transfer => {
        const statusClass = transfer.status.toLowerCase();
        return `
            <article class="history-row">
                <button class="history-summary" type="button" aria-expanded="false">
                    <span class="history-ref">${escapeHtml(transfer.transferRef)}</span>
                    <span class="history-route">${escapeHtml(transfer.senderName)} → ${escapeHtml(transfer.receiverName)}</span>
                    <span class="history-amount">${formatMoney(transfer.amount)}</span>
                    <span class="status-pill ${statusClass}">${escapeHtml(transfer.status.replaceAll("_", " "))}</span>
                </button>
                <div class="history-detail">
                    <span>Failure code<strong>${escapeHtml(transfer.failureCode || "None")}</strong></span>
                    <span>Detail<strong>${escapeHtml(transfer.failureDetail || transfer.note || "Completed normally")}</strong></span>
                    <span>Initiated<strong>${formatDate(transfer.initiatedAt)}</strong></span>
                </div>
            </article>`;
    }).join("");
    elements.historyList.querySelectorAll(".history-summary").forEach(button => {
        button.addEventListener("click", () => {
            const row = button.closest(".history-row");
            row.classList.toggle("open");
            button.setAttribute("aria-expanded", row.classList.contains("open"));
        });
    });
}

async function sendChat(event) {
    event.preventDefault();
    const message = elements.chatInput.value.trim();
    if (!message || elements.chatSubmit.disabled) return;

    appendMessage("You", message, "user-message");
    elements.chatInput.value = "";
    elements.chatSubmit.disabled = true;
    const typing = appendTyping();
    try {
        const response = await api("/api/troubleshooting/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                message,
                customerRef: explicitCustomerRef(message),
                transferRef: explicitTransferRef(message),
            }),
        });
        typing.remove();
        const assistantMessage = appendMessage("Troubleshooter", response.answer, "assistant-message");
        appendExplainability(assistantMessage, response);
        elements.diagSkills.textContent = response.skills.join(", ") || "Not routed";
        elements.diagTables.textContent = response.tables.join(", ") || "No query";
        elements.diagRequest.textContent = `${response.requestId} · ${response.queryCount} queries`;
        elements.diagSkills.title = elements.diagSkills.textContent;
        elements.diagTables.title = elements.diagTables.textContent;
    } catch (error) {
        typing.remove();
        appendMessage("System", `${error.code || "CHAT_ERROR"}: ${error.message}`, "assistant-message");
    } finally {
        elements.chatSubmit.disabled = false;
        elements.chatInput.focus();
    }
}

function appendExplainability(message, response) {
    const reasoningTrace = Array.isArray(response.reasoningTrace) ? response.reasoningTrace : [];
    const queries = Array.isArray(response.queries) ? response.queries : [];
    if (!reasoningTrace.length && !queries.length) return;

    message.classList.add("explainable");
    const inspector = document.createElement("div");
    inspector.className = "explainability";
    inspector.innerHTML = `
        <details class="audit-section">
            <summary>
                <span>Decision trace</span>
                <small>${reasoningTrace.length} inspectable steps</small>
            </summary>
            <ol class="reasoning-trace">
                ${reasoningTrace.map(step => `
                    <li>
                        <strong>${escapeHtml(step.stage)}</strong>
                        <p>${escapeHtml(step.summary)}</p>
                    </li>`).join("")}
            </ol>
        </details>
        <details class="audit-section">
            <summary>
                <span>Database evidence</span>
                <small>${queries.length} read-only ${queries.length === 1 ? "query" : "queries"}</small>
            </summary>
            <div class="query-inspector">
                ${queries.length ? queries.map(renderQueryEvidence).join("") : '<p class="no-query-evidence">No query ran because a valid customer or transfer reference was not available.</p>'}
            </div>
        </details>`;
    message.append(inspector);
    elements.chatTranscript.scrollTop = elements.chatTranscript.scrollHeight;
}

function renderQueryEvidence(query, index) {
    const parameters = Object.entries(query.parameters || {});
    const rows = Array.isArray(query.rows) ? query.rows : [];
    return `
        <details class="query-card">
            <summary>
                <span><i>${String(index + 1).padStart(2, "0")}</i>${escapeHtml(query.view)}</span>
                <small>${rows.length} ${rows.length === 1 ? "row" : "rows"}</small>
            </summary>
            <div class="query-body">
                <p class="query-purpose">${escapeHtml(query.purpose)}</p>
                <div class="sql-panel">
                    <span>Fixed parameterized SQL · SELECT only</span>
                    <pre><code>${escapeHtml(query.sql)}</code></pre>
                </div>
                <div class="query-parameters">
                    <span>Bound parameters</span>
                    ${parameters.length
                        ? parameters.map(([name, value]) => `<code>:${escapeHtml(name)} = ${escapeHtml(value)}</code>`).join("")
                        : "<code>none</code>"}
                </div>
                ${renderRawRows(rows, query.parameters || {}, query.sql || "")}
            </div>
        </details>`;
}

function renderRawRows(rows, parameters = {}, sql = "") {
    if (!rows.length) {
        return '<div class="no-query-evidence">Query completed with no matching rows.</div>';
    }
    const columns = [...new Set(rows.flatMap(row => Object.keys(row)))];
    const rowFilterParameters = boundRowFilterParameters(parameters, sql);
    return `
        <div class="raw-table-wrap" tabindex="0" aria-label="Scrollable raw query results">
            <table class="raw-table">
                <thead><tr>${columns.map(column => `<th>${escapeHtml(column)}</th>`).join("")}</tr></thead>
                <tbody>
                    ${rows.map(row => `<tr>${columns.map(column => {
                        const matches = matchingBoundParameters(row[column], rowFilterParameters);
                        const boundLabel = matches.map(([name]) => `:${name}`).join(", ");
                        return `<td class="${matches.length ? "bound-cell" : ""}"
                                    ${matches.length
                                        ? `data-bound-parameter="${escapeHtml(boundLabel)}" title="Matches ${escapeHtml(boundLabel)}"`
                                        : ""}>
                                    ${row[column] == null ? "<em>NULL</em>" : escapeHtml(row[column])}
                                </td>`;
                    }).join("")}</tr>`).join("")}
                </tbody>
            </table>
        </div>`;
}

function boundRowFilterParameters(parameters, sql) {
    const filterSql = String(sql).replace(/\b(?:LIMIT|OFFSET)\s*:[A-Z0-9_]+\b/gi, "");
    return Object.entries(parameters).filter(([name, value]) =>
        value != null && new RegExp(`:${escapeRegExp(name)}\\b`, "i").test(filterSql)
    );
}

function matchingBoundParameters(cellValue, parameters) {
    if (cellValue == null) return [];
    const normalizedCell = String(cellValue).trim().toLowerCase();
    return parameters.filter(([, value]) =>
        String(value).trim().toLowerCase() === normalizedCell
    );
}

function escapeRegExp(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function appendMessage(label, content, className) {
    const message = document.createElement("article");
    message.className = `message ${className}`;
    const labelNode = document.createElement("span");
    labelNode.className = "message-label";
    labelNode.textContent = label;
    const contentNode = document.createElement("p");
    contentNode.textContent = content;
    message.append(labelNode, contentNode);
    elements.chatTranscript.append(message);
    elements.chatTranscript.scrollTop = elements.chatTranscript.scrollHeight;
    return message;
}

function appendTyping() {
    const message = document.createElement("article");
    message.className = "message assistant-message typing";
    message.setAttribute("aria-label", "Troubleshooter is reading diagnostic evidence");
    message.innerHTML = "<i></i><i></i><i></i>";
    elements.chatTranscript.append(message);
    elements.chatTranscript.scrollTop = elements.chatTranscript.scrollHeight;
    return message;
}

function updateChatContext() {
    elements.chatContext.textContent =
        "Each prompt uses only an exact customer name or explicit CUS-* / P2P-* reference";
}

function explicitCustomerRef(message) {
    return message.match(/\bCUS-[A-Z0-9-]+\b/i)?.[0]?.toUpperCase() || null;
}

function explicitTransferRef(message) {
    return message.match(/\bP2P-[A-Z0-9-]+\b/i)?.[0]?.toUpperCase() || null;
}

function apiBaseUrl(url) {
    const config = window.P2P_CONFIG || {};
    const configuredBaseUrl = url.startsWith("/api/simulator")
        ? config.simulatorBaseUrl
        : url.startsWith("/api/troubleshooting") || url.startsWith("/api/system")
            ? config.intelligenceBaseUrl
            : "";
    return String(configuredBaseUrl || "").replace(/\/+$/, "");
}

async function api(url, options = {}) {
    const response = await fetch(apiBaseUrl(url) + url, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
        const error = new Error(body.message || `Request failed with status ${response.status}.`);
        error.code = body.code;
        throw error;
    }
    return body;
}

function parseAmount(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
}

function formatMoney(value) {
    const amount = Number(value);
    return new Intl.NumberFormat("en-MY", { style: "currency", currency: "MYR" }).format(amount || 0);
}

function formatDate(value) {
    if (!value) return "Not completed";
    return new Intl.DateTimeFormat("en-MY", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
