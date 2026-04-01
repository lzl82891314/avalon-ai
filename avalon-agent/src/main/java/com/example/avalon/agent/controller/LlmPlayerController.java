package com.example.avalon.agent.controller;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.agent.service.AgentTurnExecutionException;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.DefaultTurnAgent;
import com.example.avalon.agent.service.DeliberationPolicyRegistry;
import com.example.avalon.agent.service.LegacySingleShotDeliberationPolicy;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.TurnAgent;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.controller.PlayerActionGenerationException;
import com.example.avalon.core.player.controller.PlayerController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LlmPlayerController implements PlayerController {
    private static final String OPTIONAL_SECTION_WARNINGS = "optionalSectionWarnings";
    private static final List<String> RAW_METADATA_ATTRIBUTE_KEYS = List.of(
            "assistantContentShape",
            "assistantContentPreview",
            "reasoningDetailsPreview",
            "contentPresent",
            "reasoningDetailsPresent",
            "finishReason",
            "gatewayType"
    );

    private final AgentTurnRequestFactory requestFactory;
    private final PlayerAgentConfig playerAgentConfig;
    private final TurnAgent turnAgent;

    public LlmPlayerController(AgentGateway agentGateway,
                               AgentTurnRequestFactory requestFactory,
                               PromptBuilder promptBuilder,
                               ResponseParser responseParser,
                               ValidationRetryPolicy validationRetryPolicy,
                               PlayerAgentConfig playerAgentConfig) {
        this(
                new DefaultTurnAgent(
                        new DeliberationPolicyRegistry(List.of(
                                new LegacySingleShotDeliberationPolicy(
                                        agentGateway,
                                        requestFactory,
                                        promptBuilder,
                                        responseParser,
                                        validationRetryPolicy
                                )
                        ))
                ),
                requestFactory,
                promptBuilder,
                responseParser,
                validationRetryPolicy,
                playerAgentConfig
        );
    }

    public LlmPlayerController(TurnAgent turnAgent,
                               AgentTurnRequestFactory requestFactory,
                               PromptBuilder promptBuilder,
                               ResponseParser responseParser,
                               ValidationRetryPolicy validationRetryPolicy,
                               PlayerAgentConfig playerAgentConfig) {
        this.turnAgent = turnAgent;
        this.requestFactory = requestFactory;
        this.playerAgentConfig = playerAgentConfig == null ? new PlayerAgentConfig() : playerAgentConfig;
    }

    @Override
    public PlayerActionResult act(PlayerTurnContext context) {
        try {
            TurnAgentResult execution = turnAgent.execute(context, playerAgentConfig);
            AgentTurnResult turnResult = execution.turnResult();
            return new PlayerActionResult(
                    turnResult.getPublicSpeech(),
                    execution.action(),
                    toCoreAuditReason(turnResult),
                    toCoreMemoryUpdate(turnResult),
                    rawMetadata(execution)
            );
        } catch (AgentTurnExecutionException exception) {
            throw new PlayerActionGenerationException(
                    exception.getMessage(),
                    failureMetadata(
                            exception.request(),
                            exception.lastTurnResult(),
                            exception.attempts(),
                            exception.executionTrace(),
                            exception.policySummary(),
                            exception.getCause()
                    ),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new PlayerActionGenerationException(
                    exception.getMessage() == null ? "LLM turn failed" : exception.getMessage(),
                    failureMetadata(requestFactory.create(context, playerAgentConfig), null, 0, List.of(), Map.of(), exception),
                    exception
            );
        }
    }

    private com.example.avalon.core.player.memory.AuditReason toCoreAuditReason(AgentTurnResult turnResult) {
        if (turnResult.getAuditReason() == null) {
            return null;
        }
        return new com.example.avalon.core.player.memory.AuditReason(
                turnResult.getAuditReason().getGoal(),
                turnResult.getAuditReason().getReasonSummary() == null ? List.of() : turnResult.getAuditReason().getReasonSummary(),
                turnResult.getAuditReason().getConfidence(),
                turnResult.getAuditReason().getBeliefs()
        );
    }

    private com.example.avalon.core.player.memory.MemoryUpdate toCoreMemoryUpdate(AgentTurnResult turnResult) {
        if (turnResult.getMemoryUpdate() == null) {
            return null;
        }
        return new com.example.avalon.core.player.memory.MemoryUpdate(
                turnResult.getMemoryUpdate().getSuspicionDelta(),
                turnResult.getMemoryUpdate().getTrustDelta(),
                turnResult.getMemoryUpdate().getObservationsToAdd(),
                turnResult.getMemoryUpdate().getCommitmentsToAdd(),
                turnResult.getMemoryUpdate().getInferredFactsToAdd(),
                turnResult.getMemoryUpdate().getBeliefsToUpsert(),
                turnResult.getMemoryUpdate().getStrategyMode(),
                turnResult.getMemoryUpdate().getLastSummary()
        );
    }

    private Map<String, Object> rawMetadata(TurnAgentResult execution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        AgentTurnResult turnResult = execution.turnResult();
        com.example.avalon.agent.model.AgentTurnRequest request = execution.request();
        RawCompletionMetadata metadata = turnResult.getModelMetadata();
        if (metadata != null) {
            putIfNotNull(payload, "provider", metadata.getProvider());
            putIfNotNull(payload, "modelName", metadata.getModelName());
            putIfNotNull(payload, "inputTokens", metadata.getInputTokens());
            putIfNotNull(payload, "outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                payload.put("attributes", metadata.getAttributes());
            }
        }
        payload.put("attempts", execution.attempts());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        payload.put("inputContext", inputContext(request));
        payload.put("rawModelResponse", rawModelResponse(turnResult));
        payload.put("validation", validationSummary(turnResult, request, execution.attempts()));
        payload.put("policyId", execution.policyId());
        putIfNotNull(payload, "strategyProfileId", execution.strategyProfileId());
        if (!execution.executionTrace().isEmpty()) {
            payload.put("executionTrace", execution.executionTrace());
        }
        if (!execution.policySummary().isEmpty()) {
            payload.put("policySummary", execution.policySummary());
        }
        return payload;
    }

    private Map<String, Object> inputContext(com.example.avalon.agent.model.AgentTurnRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gameId", request.getGameId());
        payload.put("roundNo", request.getRoundNo());
        payload.put("phase", request.getPhase());
        payload.put("playerId", request.getPlayerId());
        payload.put("seatNo", request.getSeatNo());
        payload.put("roleId", request.getRoleId());
        payload.put("agentPolicyId", request.getAgentPolicyId());
        payload.put("strategyProfileId", request.getStrategyProfileId());
        payload.put("modelSlotId", request.getModelSlotId());
        payload.put("modelId", request.getModelId());
        payload.put("provider", request.getProvider());
        payload.put("modelName", request.getModelName());
        payload.put("temperature", request.getTemperature());
        payload.put("maxTokens", request.getMaxTokens());
        payload.put("allowedActions", request.getAllowedActions());
        payload.put("rulesSummary", request.getRulesSummary());
        payload.put("privateKnowledge", request.getPrivateKnowledge());
        payload.put("publicState", request.getPublicState());
        payload.put("memory", request.getMemory());
        payload.put("promptText", request.getPromptText());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        return payload;
    }

    private Map<String, Object> rawModelResponse(AgentTurnResult turnResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "publicSpeech", turnResult.getPublicSpeech());
        putIfNotNull(payload, "privateThought", turnResult.getPrivateThought());
        putIfNotNull(payload, "actionJson", turnResult.getActionJson());
        if (turnResult.getAuditReason() != null) {
            payload.put("auditReason", turnResult.getAuditReason());
        }
        if (turnResult.getMemoryUpdate() != null) {
            payload.put("memoryUpdate", turnResult.getMemoryUpdate());
        }
        if (turnResult.getModelMetadata() != null) {
            payload.put("modelMetadata", turnResult.getModelMetadata());
            if (turnResult.getModelMetadata().getAttributes() != null) {
                copyDiagnosticAttributes(payload, turnResult.getModelMetadata().getAttributes());
            }
        }
        return payload;
    }

    private Map<String, Object> validationSummary(AgentTurnResult turnResult,
                                                  com.example.avalon.agent.model.AgentTurnRequest request,
                                                  int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", true);
        payload.put("attempts", attempts);
        payload.put("allowedActions", request.getAllowedActions());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        putIfNotNull(payload, "policyId", request.getAgentPolicyId());
        if (turnResult != null
                && turnResult.getModelMetadata() != null
                && turnResult.getModelMetadata().getAttributes() != null) {
            Object warnings = turnResult.getModelMetadata().getAttributes().get(OPTIONAL_SECTION_WARNINGS);
            if (warnings != null) {
                payload.put(OPTIONAL_SECTION_WARNINGS, warnings);
            }
        }
        return payload;
    }

    private Map<String, Object> failureMetadata(com.example.avalon.agent.model.AgentTurnRequest request,
                                                AgentTurnResult turnResult,
                                                int attempts,
                                                List<Map<String, Object>> executionTrace,
                                                Map<String, Object> policySummary,
                                                Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attempts", attempts);
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        payload.put("inputContext", inputContext(request));
        payload.put("rawModelResponse", failureRawModelResponse(turnResult, throwable));
        payload.put("validation", failedValidationSummary(request, attempts, throwable));
        putIfNotNull(payload, "policyId", request.getAgentPolicyId());
        putIfNotNull(payload, "strategyProfileId", request.getStrategyProfileId());
        List<Map<String, Object>> effectiveExecutionTrace = executionTrace == null || executionTrace.isEmpty()
                ? failureExecutionTrace(request, turnResult, attempts, throwable)
                : executionTrace;
        if (!effectiveExecutionTrace.isEmpty()) {
            payload.put("executionTrace", effectiveExecutionTrace);
        }
        Map<String, Object> effectivePolicySummary = policySummary == null || policySummary.isEmpty()
                ? failurePolicySummary(request, turnResult, attempts, throwable)
                : policySummary;
        if (!effectivePolicySummary.isEmpty()) {
            payload.put("policySummary", effectivePolicySummary);
        }
        payload.put("auditVisibility", "ADMIN_ONLY");
        putIfNotNull(payload, "errorMessage", throwable == null ? null : throwable.getMessage());
        return payload;
    }

    private Map<String, Object> failedValidationSummary(com.example.avalon.agent.model.AgentTurnRequest request,
                                                        int attempts,
                                                        Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", false);
        payload.put("attempts", attempts);
        payload.put("allowedActions", request.getAllowedActions());
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        putIfNotNull(payload, "policyId", request.getAgentPolicyId());
        putIfNotNull(payload, "errorMessage", throwable == null ? null : throwable.getMessage());
        if (throwable instanceof OpenAiCompatibleResponseException responseException) {
            copyDiagnosticAttributes(payload, responseException.diagnostics());
        }
        return payload;
    }

    private Map<String, Object> failureRawModelResponse(AgentTurnResult turnResult, Throwable throwable) {
        if (turnResult != null) {
            return rawModelResponse(turnResult);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (throwable instanceof OpenAiCompatibleResponseException responseException) {
            payload.putAll(responseException.diagnostics());
        }
        return payload;
    }

    private List<Map<String, Object>> failureExecutionTrace(com.example.avalon.agent.model.AgentTurnRequest request,
                                                            AgentTurnResult turnResult,
                                                            int attempts,
                                                            Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stageId", "single-shot-failed");
        payload.put("mode", "completion");
        payload.put("status", "FAILED");
        payload.put("modelSlotId", request.getModelSlotId());
        payload.put("provider", request.getProvider());
        payload.put("modelId", request.getModelId());
        payload.put("modelName", request.getModelName());
        payload.put("attempts", attempts);
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        payload.put("allowedActions", request.getAllowedActions());
        putIfNotNull(payload, "errorMessage", throwable == null ? null : throwable.getMessage());
        RawCompletionMetadata metadata = turnResult == null ? null : turnResult.getModelMetadata();
        if (metadata != null) {
            putIfNotNull(payload, "inputTokens", metadata.getInputTokens());
            putIfNotNull(payload, "outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                payload.put("attributes", metadata.getAttributes());
            }
        }
        return List.of(payload);
    }

    private Map<String, Object> failurePolicySummary(com.example.avalon.agent.model.AgentTurnRequest request,
                                                     AgentTurnResult turnResult,
                                                     int attempts,
                                                     Throwable throwable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "policyId", request.getAgentPolicyId());
        putIfNotNull(payload, "strategyProfileId", request.getStrategyProfileId());
        payload.put("status", "FAILED");
        payload.put("modelCalls", attempts);
        if (request.getModelSlotId() != null) {
            payload.put("modelSlotIds", List.of(request.getModelSlotId()));
        }
        putIfNotNull(payload, "errorMessage", throwable == null ? null : throwable.getMessage());
        RawCompletionMetadata metadata = turnResult == null ? null : turnResult.getModelMetadata();
        if (metadata != null) {
            putIfNotNull(payload, "provider", metadata.getProvider());
            putIfNotNull(payload, "modelName", metadata.getModelName());
            putIfNotNull(payload, "inputTokens", metadata.getInputTokens());
            putIfNotNull(payload, "outputTokens", metadata.getOutputTokens());
        }
        return payload;
    }

    private void copyDiagnosticAttributes(Map<String, Object> target, Map<String, Object> attributes) {
        for (String key : RAW_METADATA_ATTRIBUTE_KEYS) {
            Object value = attributes.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}
