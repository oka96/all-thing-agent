package com.example.p2pagent.simulator.diagnostics;

import static com.example.p2pagent.simulator.diagnostics.DiagnosticModels.DiagnosticSnapshot;
import static com.example.p2pagent.simulator.diagnostics.DiagnosticModels.DiagnosticSnapshotRequest;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.p2pagent.simulator.api.DomainException;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/diagnostics")
public class DiagnosticController {

    private final DiagnosticEvidenceService evidenceService;
    private final byte[] expectedAuthorization;

    public DiagnosticController(
            DiagnosticEvidenceService evidenceService,
            @Value("${app.diagnostics.http-token}") String diagnosticToken) {
        if (diagnosticToken == null || diagnosticToken.isBlank()) {
            throw new IllegalArgumentException("app.diagnostics.http-token must not be blank.");
        }
        this.evidenceService = evidenceService;
        this.expectedAuthorization = ("Bearer " + diagnosticToken).getBytes(UTF_8);
    }

    @PostMapping("/snapshot")
    public DiagnosticSnapshot snapshot(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody DiagnosticSnapshotRequest request) {
        authorize(authorization);
        return evidenceService.collect(
                request.domains(),
                request.customerRef(),
                request.transferRef(),
                request.customerName(),
                request.recentLimit());
    }

    private void authorize(String authorization) {
        byte[] actualAuthorization = authorization == null ? new byte[0] : authorization.getBytes(UTF_8);
        if (!MessageDigest.isEqual(expectedAuthorization, actualAuthorization)) {
            throw new DomainException(
                    "DIAGNOSTIC_UNAUTHORIZED",
                    "A valid diagnostic bearer token is required.",
                    HttpStatus.UNAUTHORIZED);
        }
    }
}
