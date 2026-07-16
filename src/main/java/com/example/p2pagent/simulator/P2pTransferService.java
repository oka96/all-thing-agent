package com.example.p2pagent.simulator;

import static com.example.p2pagent.api.ApiModels.TransferRequest;
import static com.example.p2pagent.api.ApiModels.TransferResult;

import com.example.p2pagent.api.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class P2pTransferService {

    private static final BigDecimal RISK_REVIEW_THRESHOLD = new BigDecimal("750.00");
    private static final BigDecimal RISK_BLOCK_THRESHOLD = new BigDecimal("1000.00");

    private final JdbcClient jdbcClient;
    private final SimulatorQueryService queryService;

    public P2pTransferService(JdbcClient jdbcClient, SimulatorQueryService queryService) {
        this.jdbcClient = jdbcClient;
        this.queryService = queryService;
    }

    @Transactional
    public TransferResult transfer(TransferRequest request, String idempotencyHeader) {
        if (request.senderCustomerRef().equalsIgnoreCase(request.receiverCustomerRef())) {
            throw new DomainException(
                    "SAME_CUSTOMER", "Sender and receiver must be different customers.", HttpStatus.BAD_REQUEST);
        }

        BigDecimal amount = parseAmount(request.amount());
        String senderRef = normalizeRef(request.senderCustomerRef());
        String receiverRef = normalizeRef(request.receiverCustomerRef());
        String idempotencyKey = normalizeIdempotencyKey(idempotencyHeader);

        List<String> lockOrder = List.of(senderRef, receiverRef).stream().sorted().toList();
        Map<String, WalletState> wallets = lockOrder.stream()
                .map(this::lockWallet)
                .collect(Collectors.toMap(WalletState::customerRef, Function.identity()));
        WalletState sender = wallets.get(senderRef);
        WalletState receiver = wallets.get(receiverRef);

        String existing = findExisting(sender.walletRef(), idempotencyKey);
        if (existing != null) {
            return result(existing, senderRef, receiverRef);
        }

        String transferRef = newTransferRef();
        insertTransfer(transferRef, sender, receiver, amount, request.note(), idempotencyKey);
        event(transferRef, 1, "TRANSFER_CREATED", null, "CREATED", null,
                "Transfer accepted by the simulator.");
        transition(transferRef, "VALIDATING", null, null, false);
        event(transferRef, 2, "VALIDATION_STARTED", "CREATED", "VALIDATING", null,
                "Eligibility, funds, limits, and risk checks started.");

        Failure eligibility = eligibilityFailure(sender, receiver);
        if (eligibility != null) {
            attempt(transferRef, "ELIGIBILITY", "FAILED", eligibility.code(), eligibility.detail());
            return stop(transferRef, "REJECTED", eligibility, 3, senderRef, receiverRef);
        }
        attempt(transferRef, "ELIGIBILITY", "SUCCEEDED", null, "Both parties are eligible.");

        if (sender.availableBalance().compareTo(amount) < 0) {
            Failure failure = new Failure(
                    "INSUFFICIENT_FUNDS",
                    sender.displayName() + " has MYR " + money(sender.availableBalance()) + " available.");
            attempt(transferRef, "FUNDS_CHECK", "FAILED", failure.code(), failure.detail());
            return stop(transferRef, "FAILED", failure, 3, senderRef, receiverRef);
        }

        if (sender.outboundToday().add(amount).compareTo(sender.dailyOutboundLimit()) > 0) {
            Failure failure = new Failure(
                    "DAILY_LIMIT_EXCEEDED",
                    "The transfer would exceed " + sender.displayName() + "'s MYR "
                            + money(sender.dailyOutboundLimit()) + " daily outbound limit.");
            attempt(transferRef, "FUNDS_CHECK", "FAILED", failure.code(), failure.detail());
            return stop(transferRef, "REJECTED", failure, 3, senderRef, receiverRef);
        }
        attempt(transferRef, "FUNDS_CHECK", "SUCCEEDED", null, "Funds and daily limit are sufficient.");

        if (amount.compareTo(RISK_BLOCK_THRESHOLD) >= 0 || "HIGH".equals(sender.riskTier())) {
            risk(transferRef, "BLOCK", 94, "RISK_BLOCKED", "Amount or customer risk tier requires a block.");
            attempt(transferRef, "RISK", "FAILED", "RISK_BLOCKED", "Risk policy blocked the transfer.");
            return stop(
                    transferRef,
                    "REJECTED",
                    new Failure("RISK_BLOCKED", "Risk controls blocked this transfer."),
                    3,
                    senderRef,
                    receiverRef);
        }

        boolean mediumRiskReview = "MEDIUM".equals(sender.riskTier())
                && amount.compareTo(new BigDecimal("500.00")) >= 0;
        if (amount.compareTo(RISK_REVIEW_THRESHOLD) >= 0 || mediumRiskReview) {
            risk(transferRef, "REVIEW", 72, "RISK_REVIEW_REQUIRED",
                    "Amount or velocity requires manual review in this simulation.");
            attempt(transferRef, "RISK", "SUCCEEDED", "RISK_REVIEW_REQUIRED",
                    "Risk decision is REVIEW; no funds moved.");
            return stop(
                    transferRef,
                    "PENDING_REVIEW",
                    new Failure("RISK_REVIEW_REQUIRED", "The transfer is waiting for risk review; no funds moved."),
                    3,
                    senderRef,
                    receiverRef);
        }

        risk(transferRef, "PASS", 18, null, "Risk checks passed.");
        attempt(transferRef, "RISK", "SUCCEEDED", null, "Risk checks passed.");
        transition(transferRef, "PROCESSING", null, null, false);
        event(transferRef, 3, "POSTING_STARTED", "VALIDATING", "PROCESSING", null,
                "Atomic wallet and ledger posting started.");

        BigDecimal senderAfter = sender.availableBalance().subtract(amount);
        BigDecimal receiverAfter = receiver.availableBalance().add(amount);
        jdbcClient.sql("""
                        UPDATE wallet
                        SET available_balance = :balance,
                            outbound_today = outbound_today + :amount,
                            version = version + 1
                        WHERE wallet_ref = :walletRef
                        """)
                .param("balance", senderAfter)
                .param("amount", amount)
                .param("walletRef", sender.walletRef())
                .update();
        attempt(transferRef, "DEBIT", "SUCCEEDED", null, "Sender wallet debit posted.");
        ledger(transferRef, sender.walletRef(), "DEBIT", "TRANSFER", amount, senderAfter);

        jdbcClient.sql("""
                        UPDATE wallet
                        SET available_balance = :balance,
                            version = version + 1
                        WHERE wallet_ref = :walletRef
                        """)
                .param("balance", receiverAfter)
                .param("walletRef", receiver.walletRef())
                .update();
        attempt(transferRef, "CREDIT", "SUCCEEDED", null, "Receiver wallet credit posted.");
        ledger(transferRef, receiver.walletRef(), "CREDIT", "TRANSFER", amount, receiverAfter);

        transition(transferRef, "COMPLETED", null, null, true);
        event(transferRef, 4, "TRANSFER_COMPLETED", "PROCESSING", "COMPLETED", null,
                "Debit and credit ledger entries posted atomically.");
        return result(transferRef, senderRef, receiverRef);
    }

    private TransferResult stop(
            String transferRef,
            String status,
            Failure failure,
            int eventSequence,
            String senderRef,
            String receiverRef) {
        transition(transferRef, status, failure.code(), failure.detail(), false);
        event(transferRef, eventSequence, "TRANSFER_" + status, "VALIDATING", status,
                failure.code(), failure.detail());
        return result(transferRef, senderRef, receiverRef);
    }

    private Failure eligibilityFailure(WalletState sender, WalletState receiver) {
        if (!"ACTIVE".equals(sender.accountStatus())) {
            return new Failure("SENDER_RESTRICTED", "The sender account is " + sender.accountStatus() + ".");
        }
        if (!"VERIFIED".equals(sender.kycStatus())) {
            return new Failure("KYC_NOT_VERIFIED", "The sender KYC status is " + sender.kycStatus() + ".");
        }
        if (!"ACTIVE".equals(receiver.accountStatus())) {
            return new Failure("RECEIVER_RESTRICTED", "The receiver account is " + receiver.accountStatus() + ".");
        }
        if (!"VERIFIED".equals(receiver.kycStatus())) {
            return new Failure("KYC_NOT_VERIFIED", "The receiver KYC status is " + receiver.kycStatus() + ".");
        }
        if (!"ACTIVE".equals(sender.walletStatus()) || !"ACTIVE".equals(receiver.walletStatus())) {
            return new Failure("WALLET_FROZEN", "The sender or receiver wallet is not active.");
        }
        if (!sender.currency().equals(receiver.currency())) {
            return new Failure("CURRENCY_MISMATCH", "Both wallets must use the same currency.");
        }
        return null;
    }

    private WalletState lockWallet(String customerRef) {
        return jdbcClient.sql("""
                        SELECT c.customer_ref, c.display_name, c.kyc_status, c.account_status, c.risk_tier,
                               w.wallet_ref, w.currency, w.available_balance, w.daily_outbound_limit,
                               w.outbound_today, w.status AS wallet_status
                        FROM customer c
                        JOIN wallet w ON w.customer_ref = c.customer_ref
                        WHERE c.customer_ref = :customerRef
                        FOR UPDATE
                        """)
                .param("customerRef", customerRef)
                .query(this::mapWallet)
                .optional()
                .orElseThrow(() -> new DomainException(
                        "CUSTOMER_NOT_FOUND", "Customer " + customerRef + " was not found.", HttpStatus.NOT_FOUND));
    }

    private WalletState mapWallet(ResultSet rs, int rowNum) throws SQLException {
        return new WalletState(
                rs.getString("customer_ref"),
                rs.getString("display_name"),
                rs.getString("kyc_status"),
                rs.getString("account_status"),
                rs.getString("risk_tier"),
                rs.getString("wallet_ref"),
                rs.getString("currency"),
                rs.getBigDecimal("available_balance"),
                rs.getBigDecimal("daily_outbound_limit"),
                rs.getBigDecimal("outbound_today"),
                rs.getString("wallet_status"));
    }

    private void insertTransfer(
            String transferRef,
            WalletState sender,
            WalletState receiver,
            BigDecimal amount,
            String note,
            String idempotencyKey) {
        jdbcClient.sql("""
                        INSERT INTO p2p_transfer (
                            transfer_ref, sender_wallet_ref, receiver_wallet_ref, amount, currency,
                            note, status, idempotency_key, initiated_at, updated_at, version)
                        VALUES (
                            :transferRef, :senderWallet, :receiverWallet, :amount, :currency,
                            :note, 'CREATED', :idempotencyKey, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                        """)
                .param("transferRef", transferRef)
                .param("senderWallet", sender.walletRef())
                .param("receiverWallet", receiver.walletRef())
                .param("amount", amount)
                .param("currency", sender.currency())
                .param("note", note == null || note.isBlank() ? null : note.trim())
                .param("idempotencyKey", idempotencyKey)
                .update();
    }

    private void transition(
            String transferRef,
            String status,
            String failureCode,
            String failureDetail,
            boolean completed) {
        jdbcClient.sql("""
                        UPDATE p2p_transfer
                        SET status = :status,
                            failure_code = :failureCode,
                            failure_detail = :failureDetail,
                            updated_at = CURRENT_TIMESTAMP,
                            completed_at = CASE WHEN :completed THEN CURRENT_TIMESTAMP ELSE completed_at END,
                            version = version + 1
                        WHERE transfer_ref = :transferRef
                        """)
                .param("status", status)
                .param("failureCode", failureCode)
                .param("failureDetail", failureDetail)
                .param("completed", completed)
                .param("transferRef", transferRef)
                .update();
    }

    private void attempt(String transferRef, String stage, String status, String code, String detail) {
        jdbcClient.sql("""
                        INSERT INTO transfer_attempt (
                            attempt_ref, transfer_ref, attempt_no, stage, status,
                            error_code, error_detail, started_at, finished_at)
                        VALUES (
                            :attemptRef, :transferRef, 1, :stage, :status,
                            :code, :detail, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .param("attemptRef", "ATT-" + UUID.randomUUID())
                .param("transferRef", transferRef)
                .param("stage", stage)
                .param("status", status)
                .param("code", code)
                .param("detail", detail)
                .update();
    }

    private void risk(String transferRef, String decision, int score, String code, String detail) {
        jdbcClient.sql("""
                        INSERT INTO risk_check (
                            risk_check_ref, transfer_ref, check_type, decision, score,
                            reason_code, reason_detail, checked_at)
                        VALUES (
                            :riskRef, :transferRef, 'P2P_POLICY', :decision, :score,
                            :code, :detail, CURRENT_TIMESTAMP)
                        """)
                .param("riskRef", "RSK-" + UUID.randomUUID())
                .param("transferRef", transferRef)
                .param("decision", decision)
                .param("score", score)
                .param("code", code)
                .param("detail", detail)
                .update();
    }

    private void ledger(
            String transferRef,
            String walletRef,
            String direction,
            String kind,
            BigDecimal amount,
            BigDecimal balanceAfter) {
        jdbcClient.sql("""
                        INSERT INTO ledger_entry (
                            entry_ref, transfer_ref, wallet_ref, direction, entry_kind,
                            amount, status, balance_after, created_at)
                        VALUES (
                            :entryRef, :transferRef, :walletRef, :direction, :kind,
                            :amount, 'POSTED', :balanceAfter, CURRENT_TIMESTAMP)
                        """)
                .param("entryRef", "LED-" + UUID.randomUUID())
                .param("transferRef", transferRef)
                .param("walletRef", walletRef)
                .param("direction", direction)
                .param("kind", kind)
                .param("amount", amount)
                .param("balanceAfter", balanceAfter)
                .update();
    }

    private void event(
            String transferRef,
            int sequence,
            String type,
            String fromStatus,
            String toStatus,
            String failureCode,
            String detail) {
        jdbcClient.sql("""
                        INSERT INTO transfer_event (
                            event_ref, transfer_ref, sequence_no, event_type, from_status,
                            to_status, failure_code, detail, actor, occurred_at)
                        VALUES (
                            :eventRef, :transferRef, :sequence, :type, :fromStatus,
                            :toStatus, :failureCode, :detail, 'SIMULATOR', CURRENT_TIMESTAMP)
                        """)
                .param("eventRef", "EVT-" + UUID.randomUUID())
                .param("transferRef", transferRef)
                .param("sequence", sequence)
                .param("type", type)
                .param("fromStatus", fromStatus)
                .param("toStatus", toStatus)
                .param("failureCode", failureCode)
                .param("detail", detail)
                .update();
    }

    private String findExisting(String senderWalletRef, String idempotencyKey) {
        return jdbcClient.sql("""
                        SELECT transfer_ref
                        FROM p2p_transfer
                        WHERE sender_wallet_ref = :senderWalletRef
                          AND idempotency_key = :idempotencyKey
                        """)
                .param("senderWalletRef", senderWalletRef)
                .param("idempotencyKey", idempotencyKey)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private TransferResult result(String transferRef, String senderRef, String receiverRef) {
        return new TransferResult(
                queryService.transfer(transferRef),
                queryService.balances(senderRef, receiverRef));
    }

    private BigDecimal parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw.trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return amount;
        }
        catch (ArithmeticException | NumberFormatException exception) {
            throw new DomainException(
                    "INVALID_AMOUNT",
                    "Amount must be positive and contain no more than two decimal places.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeRef(String reference) {
        return reference.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (value.length() > 80) {
            throw new DomainException(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be at most 80 characters.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String newTransferRef() {
        return "P2P-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    private record WalletState(
            String customerRef,
            String displayName,
            String kycStatus,
            String accountStatus,
            String riskTier,
            String walletRef,
            String currency,
            BigDecimal availableBalance,
            BigDecimal dailyOutboundLimit,
            BigDecimal outboundToday,
            String walletStatus) {
    }

    private record Failure(String code, String detail) {
    }
}

