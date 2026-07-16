package com.example.p2pagent.intelligence.config;

import com.example.p2pagent.intelligence.client.SimulatorDiagnosticClient;
import com.example.p2pagent.intelligence.codex.CodexCliChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public final class StartupWarmup implements ApplicationRunner {

    private static final String MODEL_PROBE =
            "Startup readiness probe. Reply exactly READY without inspecting files, running commands, or using tools.";

    private final CodexCliChatModel codexChatModel;
    private final SimulatorDiagnosticClient simulatorDiagnosticClient;
    private final boolean modelWarmupEnabled;

    public StartupWarmup(
            CodexCliChatModel codexChatModel,
            SimulatorDiagnosticClient simulatorDiagnosticClient,
            @Value("${app.warmup.model-enabled:true}") boolean modelWarmupEnabled) {
        this.codexChatModel = codexChatModel;
        this.simulatorDiagnosticClient = simulatorDiagnosticClient;
        this.modelWarmupEnabled = modelWarmupEnabled;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        codexChatModel.verifyAvailability();
        simulatorDiagnosticClient.verifyAvailability();
        if (modelWarmupEnabled) {
            codexChatModel.call(new Prompt(MODEL_PROBE));
        }
    }
}
