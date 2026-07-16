package com.example.p2pagent.ai;

import static com.example.p2pagent.ai.AgentSkillCatalog.CUSTOMER_WALLET;
import static com.example.p2pagent.ai.AgentSkillCatalog.LEDGER_RECONCILIATION;
import static com.example.p2pagent.ai.AgentSkillCatalog.RISK_COMPLIANCE;
import static com.example.p2pagent.ai.AgentSkillCatalog.TRANSFER_LIFECYCLE;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticQueryGateway {

    private final JdbcClient jdbcClient;

    public DiagnosticQueryGateway(@Qualifier("diagnosticJdbcClient") JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DiagnosticSnapshot collect(Set<String> skills, String customerRef, String transferRef) {
        List<QueryEvidence> evidence = new ArrayList<>();

        if (customerRef != null) {
            add(evidence, "DIAG.CUSTOMER_ELIGIBILITY", "Customer eligibility",
                    rows("SELECT * FROM DIAG.CUSTOMER_ELIGIBILITY WHERE customer_ref = :customerRef",
                            Map.of("customerRef", customerRef)));
            add(evidence, "DIAG.WALLET_SNAPSHOT", "Wallet balance and limits",
                    rows("SELECT * FROM DIAG.WALLET_SNAPSHOT WHERE customer_ref = :customerRef",
                            Map.of("customerRef", customerRef)));
            add(evidence, "DIAG.TRANSFER_OVERVIEW", "Ten most recent related transfers",
                    rows("""
                                    SELECT * FROM DIAG.TRANSFER_OVERVIEW
                                    WHERE sender_customer_ref = :customerRef
                                       OR receiver_customer_ref = :customerRef
                                    ORDER BY initiated_at DESC
                                    LIMIT 10
                                    """,
                            Map.of("customerRef", customerRef)));
        }

        if (transferRef != null) {
            add(evidence, "DIAG.TRANSFER_OVERVIEW", "Current transfer state",
                    rows("SELECT * FROM DIAG.TRANSFER_OVERVIEW WHERE transfer_ref = :transferRef",
                            Map.of("transferRef", transferRef)));

            if (skills.contains(TRANSFER_LIFECYCLE)) {
                add(evidence, "DIAG.TRANSFER_TIMELINE", "Ordered transfer events",
                        rows("""
                                        SELECT * FROM DIAG.TRANSFER_TIMELINE
                                        WHERE transfer_ref = :transferRef
                                        ORDER BY sequence_no
                                        LIMIT 30
                                        """,
                                Map.of("transferRef", transferRef)));
                add(evidence, "DIAG.TRANSFER_ATTEMPTS", "Processing stage attempts",
                        rows("""
                                        SELECT * FROM DIAG.TRANSFER_ATTEMPTS
                                        WHERE transfer_ref = :transferRef
                                        ORDER BY started_at
                                        LIMIT 30
                                        """,
                                Map.of("transferRef", transferRef)));
            }

            if (skills.contains(RISK_COMPLIANCE)) {
                add(evidence, "DIAG.RISK_EVIDENCE", "Risk decisions",
                        rows("""
                                        SELECT * FROM DIAG.RISK_EVIDENCE
                                        WHERE transfer_ref = :transferRef
                                        ORDER BY checked_at
                                        LIMIT 20
                                        """,
                                Map.of("transferRef", transferRef)));
            }

            if (skills.contains(LEDGER_RECONCILIATION)) {
                add(evidence, "DIAG.LEDGER_EVIDENCE", "Posted and failed ledger entries",
                        rows("""
                                        SELECT * FROM DIAG.LEDGER_EVIDENCE
                                        WHERE transfer_ref = :transferRef
                                        ORDER BY created_at
                                        LIMIT 30
                                        """,
                                Map.of("transferRef", transferRef)));
                add(evidence, "DIAG.RECONCILIATION_RESULT", "Debit/credit reconciliation",
                        rows("SELECT * FROM DIAG.RECONCILIATION_RESULT WHERE transfer_ref = :transferRef",
                                Map.of("transferRef", transferRef)));
            }
        }

        if (customerRef == null && transferRef == null) {
            return new DiagnosticSnapshot(List.of(), List.of(), 0);
        }

        List<String> inspectedViews = evidence.stream().map(QueryEvidence::view).distinct().toList();
        return new DiagnosticSnapshot(evidence, inspectedViews, evidence.size());
    }

    private QueryExecution rows(String sql, Map<String, ?> parameters) {
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql);
        for (Map.Entry<String, ?> parameter : parameters.entrySet()) {
            statement = statement.param(parameter.getKey(), parameter.getValue());
        }
        Map<String, String> displayedParameters = new LinkedHashMap<>();
        parameters.forEach((name, value) -> displayedParameters.put(name, String.valueOf(value)));
        return new QueryExecution(
                sql.stripIndent().strip(),
                Collections.unmodifiableMap(displayedParameters),
                statement.query(this::mapRow).list());
    }

    private Map<String, Object> mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String label = metadata.getColumnLabel(index).toLowerCase();
            Object value = resultSet.getObject(index);
            row.put(label, value == null ? null : String.valueOf(value));
        }
        return Collections.unmodifiableMap(row);
    }

    private void add(List<QueryEvidence> evidence, String view, String purpose, QueryExecution execution) {
        evidence.add(new QueryEvidence(
                view,
                purpose,
                execution.sql(),
                execution.parameters(),
                execution.rows()));
    }

    private record QueryExecution(
            String sql,
            Map<String, String> parameters,
            List<Map<String, Object>> rows) {
    }

    public record QueryEvidence(
            String view,
            String purpose,
            String sql,
            Map<String, String> parameters,
            List<Map<String, Object>> rows) {
    }

    public record DiagnosticSnapshot(
            List<QueryEvidence> evidence,
            List<String> inspectedViews,
            int queryCount) {

        public String promptText() {
            if (evidence.isEmpty()) {
                return "No diagnostic query ran because no valid CUS-* or P2P-* reference was available.";
            }
            StringBuilder text = new StringBuilder();
            for (QueryEvidence item : evidence) {
                text.append("VIEW: ").append(item.view())
                        .append("\nPURPOSE: ").append(item.purpose())
                        .append("\nROWS (read-only):\n");
                if (item.rows().isEmpty()) {
                    text.append("  <no rows>\n");
                }
                else {
                    item.rows().forEach(row -> text.append("  ").append(row).append("\n"));
                }
                text.append('\n');
            }
            return text.toString();
        }
    }
}
