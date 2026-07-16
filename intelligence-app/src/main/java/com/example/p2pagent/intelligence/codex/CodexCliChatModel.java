package com.example.p2pagent.intelligence.codex;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class CodexCliChatModel implements ChatModel {

    @Value("${app.codex.preflight-timeout:10s}")
    private Duration preflightTimeout;

    private static final Set<String> SAFE_ENVIRONMENT_KEYS = Set.of(
            "PATH", "HOME", "CODEX_HOME", "USER", "LOGNAME", "SHELL", "TMPDIR",
            "LANG", "LC_ALL", "TERM", "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY",
            "SSL_CERT_FILE", "NODE_EXTRA_CA_CERTS");

    private final String executable;
    private final Duration timeout;
    private final Path workspace;
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CodexCliChatModel(
            @Value("${app.codex.executable:codex}") String executable,
            @Value("${app.codex.timeout:120s}") Duration timeout,
            @Value("${app.codex.workspace:${java.io.tmpdir}/p2p-codex-runtime}") String workspace) {
        this.executable = executable;
        this.timeout = timeout;
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String renderedPrompt = prompt.getInstructions().stream()
                .map(message -> "[" + message.getMessageType().name() + "]\n" + message.getText())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new CodexCliException("Spring AI supplied an empty prompt."));
        String response = execute(renderedPrompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
    }

    public void verifyAvailability() {
        try {
            Files.createDirectories(workspace);
            ProcessBuilder processBuilder = new ProcessBuilder(executable, "login", "status")
                    .directory(workspace.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = processBuilder.start();
            try {
                if (!process.waitFor(preflightTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    throw new CodexCliException("Codex authentication preflight timed out.");
                }
                if (process.exitValue() != 0) {
                    throw new CodexCliException(
                            "Codex authentication preflight failed. Run `codex login` before starting the app.");
                }
            }
            catch (InterruptedException exception) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new CodexCliException("Codex authentication preflight was interrupted.", exception);
            }
        }
        catch (IOException exception) {
            throw new CodexCliException(
                    "Codex executable is unavailable. Check CODEX_EXECUTABLE and the process PATH.", exception);
        }
    }

    private String execute(String prompt) {
        Path outputFile = null;
        try {
            Files.createDirectories(workspace);
            outputFile = workspace.resolve("response-" + UUID.randomUUID() + ".txt");
            List<String> command = new ArrayList<>(List.of(
                    executable,
                    "exec",
                    "-C", workspace.toString(),
                    "--skip-git-repo-check",
                    "--ephemeral",
                    "--sandbox", "read-only",
                    "--color", "never",
                    "--output-last-message", outputFile.toString(),
                    "--json",
                    "-"));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workspace.toFile());
            processBuilder.redirectErrorStream(true);
            scrubEnvironment(processBuilder.environment());

            Process process = processBuilder.start();
            Future<String> telemetry = ioExecutor.submit(() ->
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            try (var stdin = process.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
                throw new CodexCliException("Codex CLI timed out after " + timeout + ".");
            }

            String processOutput = telemetry.get(2, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new CodexCliException(
                        "Codex CLI exited with code " + process.exitValue() + ": " + tail(processOutput));
            }
            if (!Files.exists(outputFile)) {
                throw new CodexCliException("Codex CLI did not write a final response.");
            }
            String response = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
            if (response.isBlank()) {
                throw new CodexCliException("Codex CLI returned an empty response.");
            }
            return response;
        }
        catch (CodexCliException exception) {
            throw exception;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodexCliException("Codex CLI execution was interrupted.", exception);
        }
        catch (Exception exception) {
            throw new CodexCliException(
                    "Codex CLI could not be executed. Confirm `codex login status` for the Spring Boot user.",
                    exception);
        }
        finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                }
                catch (Exception ignored) {
                    // The ephemeral workspace can be cleaned independently if deletion is temporarily unavailable.
                }
            }
        }
    }

    private void scrubEnvironment(Map<String, String> environment) {
        Map<String, String> inherited = Map.copyOf(environment);
        environment.clear();
        inherited.forEach((key, value) -> {
            if (SAFE_ENVIRONMENT_KEYS.contains(key.toUpperCase(Locale.ROOT))) {
                environment.put(key, value);
            }
        });
    }

    private String tail(String value) {
        int limit = 1200;
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }

    @PreDestroy
    void close() {
        ioExecutor.close();
    }

    public static final class CodexCliException extends RuntimeException {

        public CodexCliException(String message) {
            super(message);
        }

        public CodexCliException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
