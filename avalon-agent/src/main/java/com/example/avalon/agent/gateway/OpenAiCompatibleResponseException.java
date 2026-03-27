package com.example.avalon.agent.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiCompatibleResponseException extends IllegalStateException {
    private final Map<String, Object> diagnostics;

    public OpenAiCompatibleResponseException(String message,
                                             Throwable cause,
                                             String provider,
                                             String modelName,
                                             String finishReason,
                                             OpenAiCompatibleMessageAnalysis analysis) {
        super(message, cause);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", provider);
        payload.put("modelName", modelName);
        payload.put("gatewayType", "openai-compatible");
        putIfNotBlank(payload, "finishReason", finishReason);
        if (analysis != null) {
            payload.putAll(analysis.diagnostics());
        }
        this.diagnostics = Map.copyOf(payload);
    }

    public Map<String, Object> diagnostics() {
        return diagnostics;
    }

    private void putIfNotBlank(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }
}
