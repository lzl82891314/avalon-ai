package com.example.avalon.runtime.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuntimeAuditEntry(
        String auditId,
        long eventSeqNo,
        String playerId,
        String visibility,
        Map<String, Object> inputContext,
        Map<String, Object> rawModelResponse,
        Map<String, Object> parsedAction,
        Map<String, Object> auditReason,
        List<Map<String, Object>> executionTrace,
        Map<String, Object> policySummary,
        Map<String, Object> validationResult,
        String errorMessage,
        Instant createdAt
) {
    public RuntimeAuditEntry {
        auditId = auditId == null || auditId.isBlank() ? java.util.UUID.randomUUID().toString() : auditId;
        inputContext = copy(inputContext);
        rawModelResponse = copy(rawModelResponse);
        parsedAction = copy(parsedAction);
        auditReason = copy(auditReason);
        executionTrace = executionTrace == null ? List.of() : executionTrace.stream().map(RuntimeAuditEntry::copy).toList();
        policySummary = copy(policySummary);
        validationResult = copy(validationResult);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
