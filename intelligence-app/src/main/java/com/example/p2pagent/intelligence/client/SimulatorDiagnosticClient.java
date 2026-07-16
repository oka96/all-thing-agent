package com.example.p2pagent.intelligence.client;

import static com.example.p2pagent.intelligence.skill.AgentSkillCatalog.LEDGER_RECONCILIATION;
import static com.example.p2pagent.intelligence.skill.AgentSkillCatalog.RISK_COMPLIANCE;
import static com.example.p2pagent.intelligence.skill.AgentSkillCatalog.TRANSFER_LIFECYCLE;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public final class SimulatorDiagnosticClient {

    private static final Map<String, DiagnosticDomain> DOMAINS_BY_SKILL = Map.of(
            TRANSFER_LIFECYCLE, DiagnosticDomain.TRANSFER_LIFECYCLE,
            RISK_COMPLIANCE, DiagnosticDomain.RISK_COMPLIANCE,
            LEDGER_RECONCILIATION, DiagnosticDomain.LEDGER_RECONCILIATION);

    private final RestClient restClient;

    public SimulatorDiagnosticClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.simulator.base-url:http://127.0.0.1:8081}") String baseUrl,
            @Value("${app.simulator.token:local-diagnostic-token}") String token,
            @Value("${app.simulator.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.simulator.read-timeout:5s}") Duration readTimeout) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("app.simulator.token must not be blank.");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim())
                .requestFactory(requestFactory)
                .build();
    }

    public DiagnosticSnapshot collect(
            Set<String> selectedSkills,
            String customerRef,
            String customerName,
            String transferRef,
            int recentLimit) {
        Set<DiagnosticDomain> domains = selectedSkills.stream()
                .map(DOMAINS_BY_SKILL::get)
                .filter(domain -> domain != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        DiagnosticSnapshotResponse response;
        try {
            response = restClient.post()
                    .uri("/internal/diagnostics/snapshot")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new DiagnosticSnapshotRequest(
                            domains, customerRef, customerName, transferRef, recentLimit))
                    .retrieve()
                    .body(DiagnosticSnapshotResponse.class);
        }
        catch (RestClientResponseException exception) {
            throw new SimulatorDiagnosticException(
                    "Simulator diagnostics returned HTTP " + exception.getStatusCode().value() + ".", exception);
        }
        catch (RestClientException exception) {
            throw new SimulatorDiagnosticException("Simulator diagnostics are unavailable.", exception);
        }
        return validate(response);
    }

    public void verifyAvailability() {
        DiagnosticSnapshot snapshot = collect(Set.of(), null, null, null, 1);
        if (snapshot.queryCount() != 0
                || !snapshot.evidence().isEmpty()
                || !snapshot.inspectedViews().isEmpty()
                || snapshot.resolvedCustomerRef() != null) {
            throw new SimulatorDiagnosticException(
                    "Simulator connectivity preflight returned unexpected diagnostic evidence.");
        }
    }

    private DiagnosticSnapshot validate(DiagnosticSnapshotResponse response) {
        if (response == null || response.evidence() == null || response.inspectedViews() == null) {
            throw new SimulatorDiagnosticException("Simulator diagnostics returned an incomplete response.");
        }
        if (response.queryCount() < 0 || response.queryCount() != response.evidence().size()) {
            throw new SimulatorDiagnosticException("Simulator diagnostics returned an inconsistent query count.");
        }
        String resolvedCustomerRef = normalizeResolvedCustomerRef(response.resolvedCustomerRef());

        List<QueryEvidence> evidence = new ArrayList<>();
        for (QueryEvidence item : response.evidence()) {
            if (item == null
                    || isBlank(item.view())
                    || isBlank(item.purpose())
                    || isBlank(item.sql())
                    || item.parameters() == null
                    || item.rows() == null) {
                throw new SimulatorDiagnosticException("Simulator diagnostics returned malformed query evidence.");
            }
            if (!item.view().toUpperCase(Locale.ROOT).startsWith("DIAG.")) {
                throw new SimulatorDiagnosticException("Simulator diagnostics returned a non-diagnostic view.");
            }
            if (!item.sql().stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
                throw new SimulatorDiagnosticException("Simulator diagnostics returned a non-read-only query.");
            }

            Map<String, String> parameters =
                    Collections.unmodifiableMap(new LinkedHashMap<>(item.parameters()));
            List<Map<String, Object>> rows = item.rows().stream()
                    .map(row -> {
                        if (row == null) {
                            throw new SimulatorDiagnosticException(
                                    "Simulator diagnostics returned a malformed result row.");
                        }
                        return Collections.unmodifiableMap(new LinkedHashMap<>(row));
                    })
                    .toList();
            evidence.add(new QueryEvidence(item.view(), item.purpose(), item.sql(), parameters, rows));
        }

        List<String> expectedViews = evidence.stream().map(QueryEvidence::view).distinct().toList();
        if (!expectedViews.equals(response.inspectedViews())) {
            throw new SimulatorDiagnosticException("Simulator diagnostics returned inconsistent inspected views.");
        }
        return new DiagnosticSnapshot(
                List.copyOf(evidence),
                List.copyOf(response.inspectedViews()),
                response.queryCount(),
                resolvedCustomerRef);
    }

    private String normalizeResolvedCustomerRef(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || !value.matches("(?i)CUS-[A-Z0-9-]+")) {
            throw new SimulatorDiagnosticException("Simulator returned an invalid resolved customer reference.");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum DiagnosticDomain {
        TRANSFER_LIFECYCLE,
        RISK_COMPLIANCE,
        LEDGER_RECONCILIATION
    }

    private record DiagnosticSnapshotRequest(
            Set<DiagnosticDomain> domains,
            String customerRef,
            String customerName,
            String transferRef,
            int recentLimit) {
    }

    private record DiagnosticSnapshotResponse(
            List<QueryEvidence> evidence,
            List<String> inspectedViews,
            int queryCount,
            String resolvedCustomerRef) {
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
            int queryCount,
            String resolvedCustomerRef) {

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

    public static final class SimulatorDiagnosticException extends RuntimeException {

        public SimulatorDiagnosticException(String message) {
            super(message);
        }

        public SimulatorDiagnosticException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
