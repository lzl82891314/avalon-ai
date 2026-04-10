package com.example.avalon.agent.gateway;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AvalonRuntimeCompatibilityResolver {
    private static final Set<String> SYSTEM_INSTRUCTION_PROVIDERS = Set.of("minimax");
    private static final Set<String> REASONING_SPLIT_PROVIDERS = Set.of("minimax");
    private static final Set<String> HIGH_TOKEN_BUDGET_PROVIDERS = Set.of("minimax", "glm", "claude", "qwen");
    private static final Set<String> HIGH_LATENCY_TIMEOUT_PROVIDERS = Set.of("minimax", "glm", "claude", "qwen");
    private static final Set<String> HIGH_COMPRESSION_PROVIDERS = Set.of("minimax", "glm", "claude", "qwen");
    private static final int DEFAULT_BELIEF_MAX_TOKENS = 960;
    private static final int DEFAULT_TOT_MAX_TOKENS = 1280;
    private static final int DEFAULT_CRITIC_MAX_TOKENS = 800;
    private static final int DEFAULT_DECISION_MAX_TOKENS = 640;
    private static final int DEFAULT_STAGE_MAX_ATTEMPTS = 2;

    private AvalonRuntimeCompatibilityResolver() {
    }

    public static AvalonRuntimeCompatibilityProfile resolve(String provider, Map<String, Object> providerOptions) {
        String providerId = providerId(provider);
        Map<String, Object> options = providerOptions == null ? Map.of() : providerOptions;
        Map<String, Object> runtime = mapOption(options.get("avalonRuntime"));
        String instructionRole = blankToNull(stringOption(runtime, "instructionRole"));
        if (instructionRole == null) {
            instructionRole = blankToNull(stringOption(options, "instructionRole"));
        }
        if (instructionRole == null) {
            instructionRole = SYSTEM_INSTRUCTION_PROVIDERS.contains(providerId) ? "system" : "developer";
        }

        Boolean reasoningSplit = booleanOption(runtime, "enableReasoningSplit");
        if (reasoningSplit == null) {
            reasoningSplit = booleanValue(options.get("reasoning_split"));
        }
        if (reasoningSplit == null) {
            reasoningSplit = REASONING_SPLIT_PROVIDERS.contains(providerId);
        }

        Boolean responseFormatJsonObject = booleanOption(runtime, "responseFormatJsonObject");
        if (responseFormatJsonObject == null) {
            responseFormatJsonObject = hasJsonObjectResponseFormat(options);
        }

        Duration defaultTimeout = durationValue(runtime.get("defaultTimeoutMillis"));
        if (defaultTimeout == null) {
            defaultTimeout = durationValue(options.get("timeoutMillis"));
        }
        if (defaultTimeout == null) {
            defaultTimeout = HIGH_LATENCY_TIMEOUT_PROVIDERS.contains(providerId)
                    ? OpenAiCompatibleSupport.HIGH_LATENCY_PROVIDER_TIMEOUT
                    : OpenAiCompatibleSupport.DEFAULT_TIMEOUT;
        }

        Integer minimumCompletionTokens = integerOption(runtime, "minimumCompletionTokens");
        if (minimumCompletionTokens == null) {
            minimumCompletionTokens = HIGH_TOKEN_BUDGET_PROVIDERS.contains(providerId)
                    ? OpenAiCompatibleSupport.MIN_STRUCTURED_OUTPUT_MAX_TOKENS
                    : 0;
        }

        String promptCompressionLevel = blankToNull(stringOption(runtime, "promptCompressionLevel"));
        if (promptCompressionLevel == null) {
            promptCompressionLevel = HIGH_COMPRESSION_PROVIDERS.contains(providerId) ? "high" : "normal";
        }

        Integer totCandidateCount = integerOption(runtime, "totCandidateCount");
        if (totCandidateCount == null) {
            totCandidateCount = 3;
        }

        Boolean admissionEligible = booleanOption(runtime, "admissionEligible");
        if (admissionEligible == null) {
            admissionEligible = !runtime.isEmpty() || "openai".equals(providerId);
        }

        String profileId = blankToNull(stringOption(runtime, "profileId"));
        if (profileId == null) {
            profileId = runtime.isEmpty() ? providerId + "-legacy-default" : providerId + "-avalon-runtime";
        }

        Map<String, Map<String, Object>> configuredStageBudgets = normalizeStageBudgets(runtime.get("stageBudgets"));
        Map<String, AvalonRuntimeStageBudget> stageBudgets = new LinkedHashMap<>();
        stageBudgets.put("belief-stage", buildStageBudget(
                "belief-stage",
                configuredStageBudgets.get("belief-stage"),
                Math.max(minimumCompletionTokens, DEFAULT_BELIEF_MAX_TOKENS),
                defaultTimeout
        ));
        stageBudgets.put("tot-stage", buildStageBudget(
                "tot-stage",
                configuredStageBudgets.get("tot-stage"),
                Math.max(minimumCompletionTokens, DEFAULT_TOT_MAX_TOKENS),
                defaultTimeout
        ));
        stageBudgets.put("critic-stage", buildStageBudget(
                "critic-stage",
                configuredStageBudgets.get("critic-stage"),
                Math.max(minimumCompletionTokens, DEFAULT_CRITIC_MAX_TOKENS),
                defaultTimeout
        ));
        stageBudgets.put("decision-stage", buildStageBudget(
                "decision-stage",
                configuredStageBudgets.get("decision-stage"),
                Math.max(minimumCompletionTokens, DEFAULT_DECISION_MAX_TOKENS),
                defaultTimeout
        ));

        return new AvalonRuntimeCompatibilityProfile(
                profileId,
                instructionRole,
                reasoningSplit,
                Boolean.TRUE.equals(responseFormatJsonObject),
                defaultTimeout,
                minimumCompletionTokens,
                promptCompressionLevel,
                totCandidateCount,
                Boolean.TRUE.equals(admissionEligible),
                stageBudgets
        );
    }

    private static AvalonRuntimeStageBudget buildStageBudget(String stageId,
                                                             Map<String, Object> rawConfig,
                                                             int defaultMaxTokens,
                                                             Duration defaultTimeout) {
        Integer maxTokens = integerOption(rawConfig, "maxTokens");
        if (maxTokens == null) {
            maxTokens = defaultMaxTokens;
        }
        Duration timeout = durationValue(rawConfig == null ? null : rawConfig.get("timeoutMillis"));
        if (timeout == null) {
            timeout = defaultTimeout;
        }
        Integer maxAttempts = integerOption(rawConfig, "maxAttempts");
        if (maxAttempts == null) {
            maxAttempts = DEFAULT_STAGE_MAX_ATTEMPTS;
        }
        return new AvalonRuntimeStageBudget(stageId, maxTokens, timeout, maxAttempts);
    }

    private static Map<String, Map<String, Object>> normalizeStageBudgets(Object rawStageBudgets) {
        Map<String, Map<String, Object>> normalized = new LinkedHashMap<>();
        if (!(rawStageBudgets instanceof Map<?, ?> stageBudgets)) {
            return normalized;
        }
        for (Map.Entry<?, ?> entry : stageBudgets.entrySet()) {
            String stageId = normalizeStageId(entry.getKey());
            if (stageId == null) {
                continue;
            }
            normalized.put(stageId, mapOption(entry.getValue()));
        }
        return normalized;
    }

    private static String normalizeStageId(Object rawStageId) {
        if (rawStageId == null) {
            return null;
        }
        String normalized = String.valueOf(rawStageId).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "belief", "belief-stage", "belief_stage", "beliefstage" -> "belief-stage";
            case "tot", "tot-stage", "tot_stage", "totstage" -> "tot-stage";
            case "critic", "critic-stage", "critic_stage", "criticstage" -> "critic-stage";
            case "decision", "decision-stage", "decision_stage", "decisionstage", "structured-json", "structured_json" -> "decision-stage";
            default -> null;
        };
    }

    private static String providerId(String provider) {
        if (provider == null || provider.isBlank()) {
            return "openai";
        }
        return provider.strip().toLowerCase(Locale.ROOT);
    }

    private static Map<String, Object> mapOption(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private static String stringOption(Map<String, Object> options, String key) {
        if (options == null || key == null) {
            return null;
        }
        Object value = options.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integerOption(Map<String, Object> options, String key) {
        if (options == null || key == null) {
            return null;
        }
        return integerValue(options.get(key));
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    private static Boolean booleanOption(Map<String, Object> options, String key) {
        if (options == null || key == null) {
            return null;
        }
        return booleanValue(options.get(key));
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    private static Duration durationValue(Object value) {
        if (value instanceof Number number) {
            return Duration.ofMillis(number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return Duration.ofMillis(Long.parseLong(text.trim()));
        }
        return null;
    }

    private static boolean hasJsonObjectResponseFormat(Map<String, Object> options) {
        if (options == null) {
            return false;
        }
        Object responseFormat = options.get("response_format");
        if (!(responseFormat instanceof Map<?, ?> formatMap)) {
            return false;
        }
        Object type = formatMap.get("type");
        return type != null && "json_object".equalsIgnoreCase(String.valueOf(type));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
