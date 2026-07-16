package com.example.p2pagent.api;

import static com.example.p2pagent.api.ApiModels.SystemStatus;
import static com.example.p2pagent.api.ApiModels.TransferRequest;
import static com.example.p2pagent.api.ApiModels.TransferResult;
import static com.example.p2pagent.api.ApiModels.TransfersResponse;
import static com.example.p2pagent.api.ApiModels.UsersResponse;

import com.example.p2pagent.simulator.P2pTransferService;
import com.example.p2pagent.simulator.SimulatorQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SimulatorController {

    private final SimulatorQueryService queryService;
    private final P2pTransferService transferService;

    public SimulatorController(SimulatorQueryService queryService, P2pTransferService transferService) {
        this.queryService = queryService;
        this.transferService = transferService;
    }

    @GetMapping("/simulator/users")
    public UsersResponse users() {
        return new UsersResponse(queryService.users());
    }

    @GetMapping("/simulator/transfers")
    public TransfersResponse transfers(@RequestParam(defaultValue = "12") int limit) {
        return new TransfersResponse(queryService.transfers(limit));
    }

    @PostMapping("/simulator/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResult transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return transferService.transfer(request, idempotencyKey);
    }

    @GetMapping("/system/status")
    public SystemStatus status() {
        return new SystemStatus(
                "READY_ON_DEMAND",
                "Spring AI + Codex CLI",
                "Local ChatGPT session",
                "read-only / ephemeral",
                true);
    }
}

