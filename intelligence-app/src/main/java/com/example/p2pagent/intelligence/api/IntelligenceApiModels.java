package com.example.p2pagent.intelligence.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class IntelligenceApiModels {

    private IntelligenceApiModels() {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            String customerRef,
            String transferRef) {
    }

    public record ChatResponse(
            String answer,
            List<ReasoningStep> reasoningTrace,
            List<QueryTrace> queries,
            List<String> skills,
            List<String> tables,
            boolean readOnly,
            int queryCount,
            String customerRef,
            String requestId) {
    }

    public record ReasoningStep(String stage, String summary) {
    }

    public record QueryTrace(
            String view,
            String purpose,
            String sql,
            Map<String, String> parameters,
            List<Map<String, Object>> rows) {
    }

    public record ApiError(String code, String message, String requestId) {
    }
}
