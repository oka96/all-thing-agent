package com.example.p2pagent.api;

import static com.example.p2pagent.api.ApiModels.ChatRequest;
import static com.example.p2pagent.api.ApiModels.ChatResponse;

import com.example.p2pagent.ai.TroubleshootingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/troubleshooting")
public class TroubleshootingController {

    private final TroubleshootingService troubleshootingService;

    public TroubleshootingController(TroubleshootingService troubleshootingService) {
        this.troubleshootingService = troubleshootingService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return troubleshootingService.troubleshoot(request);
    }
}

