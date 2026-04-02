package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;

final class ResponseRetrySupport {
    static final int CORRECTIVE_TOKEN_INCREMENT = 320;

    private ResponseRetrySupport() {
    }

    static boolean isTransportFailure(OpenAiCompatibleResponseException responseException) {
        return "transport".equalsIgnoreCase(stringValue(responseException.diagnostics().get("failureDomain")));
    }

    static boolean requiresCompressionRetry(OpenAiCompatibleResponseException responseException) {
        return requiresCompressionRetry(
                stringValue(responseException.diagnostics().get("finishReason")),
                stringValue(responseException.diagnostics().get("assistantContentShape")),
                responseException.getMessage() == null ? "" : responseException.getMessage()
        );
    }

    static boolean requiresCompressionRetry(String finishReason, String contentShape, String message) {
        if ("length".equalsIgnoreCase(finishReason)) {
            return true;
        }
        if (contentShape == null || contentShape.isBlank()) {
            return message.contains("did not include an action object");
        }
        return switch (contentShape) {
            case "truncated_json_candidate",
                 "plain_text",
                 "markdown_explanation",
                 "reasoning_only",
                 "missing_content" -> true;
            default -> message.contains("did not include an action object");
        };
    }

    static int raisedMaxTokens(Integer currentMaxTokens, int minimumCorrectiveMaxTokens) {
        int current = currentMaxTokens == null ? 0 : currentMaxTokens;
        return Math.max(current, minimumCorrectiveMaxTokens) + CORRECTIVE_TOKEN_INCREMENT;
    }

    static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }
}
