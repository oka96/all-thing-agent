package com.example.p2pagent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ApiModels {

    private ApiModels() {
    }

    public record UserView(
            String customerRef,
            String displayName,
            String initials,
            String walletRef,
            String currency,
            String availableBalance,
            String reservedBalance,
            String dailyOutboundLimit,
            String outboundToday,
            String kycStatus,
            String accountStatus,
            String walletStatus,
            String riskTier) {
    }

    public record UsersResponse(List<UserView> users) {
    }

    public record TransferView(
            String transferRef,
            String senderCustomerRef,
            String senderName,
            String receiverCustomerRef,
            String receiverName,
            String amount,
            String currency,
            String note,
            String status,
            String failureCode,
            String failureDetail,
            OffsetDateTime initiatedAt,
            OffsetDateTime completedAt) {
    }

    public record TransfersResponse(List<TransferView> transfers) {
    }

    public record TransferRequest(
            @NotBlank String senderCustomerRef,
            @NotBlank String receiverCustomerRef,
            @NotBlank String amount,
            @Size(max = 140) String note) {
    }

    public record TransferResult(TransferView transfer, Map<String, String> balances) {
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

    public record SystemStatus(
            String status,
            String intelligence,
            String authentication,
            String sandbox,
            boolean databaseReadOnly) {
    }

    public record ApiError(String code, String message, String requestId) {
    }
}
