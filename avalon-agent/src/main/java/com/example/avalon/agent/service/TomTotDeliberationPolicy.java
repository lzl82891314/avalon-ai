package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.ModelGateway;
import com.example.avalon.agent.gateway.StructuredModelGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.example.avalon.agent.model.TomBeliefStageResult;
import com.example.avalon.agent.model.TomTotStageResult;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TomTotDeliberationPolicy implements DeliberationPolicy {
    private final StructuredModelGateway structuredModelGateway;
    private final ModelGateway modelGateway;
    private final AgentTurnRequestFactory requestFactory;
    private final TomPromptFactory tomPromptFactory;
    private final ResponseParser responseParser;
    private final ValidationRetryPolicy validationRetryPolicy;
    private final StructuredStageRetryPolicy structuredStageRetryPolicy;
    private final TomPolicySupport support = new TomPolicySupport();

    public TomTotDeliberationPolicy(StructuredModelGateway structuredModelGateway,
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
        return AgentPolicyIds.TOM_TOT_V1;
    }

    @Override
    public TurnAgentResult execute(PlayerTurnContext context, PlayerAgentConfig config) {
        PlayerAgentConfig effectiveConfig = config == null ? new PlayerAgentConfig() : config;

        AgentTurnRequest beliefRequest = requestFactory.create(context, effectiveConfig, "actor");
        beliefRequest.setPromptText(tomPromptFactory.buildBeliefUserPrompt(beliefRequest));

        StructuredStageRetryPolicy.StructuredStageExecution beliefExecution;
        StructuredInferenceResult beliefStructuredResult;
        TomBeliefStageResult beliefStageResult;
        Map<String, Object> beliefTrace;
        int beliefAttempts = 0;
        try {
            beliefExecution = structuredStageRetryPolicy.execute("belief-stage", support.structuredRequest(
                    beliefRequest,
                    tomPromptFactory.buildBeliefDeveloperPrompt(beliefRequest),
                    beliefRequest.getPromptText()
            ), structuredModelGateway);
            beliefStructuredResult = beliefExecution.result();
            beliefAttempts = beliefExecution.attempts();
            beliefStageResult = support.parseBeliefStage(context, beliefStructuredResult, policyId());
            beliefTrace = support.stageTrace(
                    "belief-stage",
                    "structured-inference",
                    "COMPLETED",
                    beliefRequest,
                    beliefAttempts,
                    beliefStructuredResult.getModelMetadata(),
                    beliefTraceExtras(beliefStageResult)
            );
        } catch (RuntimeException exception) {
            RuntimeException failure = exception instanceof StructuredStageRetryPolicy.StructuredStageExecutionException structuredException
                    ? structuredException.failure()
                    : exception;
            int attempts = exception instanceof StructuredStageRetryPolicy.StructuredStageExecutionException structuredException
                    ? structuredException.attempts()
                    : Math.max(beliefAttempts, 1);
            throw new AgentTurnExecutionException(
                    "tom-tot-v1 belief stage failed",
                    beliefRequest,
                    null,
                    attempts,
                    List.of(support.failedStageTrace(
                            "belief-stage",
                            "structured-inference",
                            beliefRequest,
                            attempts,
                            null,
                            failure,
                            Map.of()
                    )),
                    support.failedPolicySummary(
                            policyId(),
                            beliefRequest,
                            3,
                            attempts,
                            support.modelSlotIds(beliefRequest),
                            0,
                            "belief-stage",
                            failure
                    ),
                    failure
            );
        }

        AgentTurnRequest totRequest = requestFactory.create(context, effectiveConfig, "actor");
        totRequest.setMemory(support.mergeBeliefIntoMemory(totRequest.getMemory(), beliefStageResult));
        totRequest.setPromptText(tomPromptFactory.buildTotUserPrompt(totRequest, beliefStageResult));

        StructuredStageRetryPolicy.StructuredStageExecution totExecution;
        StructuredInferenceResult totStructuredResult;
        TomTotStageResult totStageResult;
        Map<String, Object> totTrace;
        int totAttempts = 0;
        try {
            totExecution = structuredStageRetryPolicy.execute("tot-stage", support.structuredRequest(
                    totRequest,
                    tomPromptFactory.buildTotDeveloperPrompt(totRequest),
                    totRequest.getPromptText()
            ), structuredModelGateway);
            totStructuredResult = totExecution.result();
            totAttempts = totExecution.attempts();
            totStageResult = support.parseTotStage(totStructuredResult, policyId());
            totTrace = support.stageTrace(
                    "tot-stage",
                    "structured-inference",
                    "COMPLETED",
                    totRequest,
                    totAttempts,
                    totStructuredResult.getModelMetadata(),
                    Map.of(
                            "candidateCount", totStageResult.getCandidates().size(),
                            "selectedCandidateId", totStageResult.getSelectedCandidateId()
                    )
            );
        } catch (RuntimeException exception) {
            RuntimeException failure = exception instanceof StructuredStageRetryPolicy.StructuredStageExecutionException structuredException
                    ? structuredException.failure()
                    : exception;
            int attempts = exception instanceof StructuredStageRetryPolicy.StructuredStageExecutionException structuredException
                    ? structuredException.attempts()
                    : Math.max(totAttempts, 1);
            throw new AgentTurnExecutionException(
                    "tom-tot-v1 tot stage failed",
                    totRequest,
                    null,
                    beliefAttempts + attempts,
                    List.of(
                            beliefTrace,
                            support.failedStageTrace(
                                    "tot-stage",
                                    "structured-inference",
                                    totRequest,
                                    attempts,
                                    null,
                                    failure,
                                    Map.of()
                            )
                    ),
                    failedPolicySummary(
                            totRequest,
                            beliefAttempts + attempts,
                            beliefStageResult.getBeliefsByPlayerId().size(),
                            "tot-stage",
                            0,
                            null,
                            failure
                    ),
                    failure
            );
        }

        AgentTurnRequest decisionRequest = requestFactory.create(context, effectiveConfig, "actor");
        decisionRequest.setMemory(support.mergeBeliefIntoMemory(decisionRequest.getMemory(), beliefStageResult));
        decisionRequest.setPromptText(tomPromptFactory.buildDecisionPrompt(
                decisionRequest,
                beliefStageResult,
                totStageResult
        ));

        try {
            ValidatedAgentTurn validated = validationRetryPolicy.execute(
                    context,
                    decisionRequest,
                    modelGateway,
                    responseParser
            );
            AgentTurnResult enrichedTurnResult = support.enrichTurnResult(
                    validated.turnResult(),
                    validated.action(),
                    beliefStageResult,
                    policyId()
            );
            List<Map<String, Object>> executionTrace = List.of(
                    beliefTrace,
                    totTrace,
                    support.stageTrace(
                            "decision-stage",
                            "completion",
                            "COMPLETED",
                            validated.request(),
                            validated.attempts(),
                            enrichedTurnResult.getModelMetadata(),
                            Map.of("outputSchemaVersion", validated.request().getOutputSchemaVersion())
                    )
            );
            return new TurnAgentResult(
                    validated.request(),
                    enrichedTurnResult,
                    validated.action(),
                    beliefAttempts + totAttempts + validated.attempts(),
                    policyId(),
                    effectiveConfig.effectiveStrategyProfileId(),
                    executionTrace,
                    successPolicySummary(
                            validated.request(),
                            beliefStructuredResult,
                            totStructuredResult,
                            enrichedTurnResult,
                            beliefStageResult,
                            totStageResult,
                            beliefAttempts,
                            totAttempts,
                            validated.attempts()
                    )
            );
        } catch (AgentTurnExecutionException exception) {
            List<Map<String, Object>> executionTrace = List.of(
                    beliefTrace,
                    totTrace,
                    support.failedStageTrace(
                            "decision-stage",
                            "completion",
                            exception.request(),
                            exception.attempts(),
                            exception.lastTurnResult(),
                            exception.getCause(),
                            Map.of("outputSchemaVersion", exception.request().getOutputSchemaVersion())
                    )
            );
            throw new AgentTurnExecutionException(
                    exception.getMessage(),
                    exception.request(),
                    exception.lastTurnResult(),
                    beliefAttempts + totAttempts + exception.attempts(),
                    executionTrace,
                    failedPolicySummary(
                            exception.request(),
                            beliefAttempts + totAttempts + exception.attempts(),
                            beliefStageResult.getBeliefsByPlayerId().size(),
                            "decision-stage",
                            totStageResult.getCandidates().size(),
                            totStageResult.getSelectedCandidateId(),
                            exception.getCause()
                    ),
                    exception.getCause()
            );
        }
    }

    private Map<String, Object> successPolicySummary(AgentTurnRequest decisionRequest,
                                                     StructuredInferenceResult beliefStructuredResult,
                                                     StructuredInferenceResult totStructuredResult,
                                                     AgentTurnResult turnResult,
                                                     TomBeliefStageResult beliefStageResult,
                                                     TomTotStageResult totStageResult,
                                                     int beliefAttempts,
                                                     int totAttempts,
                                                     int decisionAttempts) {
        Map<String, Object> summary = support.baseSuccessPolicySummary(
                policyId(),
                decisionRequest,
                3,
                beliefAttempts + totAttempts + decisionAttempts,
                support.modelSlotIds(decisionRequest),
                beliefStageResult.getBeliefsByPlayerId().size(),
                support.tokenValue(beliefStructuredResult.getModelMetadata())
                        + support.tokenValue(totStructuredResult.getModelMetadata())
                        + support.tokenValue(turnResult.getModelMetadata()),
                support.outputTokenValue(beliefStructuredResult.getModelMetadata())
                        + support.outputTokenValue(totStructuredResult.getModelMetadata())
                        + support.outputTokenValue(turnResult.getModelMetadata())
        );
        summary.put("candidateCount", totStageResult.getCandidates().size());
        summary.put("selectedCandidateId", totStageResult.getSelectedCandidateId());
        return summary;
    }

    private Map<String, Object> failedPolicySummary(AgentTurnRequest request,
                                                    int totalModelCalls,
                                                    int beliefCount,
                                                    String failedStage,
                                                    int candidateCount,
                                                    String selectedCandidateId,
                                                    Throwable throwable) {
        Map<String, Object> summary = new LinkedHashMap<>(support.failedPolicySummary(
                policyId(),
                request,
                3,
                totalModelCalls,
                support.modelSlotIds(request),
                beliefCount,
                failedStage,
                throwable
        ));
        summary.put("candidateCount", candidateCount);
        if (selectedCandidateId != null) {
            summary.put("selectedCandidateId", selectedCandidateId);
        }
        return summary;
    }

    private Map<String, Object> beliefTraceExtras(TomBeliefStageResult beliefStageResult) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("beliefCount", beliefStageResult.getBeliefsByPlayerId().size());
        if (beliefStageResult.getStrategyMode() != null) {
            extras.put("strategyMode", beliefStageResult.getStrategyMode());
        }
        return extras;
    }
}
