package com.example.p2pagent.simulator.api;

import static com.example.p2pagent.simulator.api.ApiModels.TransferRequest;
import static com.example.p2pagent.simulator.api.ApiModels.TransferResult;
import static com.example.p2pagent.simulator.api.ApiModels.TransfersResponse;
import static com.example.p2pagent.simulator.api.ApiModels.UsersResponse;

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
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulatorQueryService queryService;
    private final P2pTransferService transferService;

    public SimulatorController(SimulatorQueryService queryService, P2pTransferService transferService) {
        this.queryService = queryService;
        this.transferService = transferService;
    }

    @GetMapping("/users")
    public UsersResponse users() {
        return new UsersResponse(queryService.users());
    }

    @GetMapping("/transfers")
    public TransfersResponse transfers(@RequestParam(defaultValue = "12") int limit) {
        return new TransfersResponse(queryService.transfers(limit));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResult transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return transferService.transfer(request, idempotencyKey);
    }
}
