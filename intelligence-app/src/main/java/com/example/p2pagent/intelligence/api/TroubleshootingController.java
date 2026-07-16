package com.example.p2pagent.intelligence.api;

import static com.example.p2pagent.intelligence.api.IntelligenceApiModels.ChatRequest;
import static com.example.p2pagent.intelligence.api.IntelligenceApiModels.ChatResponse;

import com.example.p2pagent.intelligence.service.TroubleshootingService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TroubleshootingController {

    private final TroubleshootingService troubleshootingService;

    public TroubleshootingController(TroubleshootingService troubleshootingService) {
        this.troubleshootingService = troubleshootingService;
    }

    @GetMapping("/system/status")
    public Map<String, String> status() {
        return Map.of("intelligence", "Spring AI + Codex CLI");
    }

    @PostMapping("/troubleshooting/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return troubleshootingService.troubleshoot(request);
    }
}
