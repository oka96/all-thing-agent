package com.example.p2pagent.simulator.diagnostics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiagnosticModels {

    private DiagnosticModels() {
    }

    public record DiagnosticSnapshotRequest(
            @Size(max = 24) String customerRef,
            @Size(max = 32) String transferRef,
            @Size(max = 3) Set<String> domains,
            @Size(max = 200) String customerName,
            @Min(1) @Max(20) Integer recentLimit) {

        public DiagnosticSnapshotRequest {
            customerName = customerName == null || customerName.isBlank()
                    ? null
                    : customerName.strip();
        }
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
    }
}
