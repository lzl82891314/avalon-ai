package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.ModelGateway;
import com.example.avalon.agent.gateway.StructuredModelGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.example.avalon.agent.model.TurnAgentResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomTotDeliberationPolicyTest {
    private final TomPolicyTestSupport support = new TomPolicyTestSupport();

    @Test
    void shouldExecuteBeliefTotAndDecisionStages() {
        AtomicReference<StructuredInferenceRequest> totRequestRef = new AtomicReference<>();
        AtomicReference<AgentTurnRequest> decisionRequestRef = new AtomicReference<>();

        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.82,"secondOrderAwarenessScore":0.41,"thirdOrderManipulationRisk":0.63,"confidence":0.76},
                            "P1":{"firstOrderEvilScore":0.99,"secondOrderAwarenessScore":0.99,"thirdOrderManipulationRisk":0.99,"confidence":0.99}
                          },
                          "strategyMode":"PRESSURE_TEST",
                          "lastSummary":"P2 remains the best pressure target",
                          "observationsToAdd":["P2 rushed the discussion cadence"],
                          "inferredFactsToAdd":["P2 may be shaping the suspicion chain"]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.2", 31L, 15L));
                return result;
            }
            totRequestRef.set(request);
            result.setPayload(support.json("""
                    {
                      "candidates":[
                        {
                          "candidateId":"C1",
                          "actionDraft":{"vote":"APPROVE"},
                          "actionPlanSummary":"approve and keep the table calm",
                          "projectedPublicReaction":"seen as stable",
                          "projectedVoteOutcome":"likely pass",
                          "projectedMissionRisk":"low info gain",
                          "expectedUtility":0.41,
                          "keyRisks":["too passive"]
                        },
                        {
                          "candidateId":"C2",
                          "actionDraft":{"vote":"REJECT"},
                          "actionPlanSummary":"reject to pressure the suspicious line",
                          "projectedPublicReaction":"starts a sharper debate",
                          "projectedVoteOutcome":"mixed table reaction",
                          "projectedMissionRisk":"medium exposure but better data",
                          "expectedUtility":0.79,
                          "keyRisks":["may attract heat"]
                        },
                        {
                          "candidateId":"C3",
                          "actionDraft":{"vote":"APPROVE"},
                          "actionPlanSummary":"approve but publicly hedge",
                          "projectedPublicReaction":"ambiguous",
                          "projectedVoteOutcome":"likely pass",
                          "projectedMissionRisk":"medium ambiguity",
                          "expectedUtility":0.56,
                          "keyRisks":["can look inconsistent"]
                        }
                      ],
                      "selectedCandidateId":"C2",
                      "summary":"C2 creates the strongest test without overcommitting"
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(support.metadata("openai", "gpt-5.2", 29L, 13L));
            return result;
        };

        ModelGateway modelGateway = request -> {
            decisionRequestRef.set(request.copy());
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"REJECT\"}");
            result.setAuditReason(new AuditReason());
            result.setModelMetadata(support.metadata("openai", "gpt-5.2", 27L, 9L));
            return result;
        };

        TomTotDeliberationPolicy policy = new TomTotDeliberationPolicy(
                structuredModelGateway,
                modelGateway,
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        TurnAgentResult result = policy.execute(
                support.teamVoteContext(),
                support.config(AgentPolicyIds.TOM_TOT_V1, "tom-tot-v1-baseline")
        );

        assertTrue(totRequestRef.get().getUserPrompt().contains("tomBeliefStage="));
        assertTrue(decisionRequestRef.get().getPromptText().contains("tomTotStage="));
        assertEquals("PRESSURE_TEST", decisionRequestRef.get().getMemory().get("strategyMode"));
        assertEquals(3, result.attempts());
        assertEquals("tom-tot-v1", result.policyId());
        assertEquals(3, result.executionTrace().size());
        assertEquals("belief-stage", result.executionTrace().get(0).get("stageId"));
        assertEquals("tot-stage", result.executionTrace().get(1).get("stageId"));
        assertEquals("decision-stage", result.executionTrace().get(2).get("stageId"));
        assertEquals(1, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().size());
        assertEquals(3, result.policySummary().get("stageCount"));
        assertEquals(3, result.policySummary().get("modelCalls"));
        assertEquals(3, result.policySummary().get("candidateCount"));
        assertEquals("C2", result.policySummary().get("selectedCandidateId"));
    }

    @Test
    void shouldWrapTotStageFailureWithTraceAndSummary() {
        StructuredModelGateway structuredModelGateway = request -> {
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                StructuredInferenceResult result = new StructuredInferenceResult();
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.71,"secondOrderAwarenessScore":0.35,"thirdOrderManipulationRisk":0.52,"confidence":0.68}
                          },
                          "strategyMode":"CAUTIOUS",
                          "lastSummary":"keep pressure limited",
                          "observationsToAdd":["P2 voted unstably"],
                          "inferredFactsToAdd":[]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.2", 19L, 9L));
                return result;
            }
            throw new IllegalStateException("tot exploded");
        };

        TomTotDeliberationPolicy policy = new TomTotDeliberationPolicy(
                structuredModelGateway,
                request -> {
                    throw new IllegalStateException("should not reach decision stage");
                },
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        AgentTurnExecutionException error = assertThrows(
                AgentTurnExecutionException.class,
                () -> policy.execute(
                        support.teamVoteContext(),
                        support.config(AgentPolicyIds.TOM_TOT_V1, "tom-tot-v1-baseline")
                )
        );

        assertEquals("tom-tot-v1 tot stage failed", error.getMessage());
        assertEquals(2, error.attempts());
        assertEquals(2, error.executionTrace().size());
        assertEquals("belief-stage", error.executionTrace().get(0).get("stageId"));
        assertEquals("tot-stage", error.executionTrace().get(1).get("stageId"));
        assertEquals("FAILED", error.executionTrace().get(1).get("status"));
        assertEquals("tot-stage", error.policySummary().get("failedStage"));
        assertEquals(1, error.policySummary().get("beliefCount"));
        assertEquals(0, error.policySummary().get("candidateCount"));
    }

    @Test
    void shouldRetryTotStageCompressionFailuresAndReflectActualAttempts() {
        AtomicInteger totAttempts = new AtomicInteger();

        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.63,"secondOrderAwarenessScore":0.31,"thirdOrderManipulationRisk":0.47,"confidence":0.64}
                          },
                          "strategyMode":"SAFE_DEFAULT",
                          "lastSummary":"先保留观察空间",
                          "observationsToAdd":["P2 仍然偏吵"],
                          "inferredFactsToAdd":[]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.4", 20L, 10L));
                return result;
            }
            if (totAttempts.incrementAndGet() == 1) {
                throw support.truncatedJsonFailure("openai", "gpt-5.4");
            }
            result.setPayload(support.json("""
                    {
                      "candidates":[
                        {"candidateId":"C1","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve","projectedPublicReaction":"calm","projectedVoteOutcome":"pass","projectedMissionRisk":"low","expectedUtility":0.44,"keyRisks":["soft"]},
                        {"candidateId":"C2","actionDraft":{"vote":"REJECT"},"actionPlanSummary":"reject","projectedPublicReaction":"mixed","projectedVoteOutcome":"contested","projectedMissionRisk":"medium","expectedUtility":0.73,"keyRisks":["heat"]},
                        {"candidateId":"C3","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve with hedge","projectedPublicReaction":"mixed","projectedVoteOutcome":"pass","projectedMissionRisk":"medium","expectedUtility":0.52,"keyRisks":["mixed"]}
                      ],
                      "selectedCandidateId":"C2",
                      "summary":"C2 gives the best pressure test"
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(support.metadata("openai", "gpt-5.4", 32L, 16L));
            return result;
        };

        TomTotDeliberationPolicy policy = new TomTotDeliberationPolicy(
                structuredModelGateway,
                request -> {
                    AgentTurnResult result = new AgentTurnResult();
                    result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"REJECT\"}");
                    result.setAuditReason(new AuditReason());
                    result.setModelMetadata(support.metadata("openai", "gpt-5.4", 18L, 9L));
                    return result;
                },
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        TurnAgentResult result = policy.execute(
                support.teamVoteContext(),
                support.config(AgentPolicyIds.TOM_TOT_V1, "tom-tot-v1-baseline")
        );

        assertEquals(4, result.attempts());
        assertEquals(2, result.executionTrace().get(1).get("attempts"));
        assertEquals(4, result.policySummary().get("modelCalls"));
    }
}
