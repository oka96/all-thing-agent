package com.example.p2pagent.intelligence.api;

import static com.example.p2pagent.intelligence.api.IntelligenceApiModels.ApiError;

import com.example.p2pagent.intelligence.client.SimulatorDiagnosticClient.SimulatorDiagnosticException;
import com.example.p2pagent.intelligence.codex.CodexCliChatModel.CodexCliException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", message, requestId()));
    }

    @ExceptionHandler(SimulatorDiagnosticException.class)
    public ResponseEntity<ApiError> handleSimulator(SimulatorDiagnosticException exception) {
        logger.warn("Simulator diagnostic request failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError(
                        "SIMULATOR_DIAGNOSTICS_UNAVAILABLE",
                        "Read-only simulator diagnostics are unavailable; troubleshooting was stopped.",
                        requestId()));
    }

    @ExceptionHandler(CodexCliException.class)
    public ResponseEntity<ApiError> handleCodex(CodexCliException exception) {
        logger.warn("Codex CLI request failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(
                        "CODEX_UNAVAILABLE",
                        "Codex CLI is unavailable; confirm the local ChatGPT login and try again.",
                        requestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        logger.error("Unexpected intelligence request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        "INTERNAL_ERROR",
                        "The intelligence request could not be completed.",
                        requestId()));
    }

    private String requestId() {
        return "req_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
