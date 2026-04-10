package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.example.avalon.agent.model.TomBeliefStageResult;
import com.example.avalon.agent.model.TomCriticStageResult;
import com.example.avalon.agent.model.TomTotCandidate;
import com.example.avalon.agent.model.TomTotStageResult;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.memory.PlayerBeliefState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TomPolicySupport {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    StructuredInferenceRequest structuredRequest(AgentTurnRequest request,
                                                 String developerPrompt,
                                                 String userPrompt) {
        StructuredInferenceRequest structuredRequest = new StructuredInferenceRequest();
        structuredRequest.setModelSlotId(request.getModelSlotId());
        structuredRequest.setModelId(request.getModelId());
        structuredRequest.setProvider(request.getProvider());
        structuredRequest.setModelName(request.getModelName());
        structuredRequest.setTemperature(request.getTemperature());
        structuredRequest.setMaxTokens(request.getMaxTokens());
        structuredRequest.setProviderOptions(request.getProviderOptions());
        structuredRequest.setDeveloperPrompt(developerPrompt);
        structuredRequest.setUserPrompt(userPrompt);
        return structuredRequest;
    }

    TomBeliefStageResult parseBeliefStage(PlayerTurnContext context,
                                          StructuredInferenceResult structuredResult,
                                          String policyId) {
        try {
            JsonNode payload = structuredResult.getPayload();
            TomBeliefStageResult beliefStageResult = new TomBeliefStageResult();
            beliefStageResult.setBeliefsByPlayerId(parseBeliefsByPlayerId(payload == null ? null : payload.get("beliefsByPlayerId")));
            beliefStageResult.setStrategyMode(textValue(payload == null ? null : payload.get("strategyMode")));
            beliefStageResult.setLastSummary(textValue(payload == null ? null : payload.get("lastSummary")));
            beliefStageResult.setObservationsToAdd(stringList(payload == null ? null : payload.get("observationsToAdd")));
            beliefStageResult.setInferredFactsToAdd(stringList(payload == null ? null : payload.get("inferredFactsToAdd")));
            sanitizeBeliefStage(context, beliefStageResult);
            return beliefStageResult;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse " + policyId + " belief stage payload", exception);
        }
    }

    TomTotStageResult parseTotStage(StructuredInferenceResult structuredResult,
                                    String policyId,
                                    int maxCandidates) {
        try {
            JsonNode payload = structuredResult.getPayload();
            TomTotStageResult totStageResult = new TomTotStageResult();
            totStageResult.setCandidates(parseTotCandidates(payload == null ? null : payload.get("candidates")));
            totStageResult.setSelectedCandidateId(textValue(payload == null ? null : payload.get("selectedCandidateId")));
            totStageResult.setSummary(textValue(payload == null ? null : payload.get("summary")));
            sanitizeTotStage(totStageResult, maxCandidates);
            return totStageResult;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse " + policyId + " tot stage payload", exception);
        }
    }

    TomCriticStageResult parseCriticStage(StructuredInferenceResult structuredResult, String policyId) {
        try {
            JsonNode payload = structuredResult.getPayload();
            TomCriticStageResult criticStageResult = new TomCriticStageResult();
            criticStageResult.setStatus(textValue(payload == null ? null : payload.get("status")));
            criticStageResult.setRiskFindings(stringList(payload == null ? null : payload.get("riskFindings")));
            criticStageResult.setCounterSignals(stringList(payload == null ? null : payload.get("counterSignals")));
            criticStageResult.setRecommendedAdjustments(stringList(payload == null ? null : payload.get("recommendedAdjustments")));
            criticStageResult.setSummary(textValue(payload == null ? null : payload.get("summary")));
            sanitizeCriticStage(criticStageResult);
            return criticStageResult;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse " + policyId + " critic stage payload", exception);
        }
    }

    Map<String, Object> mergeBeliefIntoMemory(Map<String, Object> memory, TomBeliefStageResult beliefStageResult) {
        Map<String, Object> merged = new LinkedHashMap<>(memory == null ? Map.of() : memory);
        merged.put("beliefsByPlayerId", beliefStageResult.getBeliefsByPlayerId());
        if (beliefStageResult.getStrategyMode() != null) {
            merged.put("strategyMode", beliefStageResult.getStrategyMode());
        }
        if (beliefStageResult.getLastSummary() != null) {
            merged.put("lastSummary", beliefStageResult.getLastSummary());
        }
        mergeListField(merged, "observations", beliefStageResult.getObservationsToAdd());
        mergeListField(merged, "inferredFacts", beliefStageResult.getInferredFactsToAdd());
        return merged;
    }

    AgentTurnResult enrichTurnResult(AgentTurnResult turnResult,
                                     PlayerAction action,
                                     TomBeliefStageResult beliefStageResult,
                                     String policyId) {
        MemoryUpdate mergedMemoryUpdate = mergeMemoryUpdates(
                buildBeliefMemoryUpdate(beliefStageResult),
                turnResult.getMemoryUpdate()
        );
        turnResult.setMemoryUpdate(mergedMemoryUpdate);

        AuditReason auditReason = turnResult.getAuditReason();
        if (auditReason == null) {
            auditReason = new AuditReason();
        }
        if (blankToNull(auditReason.getGoal()) == null) {
            auditReason.setGoal("Generate a legal " + action.actionType().name() + " action under " + policyId + " reasoning");
        }
        if ((auditReason.getReasonSummary() == null || auditReason.getReasonSummary().isEmpty())
                && beliefStageResult.getLastSummary() != null) {
            auditReason.setReasonSummary(List.of(beliefStageResult.getLastSummary()));
        }
        if (auditReason.getConfidence() == null) {
            auditReason.setConfidence(beliefConfidence(beliefStageResult));
        }
        Map<String, Object> beliefs = new LinkedHashMap<>(auditReason.getBeliefs());
        if (mergedMemoryUpdate.getStrategyMode() != null) {
            beliefs.put("strategyMode", mergedMemoryUpdate.getStrategyMode());
        }
        beliefs.put("beliefsByPlayerId", beliefStageResult.getBeliefsByPlayerId());
        auditReason.setBeliefs(beliefs);
        turnResult.setAuditReason(auditReason);
        return turnResult;
    }

    Map<String, Object> stageTrace(String stageId,
                                   String mode,
                                   String status,
                                   AgentTurnRequest request,
                                   int attempts,
                                   RawCompletionMetadata metadata,
                                   Map<String, Object> extras) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("stageId", stageId);
        trace.put("mode", mode);
        trace.put("status", status);
        trace.put("modelSlotId", request.getModelSlotId());
        trace.put("provider", request.getProvider());
        trace.put("modelId", request.getModelId());
        trace.put("modelName", request.getModelName());
        trace.put("attempts", attempts);
        if (extras != null) {
            extras.forEach((key, value) -> {
                if (value != null) {
                    trace.put(key, value);
                }
            });
        }
        if (metadata != null) {
            putIfNotNull(trace, "inputTokens", metadata.getInputTokens());
            putIfNotNull(trace, "outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                trace.put("attributes", metadata.getAttributes());
            }
        }
        return trace;
    }

    Map<String, Object> failedStageTrace(String stageId,
                                         String mode,
                                         AgentTurnRequest request,
                                         int attempts,
                                         AgentTurnResult turnResult,
                                         Throwable throwable,
                                         Map<String, Object> extras) {
        Map<String, Object> trace = stageTrace(
                stageId,
                mode,
                "FAILED",
                request,
                attempts,
                turnResult == null ? null : turnResult.getModelMetadata(),
                extras
        );
        if (throwable != null && throwable.getMessage() != null) {
            trace.put("errorMessage", throwable.getMessage());
        }
        return trace;
    }

    Map<String, Object> failedPolicySummary(String policyId,
                                            AgentTurnRequest request,
                                            int stageCount,
                                            int totalModelCalls,
                                            List<String> modelSlotIds,
                                            int beliefCount,
                                            String failedStage,
                                            Throwable throwable) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policyId", policyId);
        summary.put("strategyProfileId", request.getStrategyProfileId());
        summary.put("status", "FAILED");
        summary.put("stageCount", stageCount);
        summary.put("failedStage", failedStage);
        summary.put("modelCalls", totalModelCalls);
        summary.put("modelSlotIds", List.copyOf(modelSlotIds));
        summary.put("beliefCount", beliefCount);
        if (throwable != null && throwable.getMessage() != null) {
            summary.put("errorMessage", throwable.getMessage());
        }
        return summary;
    }

    Map<String, Object> baseSuccessPolicySummary(String policyId,
                                                 AgentTurnRequest request,
                                                 int stageCount,
                                                 int modelCalls,
                                                 List<String> modelSlotIds,
                                                 int beliefCount,
                                                 long inputTokens,
                                                 long outputTokens) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policyId", policyId);
        summary.put("strategyProfileId", request.getStrategyProfileId());
        summary.put("status", "COMPLETED");
        summary.put("stageCount", stageCount);
        summary.put("modelCalls", modelCalls);
        summary.put("modelSlotIds", List.copyOf(modelSlotIds));
        summary.put("beliefCount", beliefCount);
        summary.put("inputTokens", inputTokens);
        summary.put("outputTokens", outputTokens);
        return summary;
    }

    List<String> modelSlotIds(AgentTurnRequest... requests) {
        Set<String> slots = new LinkedHashSet<>();
        if (requests != null) {
            for (AgentTurnRequest request : requests) {
                if (request != null && blankToNull(request.getModelSlotId()) != null) {
                    slots.add(request.getModelSlotId());
                }
            }
        }
        return List.copyOf(slots);
    }

    boolean hasExplicitModelSlot(PlayerAgentConfig config, String slotId) {
        return config != null
                && config.getModelSlots() != null
                && slotId != null
                && config.getModelSlots().containsKey(slotId);
    }

    long tokenValue(RawCompletionMetadata metadata) {
        return metadata == null || metadata.getInputTokens() == null ? 0L : metadata.getInputTokens();
    }

    long outputTokenValue(RawCompletionMetadata metadata) {
        return metadata == null || metadata.getOutputTokens() == null ? 0L : metadata.getOutputTokens();
    }

    private void sanitizeBeliefStage(PlayerTurnContext context, TomBeliefStageResult beliefStageResult) {
        Set<String> validPlayerIds = validOtherPlayerIds(context);
        Map<String, PlayerBeliefState> sanitizedBeliefs = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerBeliefState> entry : beliefStageResult.getBeliefsByPlayerId().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String playerId = entry.getKey().trim();
            if (!validPlayerIds.contains(playerId)) {
                continue;
            }
            sanitizedBeliefs.put(playerId, entry.getValue());
        }
        beliefStageResult.setBeliefsByPlayerId(sanitizedBeliefs);
        beliefStageResult.setStrategyMode(blankToNull(beliefStageResult.getStrategyMode()));
        beliefStageResult.setLastSummary(blankToNull(beliefStageResult.getLastSummary()));
        beliefStageResult.setObservationsToAdd(sanitizeStrings(beliefStageResult.getObservationsToAdd()));
        beliefStageResult.setInferredFactsToAdd(sanitizeStrings(beliefStageResult.getInferredFactsToAdd()));
    }

    private void sanitizeTotStage(TomTotStageResult totStageResult, int maxCandidates) {
        List<TomTotCandidate> sanitizedCandidates = new ArrayList<>();
        int index = 1;
        int candidateLimit = Math.max(1, maxCandidates);
        for (TomTotCandidate candidate : totStageResult.getCandidates()) {
            if (candidate == null) {
                continue;
            }
            String candidateId = blankToNull(candidate.getCandidateId());
            candidate.setCandidateId(candidateId == null ? "C" + index : candidateId);
            candidate.setActionPlanSummary(blankToNull(candidate.getActionPlanSummary()));
            candidate.setProjectedPublicReaction(blankToNull(candidate.getProjectedPublicReaction()));
            candidate.setProjectedVoteOutcome(blankToNull(candidate.getProjectedVoteOutcome()));
            candidate.setProjectedMissionRisk(blankToNull(candidate.getProjectedMissionRisk()));
            candidate.setKeyRisks(sanitizeStrings(candidate.getKeyRisks()));
            if (candidate.getExpectedUtility() != null) {
                candidate.setExpectedUtility(clamp(candidate.getExpectedUtility()));
            }
            sanitizedCandidates.add(candidate);
            if (sanitizedCandidates.size() == candidateLimit) {
                break;
            }
            index++;
        }
        if (sanitizedCandidates.isEmpty()) {
            throw new IllegalStateException("ToT stage returned no valid candidates");
        }
        totStageResult.setCandidates(sanitizedCandidates);
        String selectedCandidateId = blankToNull(totStageResult.getSelectedCandidateId());
        boolean selectedExists = selectedCandidateId != null && sanitizedCandidates.stream()
                .map(TomTotCandidate::getCandidateId)
                .anyMatch(selectedCandidateId::equals);
        totStageResult.setSelectedCandidateId(selectedExists
                ? selectedCandidateId
                : sanitizedCandidates.get(0).getCandidateId());
        totStageResult.setSummary(blankToNull(totStageResult.getSummary()));
    }

    private void sanitizeCriticStage(TomCriticStageResult criticStageResult) {
        criticStageResult.setStatus(blankToNull(criticStageResult.getStatus()));
        criticStageResult.setRiskFindings(sanitizeStrings(criticStageResult.getRiskFindings()));
        criticStageResult.setCounterSignals(sanitizeStrings(criticStageResult.getCounterSignals()));
        criticStageResult.setRecommendedAdjustments(sanitizeStrings(criticStageResult.getRecommendedAdjustments()));
        criticStageResult.setSummary(blankToNull(criticStageResult.getSummary()));
    }

    private Set<String> validOtherPlayerIds(PlayerTurnContext context) {
        Set<String> playerIds = new LinkedHashSet<>();
        context.publicState().players().forEach(player -> {
            if (player.playerId() != null
                    && !player.playerId().isBlank()
                    && !player.playerId().equals(context.playerId())) {
                playerIds.add(player.playerId());
            }
        });
        return playerIds;
    }

    private void mergeListField(Map<String, Object> memory, String fieldName, List<String> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        List<String> values = new ArrayList<>();
        Object existing = memory.get(fieldName);
        if (existing instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    String value = String.valueOf(item);
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
            }
        }
        values.addAll(additions.stream().filter(item -> item != null && !item.isBlank()).toList());
        memory.put(fieldName, List.copyOf(values));
    }

    private MemoryUpdate buildBeliefMemoryUpdate(TomBeliefStageResult beliefStageResult) {
        MemoryUpdate memoryUpdate = new MemoryUpdate();
        memoryUpdate.setBeliefsToUpsert(beliefStageResult.getBeliefsByPlayerId());
        memoryUpdate.setObservationsToAdd(beliefStageResult.getObservationsToAdd());
        memoryUpdate.setInferredFactsToAdd(beliefStageResult.getInferredFactsToAdd());
        memoryUpdate.setStrategyMode(beliefStageResult.getStrategyMode());
        memoryUpdate.setLastSummary(beliefStageResult.getLastSummary());
        return memoryUpdate;
    }

    private MemoryUpdate mergeMemoryUpdates(MemoryUpdate beliefMemoryUpdate, MemoryUpdate decisionMemoryUpdate) {
        if (decisionMemoryUpdate == null) {
            return beliefMemoryUpdate;
        }
        MemoryUpdate merged = new MemoryUpdate();
        merged.setSuspicionDelta(sumDoubleMap(
                beliefMemoryUpdate.getSuspicionDelta(),
                decisionMemoryUpdate.getSuspicionDelta()
        ));
        merged.setTrustDelta(sumDoubleMap(
                beliefMemoryUpdate.getTrustDelta(),
                decisionMemoryUpdate.getTrustDelta()
        ));
        merged.setObservationsToAdd(concat(
                beliefMemoryUpdate.getObservationsToAdd(),
                decisionMemoryUpdate.getObservationsToAdd()
        ));
        merged.setCommitmentsToAdd(concat(
                beliefMemoryUpdate.getCommitmentsToAdd(),
                decisionMemoryUpdate.getCommitmentsToAdd()
        ));
        merged.setInferredFactsToAdd(concat(
                beliefMemoryUpdate.getInferredFactsToAdd(),
                decisionMemoryUpdate.getInferredFactsToAdd()
        ));
        merged.setBeliefsToUpsert(beliefMemoryUpdate.getBeliefsToUpsert());
        merged.setStrategyMode(blankToNull(decisionMemoryUpdate.getStrategyMode()) == null
                ? beliefMemoryUpdate.getStrategyMode()
                : decisionMemoryUpdate.getStrategyMode());
        merged.setLastSummary(blankToNull(decisionMemoryUpdate.getLastSummary()) == null
                ? beliefMemoryUpdate.getLastSummary()
                : decisionMemoryUpdate.getLastSummary());
        return merged;
    }

    private Map<String, Double> sumDoubleMap(Map<String, Double> left, Map<String, Double> right) {
        Map<String, Double> merged = new LinkedHashMap<>();
        if (left != null) {
            left.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.merge(key, value, Double::sum);
                }
            });
        }
        if (right != null) {
            right.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.merge(key, value, Double::sum);
                }
            });
        }
        return merged;
    }

    private List<String> concat(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<>();
        if (left != null) {
            merged.addAll(left.stream().filter(item -> item != null && !item.isBlank()).toList());
        }
        if (right != null) {
            merged.addAll(right.stream().filter(item -> item != null && !item.isBlank()).toList());
        }
        return List.copyOf(merged);
    }

    private List<String> sanitizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::blankToNull)
                .filter(value -> value != null)
                .toList();
    }

    private double beliefConfidence(TomBeliefStageResult beliefStageResult) {
        return beliefStageResult.getBeliefsByPlayerId().values().stream()
                .mapToDouble(PlayerBeliefState::confidence)
                .average()
                .orElse(0.25);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private Map<String, PlayerBeliefState> parseBeliefsByPlayerId(JsonNode node) {
        Map<String, PlayerBeliefState> beliefs = new LinkedHashMap<>();
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return beliefs;
        }
        node.fields().forEachRemaining(entry -> {
            String playerId = blankToNull(entry.getKey());
            JsonNode beliefNode = entry.getValue();
            if (playerId == null || beliefNode == null || !beliefNode.isObject()) {
                return;
            }
            beliefs.put(playerId, new PlayerBeliefState(
                    doubleValue(beliefNode.get("firstOrderEvilScore"), 0.5),
                    doubleValue(beliefNode.get("secondOrderAwarenessScore"), 0.5),
                    doubleValue(beliefNode.get("thirdOrderManipulationRisk"), 0.5),
                    doubleValue(beliefNode.get("confidence"), 0.5)
            ));
        });
        return beliefs;
    }

    private List<TomTotCandidate> parseTotCandidates(JsonNode node) {
        List<TomTotCandidate> candidates = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return candidates;
        }
        if (node.isArray()) {
            for (JsonNode candidateNode : node) {
                TomTotCandidate candidate = parseTotCandidate(candidateNode);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            return candidates;
        }
        TomTotCandidate candidate = parseTotCandidate(node);
        if (candidate != null) {
            candidates.add(candidate);
        }
        return candidates;
    }

    private TomTotCandidate parseTotCandidate(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return null;
        }
        TomTotCandidate candidate = new TomTotCandidate();
        candidate.setCandidateId(textValue(node.get("candidateId")));
        candidate.setActionDraft(mapValue(node.get("actionDraft")));
        candidate.setActionPlanSummary(textValue(node.get("actionPlanSummary")));
        candidate.setProjectedPublicReaction(textValue(node.get("projectedPublicReaction")));
        candidate.setProjectedVoteOutcome(textValue(node.get("projectedVoteOutcome")));
        candidate.setProjectedMissionRisk(textValue(node.get("projectedMissionRisk")));
        candidate.setExpectedUtility(doubleObjectValue(node.get("expectedUtility")));
        candidate.setKeyRisks(stringList(node.get("keyRisks")));
        return candidate;
    }

    private Map<String, Object> mapValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return values;
        }
        if (node.isArray()) {
            node.forEach(item -> {
                String value = textValue(item);
                if (value != null) {
                    values.add(value);
                }
            });
            return values;
        }
        String value = textValue(node);
        if (value != null) {
            values.add(value);
        }
        return values;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return String.valueOf(node.asText());
        }
        return blankToNull(node.toString());
    }

    private double doubleValue(JsonNode node, double defaultValue) {
        Double value = doubleObjectValue(node);
        return value == null ? defaultValue : value;
    }

    private Double doubleObjectValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Double.parseDouble(node.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
