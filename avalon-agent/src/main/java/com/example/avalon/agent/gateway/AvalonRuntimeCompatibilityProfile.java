package com.example.avalon.agent.gateway;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record AvalonRuntimeCompatibilityProfile(
        String profileId,
        String instructionRole,
        boolean reasoningSplit,
        boolean responseFormatJsonObject,
        Duration defaultTimeout,
        int minimumCompletionTokens,
        String promptCompressionLevel,
        int totCandidateCount,
        boolean admissionEligible,
        Map<String, AvalonRuntimeStageBudget> stageBudgets
) {
    public AvalonRuntimeCompatibilityProfile {
        profileId = blankToFallback(profileId, "legacy-default");
        instructionRole = blankToFallback(instructionRole, "developer");
        defaultTimeout = defaultTimeout == null ? OpenAiCompatibleSupport.DEFAULT_TIMEOUT : defaultTimeout;
        minimumCompletionTokens = Math.max(0, minimumCompletionTokens);
        promptCompressionLevel = normalizeCompression(promptCompressionLevel);
        totCandidateCount = Math.max(1, totCandidateCount);
        stageBudgets = stageBudgets == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stageBudgets));
    }

    public AvalonRuntimeStageBudget stageBudget(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            return null;
        }
        return stageBudgets.get(stageId.trim().toLowerCase(Locale.ROOT));
    }

    public boolean highCompression() {
        return "high".equals(promptCompressionLevel);
    }

    private static String normalizeCompression(String value) {
        String normalized = blankToFallback(value, "normal").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "compact", "compressed", "high" -> "high";
            case "medium" -> "medium";
            default -> "normal";
        };
    }

    private static String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
