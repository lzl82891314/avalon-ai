package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.ModelGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegacySingleShotDeliberationPolicy implements DeliberationPolicy {
    private final ModelGateway modelGateway;
    private final AgentTurnRequestFactory requestFactory;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;
    private final ValidationRetryPolicy validationRetryPolicy;

    public LegacySingleShotDeliberationPolicy(ModelGateway modelGateway,
                                              AgentTurnRequestFactory requestFactory,
                                              PromptBuilder promptBuilder,
                                              ResponseParser responseParser,
                                              ValidationRetryPolicy validationRetryPolicy) {
        this.modelGateway = modelGateway;
        this.requestFactory = requestFactory;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.validationRetryPolicy = validationRetryPolicy;
    }

    @Override
    public String policyId() {
        return AgentPolicyIds.LEGACY_SINGLE_SHOT;
    }

    @Override
    public TurnAgentResult execute(PlayerTurnContext context, PlayerAgentConfig config) {
        AgentTurnRequest request = requestFactory.create(context, config);
        request.setPromptText(promptBuilder.build(request));
        ValidatedAgentTurn validated = validationRetryPolicy.execute(context, request, modelGateway, responseParser);
        AgentTurnResult turnResult = validated.turnResult();
        PlayerAction action = validated.action();
        return new TurnAgentResult(
                validated.request(),
                turnResult,
                action,
                validated.attempts(),
                policyId(),
                config == null ? null : config.effectiveStrategyProfileId(),
                List.of(traceStep(validated.request(), turnResult, validated.attempts())),
                policySummary(validated.request(), turnResult, validated.attempts())
        );
    }

    private Map<String, Object> traceStep(AgentTurnRequest request, AgentTurnResult turnResult, int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stageId", "single-shot");
        payload.put("mode", "completion");
        payload.put("modelSlotId", request.getModelSlotId());
        payload.put("provider", request.getProvider());
        payload.put("modelId", request.getModelId());
        payload.put("modelName", request.getModelName());
        payload.put("attempts", attempts);
        payload.put("outputSchemaVersion", request.getOutputSchemaVersion());
        payload.put("allowedActions", request.getAllowedActions());
        RawCompletionMetadata metadata = turnResult == null ? null : turnResult.getModelMetadata();
        if (metadata != null) {
            payload.put("inputTokens", metadata.getInputTokens());
            payload.put("outputTokens", metadata.getOutputTokens());
            if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
                payload.put("attributes", metadata.getAttributes());
            }
        }
        return payload;
    }

    private Map<String, Object> policySummary(AgentTurnRequest request, AgentTurnResult turnResult, int attempts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyId", policyId());
        payload.put("strategyProfileId", request.getStrategyProfileId());
        payload.put("modelCalls", attempts);
        payload.put("modelSlotIds", List.of(request.getModelSlotId()));
        RawCompletionMetadata metadata = turnResult == null ? null : turnResult.getModelMetadata();
        if (metadata != null) {
            payload.put("provider", metadata.getProvider());
            payload.put("modelName", metadata.getModelName());
            payload.put("inputTokens", metadata.getInputTokens());
            payload.put("outputTokens", metadata.getOutputTokens());
        }
        return payload;
    }
}
