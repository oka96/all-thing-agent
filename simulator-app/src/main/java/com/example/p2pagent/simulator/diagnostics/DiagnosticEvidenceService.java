package com.example.p2pagent.simulator.diagnostics;

import static com.example.p2pagent.simulator.diagnostics.DiagnosticModels.DiagnosticSnapshot;
import static com.example.p2pagent.simulator.diagnostics.DiagnosticModels.QueryEvidence;

import com.example.p2pagent.simulator.api.DomainException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticEvidenceService {

    public static final String TRANSFER_LIFECYCLE = "TRANSFER_LIFECYCLE";
    public static final String RISK_COMPLIANCE = "RISK_COMPLIANCE";
    public static final String LEDGER_RECONCILIATION = "LEDGER_RECONCILIATION";

    private static final Set<String> ALLOWED_DOMAINS =
            Set.of(TRANSFER_LIFECYCLE, RISK_COMPLIANCE, LEDGER_RECONCILIATION);
    private static final Pattern CUSTOMER_REFERENCE = Pattern.compile("CUS-[A-Z0-9-]+");

    private final JdbcClient jdbcClient;

    public DiagnosticEvidenceService(@Qualifier("diagnosticJdbcClient") JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DiagnosticSnapshot collect(
            Set<String> requestedDomains,
            String requestedCustomerRef,
            String requestedTransferRef,
            String requestedCustomerName,
            Integer requestedRecentLimit) {
        Set<String> domains = normalizeDomains(requestedDomains);
        String customerRef = normalizeReference(requestedCustomerRef);
        String transferRef = normalizeReference(requestedTransferRef);
        String customerName = requestedCustomerName == null || requestedCustomerName.isBlank()
                ? null
                : requestedCustomerName.strip();
        int recentLimit = requestedRecentLimit == null ? 10 : requestedRecentLimit;
        List<QueryEvidence> evidence = new ArrayList<>();
        String resolvedCustomerRef = null;

        if (customerRef == null && customerName != null) {
            QueryExecution resolution = rows("""
                            SELECT customer_ref, display_name
                            FROM DIAG.CUSTOMER_ELIGIBILITY
                            WHERE LOWER(display_name) = LOWER(CAST(:customerName AS VARCHAR))
                            ORDER BY customer_ref
                            """,
                    Map.of("customerName", customerName));
            add(evidence, "DIAG.CUSTOMER_ELIGIBILITY", "Resolve customer reference by complete display name", resolution);
            resolvedCustomerRef = resolveCustomerReference(resolution.rows());
            customerRef = resolvedCustomerRef;
        }

        if (customerRef != null) {
            add(evidence, "DIAG.CUSTOMER_ELIGIBILITY", "Customer eligibility",
                    rows("SELECT * FROM DIAG.CUSTOMER_ELIGIBILITY WHERE customer_ref = :customerRef",
                            Map.of("customerRef", customerRef)));
            add(evidence, "DIAG.WALLET_SNAPSHOT", "Wallet balance and limits",
                    rows("SELECT * FROM DIAG.WALLET_SNAPSHOT WHERE customer_ref = :customerRef",
                            Map.of("customerRef", customerRef)));
            add(evidence, "DIAG.TRANSFER_OVERVIEW", recentLimit + " most recent related transfers",
                    rows("""
                                    SELECT * FROM DIAG.TRANSFER_OVERVIEW
                                    WHERE sender_customer_ref = :customerRef
                                       OR receiver_customer_ref = :customerRef
                                    ORDER BY initiated_at DESC
                                    LIMIT :recentLimit
                                    """,
                            Map.of("customerRef", customerRef, "recentLimit", recentLimit)));
        }

        if (transferRef != null) {
            add(evidence, "DIAG.TRANSFER_OVERVIEW", "Current transfer state",
                    rows("SELECT * FROM DIAG.TRANSFER_OVERVIEW WHERE transfer_ref = :transferRef",
                            Map.of("transferRef", transferRef)));

            if (domains.contains(TRANSFER_LIFECYCLE)) {
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

            if (domains.contains(RISK_COMPLIANCE)) {
                add(evidence, "DIAG.RISK_EVIDENCE", "Risk decisions",
                        rows("""
                                        SELECT * FROM DIAG.RISK_EVIDENCE
                                        WHERE transfer_ref = :transferRef
                                        ORDER BY checked_at
                                        LIMIT 20
                                        """,
                                Map.of("transferRef", transferRef)));
            }

            if (domains.contains(LEDGER_RECONCILIATION)) {
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

        if (evidence.isEmpty() && customerRef == null && transferRef == null) {
            return new DiagnosticSnapshot(List.of(), List.of(), 0, null);
        }

        List<String> inspectedViews = evidence.stream()
                .map(QueryEvidence::view)
                .distinct()
                .toList();
        return new DiagnosticSnapshot(
                List.copyOf(evidence), inspectedViews, evidence.size(), resolvedCustomerRef);
    }

    private String resolveCustomerReference(List<Map<String, Object>> rows) {
        if (rows.size() != 1) {
            return null;
        }
        Object value = rows.getFirst().get("customer_ref");
        if (!(value instanceof String reference)) {
            return null;
        }
        String normalized = normalizeReference(reference);
        return normalized != null && CUSTOMER_REFERENCE.matcher(normalized).matches()
                ? normalized
                : null;
    }

    private Set<String> normalizeDomains(Set<String> requestedDomains) {
        if (requestedDomains == null || requestedDomains.isEmpty()) {
            return Set.of();
        }
        Set<String> domains = requestedDomains.stream()
                .map(domain -> domain == null ? "" : domain.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        Set<String> invalid = domains.stream()
                .filter(domain -> !ALLOWED_DOMAINS.contains(domain))
                .collect(Collectors.toUnmodifiableSet());
        if (!invalid.isEmpty()) {
            throw new DomainException(
                    "INVALID_DIAGNOSTIC_DOMAIN",
                    "Unsupported diagnostic domain: " + String.join(", ", invalid),
                    HttpStatus.BAD_REQUEST);
        }
        return domains;
    }

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return reference.trim().toUpperCase(Locale.ROOT);
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
            String label = metadata.getColumnLabel(index).toLowerCase(Locale.ROOT);
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
}
