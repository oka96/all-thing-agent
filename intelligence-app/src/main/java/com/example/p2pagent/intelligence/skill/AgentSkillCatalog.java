package com.example.p2pagent.intelligence.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class AgentSkillCatalog {

    public static final String TRIAGE = "p2p-triage";
    public static final String CUSTOMER_WALLET = "customer-wallet-eligibility";
    public static final String TRANSFER_LIFECYCLE = "transfer-lifecycle";
    public static final String RISK_COMPLIANCE = "risk-compliance";
    public static final String LEDGER_RECONCILIATION = "ledger-reconciliation";

    private final Map<String, Skill> skills;
    private final Map<String, String> documents = new LinkedHashMap<>();

    public AgentSkillCatalog(ResourceLoader resourceLoader) {
        List<Skill> definitions = List.of(
                new Skill(
                        TRIAGE,
                        "Route P2P troubleshooting prompts and extract stable customer or transfer references.",
                        List.of("any troubleshooting question"),
                        List.of(),
                        "classpath:agent-skills/p2p-triage/SKILL.md"),
                new Skill(
                        CUSTOMER_WALLET,
                        "Diagnose account, KYC, wallet, balance, eligibility, and daily-limit issues.",
                        List.of("account", "kyc", "wallet", "balance", "limit", "send", "receive", "frozen"),
                        List.of("DIAG.CUSTOMER_ELIGIBILITY", "DIAG.WALLET_SNAPSHOT"),
                        "classpath:agent-skills/customer-wallet-eligibility/SKILL.md"),
                new Skill(
                        TRANSFER_LIFECYCLE,
                        "Diagnose transfer state, attempts, failures, pending states, and timeouts.",
                        List.of("transfer", "pending", "failed", "status", "timeout", "latest", "last"),
                        List.of("DIAG.TRANSFER_OVERVIEW", "DIAG.TRANSFER_TIMELINE", "DIAG.TRANSFER_ATTEMPTS"),
                        "classpath:agent-skills/transfer-lifecycle/SKILL.md"),
                new Skill(
                        RISK_COMPLIANCE,
                        "Explain risk blocks, reviews, scores, fraud controls, and policy rejection.",
                        List.of("risk", "blocked", "review", "fraud", "compliance", "rejected"),
                        List.of("DIAG.RISK_EVIDENCE", "DIAG.CUSTOMER_ELIGIBILITY"),
                        "classpath:agent-skills/risk-compliance/SKILL.md"),
                new Skill(
                        LEDGER_RECONCILIATION,
                        "Reconcile debits, credits, missing funds, duplicate charges, and reversals.",
                        List.of("debited", "credited", "charged", "missing", "received", "refund", "reversed", "ledger"),
                        List.of("DIAG.LEDGER_EVIDENCE", "DIAG.RECONCILIATION_RESULT", "DIAG.TRANSFER_OVERVIEW"),
                        "classpath:agent-skills/ledger-reconciliation/SKILL.md"));
        this.skills = definitions.stream().collect(Collectors.toMap(
                Skill::id,
                skill -> skill,
                (left, right) -> left,
                LinkedHashMap::new));
        for (Skill skill : definitions) {
            try (var input = resourceLoader.getResource(skill.resource()).getInputStream()) {
                documents.put(skill.id(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            catch (IOException exception) {
                throw new IllegalStateException("Could not load agent skill " + skill.id(), exception);
            }
        }
    }

    public String triageDocument() {
        return documents.get(TRIAGE);
    }

    public String routingCatalog() {
        return skills.values().stream()
                .filter(skill -> !TRIAGE.equals(skill.id()))
                .map(skill -> "- " + skill.id() + ": " + skill.description()
                        + " Signals: " + String.join(", ", skill.signals()))
                .collect(Collectors.joining("\n"));
    }

    public Set<String> validateOrFallback(Set<String> requested, String message) {
        Set<String> valid = requested.stream()
                .filter(id -> skills.containsKey(id) && !TRIAGE.equals(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!valid.isEmpty()) {
            return valid;
        }
        return heuristicRoute(message);
    }

    public Set<String> heuristicRoute(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>();
        skills.values().stream()
                .filter(skill -> !TRIAGE.equals(skill.id()))
                .filter(skill -> skill.signals().stream().anyMatch(normalized::contains))
                .forEach(skill -> selected.add(skill.id()));
        if (selected.isEmpty()) {
            selected.add(TRANSFER_LIFECYCLE);
        }
        return selected;
    }

    public String selectedDocuments(Set<String> selected) {
        return selected.stream()
                .map(id -> documents.getOrDefault(id, ""))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public List<String> tables(Set<String> selected) {
        List<String> tables = new ArrayList<>();
        for (String id : selected) {
            Skill skill = skills.get(id);
            if (skill != null) {
                skill.tables().forEach(table -> {
                    if (!tables.contains(table)) {
                        tables.add(table);
                    }
                });
            }
        }
        return tables;
    }

    public Set<String> allowedDomainSkillIds() {
        return skills.keySet().stream()
                .filter(id -> !TRIAGE.equals(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record Skill(
            String id,
            String description,
            List<String> signals,
            List<String> tables,
            String resource) {
    }
}
