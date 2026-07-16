package com.example.p2pagent.simulator;

import static com.example.p2pagent.simulator.api.ApiModels.TransferView;
import static com.example.p2pagent.simulator.api.ApiModels.UserView;

import com.example.p2pagent.simulator.api.DomainException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SimulatorQueryService {

    private final JdbcClient jdbcClient;

    public SimulatorQueryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UserView> users() {
        return jdbcClient.sql("""
                        SELECT c.customer_ref, c.display_name, c.kyc_status, c.account_status, c.risk_tier,
                               w.wallet_ref, w.currency, w.available_balance, w.reserved_balance,
                               w.daily_outbound_limit, w.outbound_today, w.status AS wallet_status
                        FROM customer c
                        JOIN wallet w ON w.customer_ref = c.customer_ref
                        ORDER BY c.customer_ref
                        """)
                .query(this::mapUser)
                .list();
    }

    public List<TransferView> transfers(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        return jdbcClient.sql("""
                        SELECT t.transfer_ref,
                               sender.customer_ref AS sender_customer_ref,
                               sender.display_name AS sender_name,
                               receiver.customer_ref AS receiver_customer_ref,
                               receiver.display_name AS receiver_name,
                               t.amount, t.currency, t.note, t.status, t.failure_code, t.failure_detail,
                               t.initiated_at, t.completed_at
                        FROM p2p_transfer t
                        JOIN wallet sw ON sw.wallet_ref = t.sender_wallet_ref
                        JOIN customer sender ON sender.customer_ref = sw.customer_ref
                        JOIN wallet rw ON rw.wallet_ref = t.receiver_wallet_ref
                        JOIN customer receiver ON receiver.customer_ref = rw.customer_ref
                        ORDER BY t.initiated_at DESC
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query(this::mapTransfer)
                .list();
    }

    public TransferView transfer(String transferRef) {
        return jdbcClient.sql("""
                        SELECT t.transfer_ref,
                               sender.customer_ref AS sender_customer_ref,
                               sender.display_name AS sender_name,
                               receiver.customer_ref AS receiver_customer_ref,
                               receiver.display_name AS receiver_name,
                               t.amount, t.currency, t.note, t.status, t.failure_code, t.failure_detail,
                               t.initiated_at, t.completed_at
                        FROM p2p_transfer t
                        JOIN wallet sw ON sw.wallet_ref = t.sender_wallet_ref
                        JOIN customer sender ON sender.customer_ref = sw.customer_ref
                        JOIN wallet rw ON rw.wallet_ref = t.receiver_wallet_ref
                        JOIN customer receiver ON receiver.customer_ref = rw.customer_ref
                        WHERE t.transfer_ref = :transferRef
                        """)
                .param("transferRef", transferRef)
                .query(this::mapTransfer)
                .optional()
                .orElseThrow(() -> new DomainException(
                        "TRANSFER_NOT_FOUND", "Transfer " + transferRef + " was not found.", HttpStatus.NOT_FOUND));
    }

    public Map<String, String> balances(String... customerRefs) {
        Map<String, String> balances = new LinkedHashMap<>();
        for (String customerRef : customerRefs) {
            jdbcClient.sql("""
                            SELECT customer_ref, available_balance
                            FROM wallet
                            WHERE customer_ref = :customerRef
                            """)
                    .param("customerRef", customerRef)
                    .query((rs, rowNum) -> Map.entry(
                            rs.getString("customer_ref"), money(rs.getBigDecimal("available_balance"))))
                    .optional()
                    .ifPresent(entry -> balances.put(entry.getKey(), entry.getValue()));
        }
        return balances;
    }

    private UserView mapUser(ResultSet rs, int rowNum) throws SQLException {
        String name = rs.getString("display_name");
        return new UserView(
                rs.getString("customer_ref"),
                name,
                initials(name),
                rs.getString("wallet_ref"),
                rs.getString("currency"),
                money(rs.getBigDecimal("available_balance")),
                money(rs.getBigDecimal("reserved_balance")),
                money(rs.getBigDecimal("daily_outbound_limit")),
                money(rs.getBigDecimal("outbound_today")),
                rs.getString("kyc_status"),
                rs.getString("account_status"),
                rs.getString("wallet_status"),
                rs.getString("risk_tier"));
    }

    private TransferView mapTransfer(ResultSet rs, int rowNum) throws SQLException {
        return new TransferView(
                rs.getString("transfer_ref"),
                rs.getString("sender_customer_ref"),
                rs.getString("sender_name"),
                rs.getString("receiver_customer_ref"),
                rs.getString("receiver_name"),
                money(rs.getBigDecimal("amount")),
                rs.getString("currency"),
                rs.getString("note"),
                rs.getString("status"),
                rs.getString("failure_code"),
                rs.getString("failure_detail"),
                rs.getObject("initiated_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class));
    }

    private String initials(String displayName) {
        return displayName.lines()
                .flatMap(line -> List.of(line.trim().split("\\s+")).stream())
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .reduce("", String::concat);
    }

    private String money(BigDecimal value) {
        return value == null ? null : value.setScale(2).toPlainString();
    }
}

