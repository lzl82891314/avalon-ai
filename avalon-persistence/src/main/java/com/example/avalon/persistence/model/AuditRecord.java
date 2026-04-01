package com.example.avalon.persistence.model;

import java.time.Instant;

public record AuditRecord(
        String auditId,
        String gameId,
        Long eventSeqNo,
        String playerId,
        String visibility,
        String inputContextJson,
        String inputContextHash,
        String rawModelResponseJson,
        String parsedActionJson,
        String auditReasonJson,
        String executionTraceJson,
        String policySummaryJson,
        String validationResultJson,
        String errorMessage,
        Instant createdAt
) {
    public AuditRecord {
        inputContextJson = inputContextJson == null ? "{}" : inputContextJson;
        rawModelResponseJson = rawModelResponseJson == null ? "{}" : rawModelResponseJson;
        parsedActionJson = parsedActionJson == null ? "{}" : parsedActionJson;
        auditReasonJson = auditReasonJson == null ? "{}" : auditReasonJson;
        executionTraceJson = executionTraceJson == null ? "[]" : executionTraceJson;
        policySummaryJson = policySummaryJson == null ? "{}" : policySummaryJson;
        validationResultJson = validationResultJson == null ? "{}" : validationResultJson;
    }
}
