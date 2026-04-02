package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.ModelGateway;
import com.example.avalon.agent.gateway.StructuredModelGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.example.avalon.agent.model.TomBeliefStageResult;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.memory.PlayerBeliefState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TomDeliberationPolicy implements DeliberationPolicy {
    private final StructuredModelGateway structuredModelGateway;
    private final ModelGateway modelGateway;
    private final AgentTurnRequestFactory requestFactory;
    private final TomPromptFactory tomPromptFactory;
    private final ResponseParser responseParser;
    private final ValidationRetryPolicy validationRetryPolicy;
    private final StructuredStageRetryPolicy structuredStageRetryPolicy;
    private final TomPolicySupport support = new TomPolicySupport();

    public TomDeliberationPolicy(StructuredModelGateway structuredModelGateway,
                                 ModelGateway modelGateway,
                                 AgentTurnRequestFactory requestFactory,
                                 TomPromptFactory tomPromptFactory,
                                 ResponseParser responseParser,
                                 ValidationRetryPolicy validationRetryPolicy,
                                 StructuredStageRetryPolicy structuredStageRetryPolicy) {
        this.structuredModelGateway = structuredModelGateway;
        this.modelGateway = modelGateway;
        this.requestFactory = requestFactory;
        this.tomPromptFactory = tomPromptFactory;
        this.responseParser = responseParser;
        this.validationRetryPolicy = validationRetryPolicy;
        this.structuredStageRetryPolicy = structuredStageRetryPolicy;
    }

    @Override
    public String policyId() {
        return AgentPolicyIds.TOM_V1;
    }

    @Override
    public TurnAgentResult execute(PlayerTurnContext context, PlayerAgentConfig config) {
        PlayerAgentConfig effectiveConfig = config == null ? new PlayerAgentConfig() : config;

        AgentTurnRequest beliefRequest = requestFactory.create(context, effectiveConfig, "actor");
        beliefRequest.setPromptText(tomPromptFactory.buildBeliefUserPrompt(beliefRequest));
        var structuredRequest = support.structuredRequest(
                beliefRequest,
                tomPromptFactory.buildBeliefDeveloperPrompt(beliefRequest),
                beliefRequest.getPromptText()
        );

        StructuredStageRetryPolicy.StructuredStageExecution beliefExecution;
        StructuredInferenceResult structuredResult;
        TomBeliefStageResult beliefStageResult;
        Map<String, Object> beliefTrace;
        int beliefAttempts = 0;
        try {
            beliefExecution = structuredStageRetryPolicy.execute("belief-stage", structuredRequest, structuredModelGateway);
            structuredResult = beliefExecution.result();
            beliefAttempts = beliefExecution.attempts();
            beliefStageResult = support.parseBeliefStage(context, structuredResult, policyId());
            beliefTrace = beliefTrace(beliefRequest, structuredResult, beliefStageResult, beliefAttempts);
        } catch (RuntimeException exception) {
            RuntimeException failure = exception instanceof StructuredStageRetryPolicy.StructuredStageExecutionException structuredException
                    ? structuredException.failure()
                    : exception;
            int attempts = exception instanceof StructuredStageRetryPolicy.StructuredStageExecutionException structuredException
                    ? structuredException.attempts()
                    : Math.max(beliefAttempts, 1);
            throw new AgentTurnExecutionException(
                    "tom-v1 belief stage failed",
                    beliefRequest,
                    null,
                    attempts,
                    List.of(failedBeliefTrace(beliefRequest, attempts, failure)),
                    failedPolicySummary(beliefRequest, attempts, 0, "belief-stage", failure),
                    failure
            );
        }

        AgentTurnRequest decisionRequest = requestFactory.create(context, effectiveConfig, "actor");
        decisionRequest.setMemory(support.mergeBeliefIntoMemory(decisionRequest.getMemory(), beliefStageResult));
        decisionRequest.setPromptText(tomPromptFactory.buildDecisionPrompt(decisionRequest, beliefStageResult));

        try {
            ValidatedAgentTurn validated = validationRetryPolicy.execute(context, decisionRequest, modelGateway, responseParser);
            AgentTurnResult enrichedTurnResult = enrichTurnResult(validated.turnResult(), validated.action(), beliefStageResult);
            List<Map<String, Object>> executionTrace = List.of(
                    beliefTrace,
                    decisionTrace(validated.request(), enrichedTurnResult, validated.attempts())
            );
            return new TurnAgentResult(
                    validated.request(),
                    enrichedTurnResult,
                    validated.action(),
                    beliefAttempts + validated.attempts(),
                    policyId(),
                    effectiveConfig.effectiveStrategyProfileId(),
                    executionTrace,
                    successPolicySummary(
                            validated.request(),
                            structuredResult,
                            enrichedTurnResult,
                            beliefStageResult,
                            beliefAttempts,
                            validated.attempts()
                    )
            );
        } catch (AgentTurnExecutionException exception) {
            List<Map<String, Object>> executionTrace = List.of(
                    beliefTrace,
                    failedDecisionTrace(exception.request(), exception.lastTurnResult(), exception.attempts(), exception.getCause())
            );
            throw new AgentTurnExecutionException(
                    exception.getMessage(),
                    exception.request(),
                    exception.lastTurnResult(),
                    beliefAttempts + exception.attempts(),
                    executionTrace,
                    failedPolicySummary(
                            exception.request(),
                            beliefAttempts + exception.attempts(),
                            beliefStageResult.getBeliefsByPlayerId().size(),
                            "decision-stage",
                            exception.getCause()
                    ),
                    exception.getCause()
            );
        }
    }

    private AgentTurnResult enrichTurnResult(AgentTurnResult turnResult,
                                             PlayerAction action,
                                             TomBeliefStageResult beliefStageResult) {
        MemoryUpdate mergedMemoryUpdate = mergeMemoryUpdates(buildBeliefMemoryUpdate(beliefStageResult), turnResult.getMemoryUpdate());
        turnResult.setMemoryUpdate(mergedMemoryUpdate);

        AuditReason auditReason = turnResult.getAuditReason();
        if (auditReason == null) {
            auditReason = new AuditReason();
        }
        if (blankToNull(auditReason.getGoal()) == null) {
            auditReason.setGoal("Generate a legal " + action.actionType().name() + " action under tom-v1 reasoning");
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
        merged.setSuspicionDelta(sumDoubleMap(beliefMemoryUpdate.getSuspicionDelta(), decisionMemoryUpdate.getSuspicionDelta()));
        merged.setTrustDelta(sumDoubleMap(beliefMemoryUpdate.getTrustDelta(), decisionMemoryUpdate.getTrustDelta()));
        merged.setObservationsToAdd(concat(beliefMemoryUpdate.getObservationsToAdd(), decisionMemoryUpdate.getObservationsToAdd()));
        merged.setCommitmentsToAdd(concat(beliefMemoryUpdate.getCommitmentsToAdd(), decisionMemoryUpdate.getCommitmentsToAdd()));
        merged.setInferredFactsToAdd(concat(beliefMemoryUpdate.getInferredFactsToAdd(), decisionMemoryUpdate.getInferredFactsToAdd()));
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

    private Map<String, Object> beliefTrace(AgentTurnRequest beliefRequest,
                                            StructuredInferenceResult structuredResult,
                                            TomBeliefStageResult beliefStageResult,
                                            int attempts) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("stageId", "belief-stage");
        trace.put("mode", "structured-inference");
        trace.put("status", "COMPLETED");
        trace.put("modelSlotId", beliefRequest.getModelSlotId());
        trace.put("provider", beliefRequest.getProvider());
        trace.put("modelId", beliefRequest.getModelId());
        trace.put("modelName", beliefRequest.getModelName());
        trace.put("attempts", attempts);
        trace.put("beliefCount", beliefStageResult.getBeliefsByPlayerId().size());
        if (beliefStageResult.getStrategyMode() != null) {
            trace.put("strategyMode", beliefStageResult.getStrategyMode());
        }
        RawCompletionMetadata metadata = structuredResult.getModelMetadata();
        if (metadata != null) {
            trace.put("inputTokens", metadata.getInputTokens());
            trace.put("outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                trace.put("attributes", metadata.getAttributes());
            }
        }
        return trace;
    }

    private Map<String, Object> failedBeliefTrace(AgentTurnRequest beliefRequest, int attempts, Throwable throwable) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("stageId", "belief-stage");
        trace.put("mode", "structured-inference");
        trace.put("status", "FAILED");
        trace.put("modelSlotId", beliefRequest.getModelSlotId());
        trace.put("provider", beliefRequest.getProvider());
        trace.put("modelId", beliefRequest.getModelId());
        trace.put("modelName", beliefRequest.getModelName());
        trace.put("attempts", attempts);
        if (throwable != null && throwable.getMessage() != null) {
            trace.put("errorMessage", throwable.getMessage());
        }
        return trace;
    }

    private Map<String, Object> decisionTrace(AgentTurnRequest decisionRequest,
                                              AgentTurnResult turnResult,
                                              int attempts) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("stageId", "decision-stage");
        trace.put("mode", "completion");
        trace.put("status", "COMPLETED");
        trace.put("modelSlotId", decisionRequest.getModelSlotId());
        trace.put("provider", decisionRequest.getProvider());
        trace.put("modelId", decisionRequest.getModelId());
        trace.put("modelName", decisionRequest.getModelName());
        trace.put("attempts", attempts);
        trace.put("outputSchemaVersion", decisionRequest.getOutputSchemaVersion());
        RawCompletionMetadata metadata = turnResult == null ? null : turnResult.getModelMetadata();
        if (metadata != null) {
            trace.put("inputTokens", metadata.getInputTokens());
            trace.put("outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                trace.put("attributes", metadata.getAttributes());
            }
        }
        return trace;
    }

    private Map<String, Object> failedDecisionTrace(AgentTurnRequest decisionRequest,
                                                    AgentTurnResult turnResult,
                                                    int attempts,
                                                    Throwable throwable) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("stageId", "decision-stage");
        trace.put("mode", "completion");
        trace.put("status", "FAILED");
        trace.put("modelSlotId", decisionRequest.getModelSlotId());
        trace.put("provider", decisionRequest.getProvider());
        trace.put("modelId", decisionRequest.getModelId());
        trace.put("modelName", decisionRequest.getModelName());
        trace.put("attempts", attempts);
        trace.put("outputSchemaVersion", decisionRequest.getOutputSchemaVersion());
        if (throwable != null && throwable.getMessage() != null) {
            trace.put("errorMessage", throwable.getMessage());
        }
        RawCompletionMetadata metadata = turnResult == null ? null : turnResult.getModelMetadata();
        if (metadata != null) {
            trace.put("inputTokens", metadata.getInputTokens());
            trace.put("outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                trace.put("attributes", metadata.getAttributes());
            }
        }
        return trace;
    }

    private Map<String, Object> successPolicySummary(AgentTurnRequest decisionRequest,
                                                     StructuredInferenceResult structuredResult,
                                                     AgentTurnResult turnResult,
                                                     TomBeliefStageResult beliefStageResult,
                                                     int beliefAttempts,
                                                     int decisionAttempts) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policyId", policyId());
        summary.put("strategyProfileId", decisionRequest.getStrategyProfileId());
        summary.put("status", "COMPLETED");
        summary.put("stageCount", 2);
        summary.put("modelCalls", beliefAttempts + decisionAttempts);
        summary.put("modelSlotIds", List.of(decisionRequest.getModelSlotId()));
        summary.put("beliefCount", beliefStageResult.getBeliefsByPlayerId().size());
        long inputTokens = tokenValue(structuredResult.getModelMetadata()) + tokenValue(turnResult == null ? null : turnResult.getModelMetadata());
        long outputTokens = outputTokenValue(structuredResult.getModelMetadata()) + outputTokenValue(turnResult == null ? null : turnResult.getModelMetadata());
        summary.put("inputTokens", inputTokens);
        summary.put("outputTokens", outputTokens);
        return summary;
    }

    private Map<String, Object> failedPolicySummary(AgentTurnRequest request,
                                                    int totalModelCalls,
                                                    int beliefCount,
                                                    String failedStage,
                                                    Throwable throwable) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("policyId", policyId());
        summary.put("strategyProfileId", request.getStrategyProfileId());
        summary.put("status", "FAILED");
        summary.put("stageCount", 2);
        summary.put("failedStage", failedStage);
        summary.put("modelCalls", totalModelCalls);
        summary.put("modelSlotIds", List.of(request.getModelSlotId()));
        summary.put("beliefCount", beliefCount);
        if (throwable != null && throwable.getMessage() != null) {
            summary.put("errorMessage", throwable.getMessage());
        }
        return summary;
    }

    private long tokenValue(RawCompletionMetadata metadata) {
        return metadata == null || metadata.getInputTokens() == null ? 0L : metadata.getInputTokens();
    }

    private long outputTokenValue(RawCompletionMetadata metadata) {
        return metadata == null || metadata.getOutputTokens() == null ? 0L : metadata.getOutputTokens();
    }

    private double beliefConfidence(TomBeliefStageResult beliefStageResult) {
        return beliefStageResult.getBeliefsByPlayerId().values().stream()
                .mapToDouble(PlayerBeliefState::confidence)
                .average()
                .orElse(0.25);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
