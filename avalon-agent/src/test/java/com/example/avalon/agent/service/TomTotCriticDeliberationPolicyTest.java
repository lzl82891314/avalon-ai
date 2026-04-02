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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomTotCriticDeliberationPolicyTest {
    private final TomPolicyTestSupport support = new TomPolicyTestSupport();

    @Test
    void shouldExecuteBeliefTotCriticAndDecisionStages() {
        AtomicReference<StructuredInferenceRequest> criticRequestRef = new AtomicReference<>();
        AtomicReference<AgentTurnRequest> decisionRequestRef = new AtomicReference<>();

        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.77,"secondOrderAwarenessScore":0.38,"thirdOrderManipulationRisk":0.58,"confidence":0.72}
                          },
                          "strategyMode":"BALANCED_PRESSURE",
                          "lastSummary":"P2 still deserves structured pressure",
                          "observationsToAdd":["P2 defended the team too quickly"],
                          "inferredFactsToAdd":["P2 may be shaping the vote lane"]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.2", 33L, 16L));
                return result;
            }
            if (request.getDeveloperPrompt().contains("stageId=tot-stage")) {
                result.setPayload(support.json("""
                        {
                          "candidates":[
                            {
                              "candidateId":"C1",
                              "actionDraft":{"vote":"APPROVE"},
                              "actionPlanSummary":"approve and gather more data later",
                              "projectedPublicReaction":"calm",
                              "projectedVoteOutcome":"likely pass",
                              "projectedMissionRisk":"low pressure",
                              "expectedUtility":0.47,
                              "keyRisks":["too soft"]
                            },
                            {
                              "candidateId":"C2",
                              "actionDraft":{"vote":"REJECT"},
                              "actionPlanSummary":"reject and challenge the table logic",
                              "projectedPublicReaction":"more polarized",
                              "projectedVoteOutcome":"contested",
                              "projectedMissionRisk":"medium but informative",
                              "expectedUtility":0.74,
                              "keyRisks":["may draw suspicion"]
                            },
                            {
                              "candidateId":"C3",
                              "actionDraft":{"vote":"APPROVE"},
                              "actionPlanSummary":"approve but keep public speech narrow",
                              "projectedPublicReaction":"ambiguous",
                              "projectedVoteOutcome":"likely pass",
                              "projectedMissionRisk":"medium concealment",
                              "expectedUtility":0.58,
                              "keyRisks":["looks indecisive"]
                            }
                          ],
                          "selectedCandidateId":"C2",
                          "summary":"C2 offers the best information value"
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.2", 27L, 12L));
                return result;
            }
            criticRequestRef.set(request);
            result.setPayload(support.json("""
                    {
                      "status":"MIXED",
                      "riskFindings":["A hard reject can make Merlin too readable"],
                      "counterSignals":["The line is still defensible as a cautious good player"],
                      "recommendedAdjustments":["Keep the speech short and avoid certainty"],
                      "summary":"Use C2 but soften the explanation"
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(support.metadata("openai", "gpt-5.2-mini", 21L, 8L));
            return result;
        };

        ModelGateway modelGateway = request -> {
            decisionRequestRef.set(request.copy());
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"REJECT\"}");
            result.setAuditReason(new AuditReason());
            result.setModelMetadata(support.metadata("openai", "gpt-5.2", 24L, 10L));
            return result;
        };

        TomTotCriticDeliberationPolicy policy = new TomTotCriticDeliberationPolicy(
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
                support.configWithCriticSlot(AgentPolicyIds.TOM_TOT_CRITIC_V1, "tom-tot-critic-v1-baseline")
        );

        assertEquals("critic", criticRequestRef.get().getModelSlotId());
        assertEquals("actor", decisionRequestRef.get().getModelSlotId());
        assertTrue(decisionRequestRef.get().getPromptText().contains("tomCriticStage="));
        assertEquals(4, result.attempts());
        assertEquals(4, result.executionTrace().size());
        assertEquals("critic-stage", result.executionTrace().get(2).get("stageId"));
        assertEquals("decision-stage", result.executionTrace().get(3).get("stageId"));
        assertEquals(4, result.policySummary().get("stageCount"));
        assertEquals(4, result.policySummary().get("modelCalls"));
        assertEquals(3, result.policySummary().get("candidateCount"));
        assertEquals("C2", result.policySummary().get("selectedCandidateId"));
        assertEquals("MIXED", result.policySummary().get("criticStatus"));
        assertEquals(List.of("actor", "critic"), result.policySummary().get("modelSlotIds"));
    }

    @Test
    void shouldFallbackCriticStageToActorWhenCriticSlotMissing() {
        AtomicReference<StructuredInferenceRequest> criticRequestRef = new AtomicReference<>();

        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.65,"secondOrderAwarenessScore":0.32,"thirdOrderManipulationRisk":0.49,"confidence":0.66}
                          },
                          "strategyMode":"SAFE_DEFAULT",
                          "lastSummary":"keep the line conservative",
                          "observationsToAdd":["P2 is still noisy"],
                          "inferredFactsToAdd":[]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("noop", "deterministic-fallback", 10L, 6L));
                return result;
            }
            if (request.getDeveloperPrompt().contains("stageId=tot-stage")) {
                result.setPayload(support.json("""
                        {
                          "candidates":[
                            {"candidateId":"C1","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve","projectedPublicReaction":"calm","projectedVoteOutcome":"pass","projectedMissionRisk":"low","expectedUtility":0.45,"keyRisks":[]},
                            {"candidateId":"C2","actionDraft":{"vote":"REJECT"},"actionPlanSummary":"reject","projectedPublicReaction":"mixed","projectedVoteOutcome":"mixed","projectedMissionRisk":"medium","expectedUtility":0.68,"keyRisks":[]},
                            {"candidateId":"C3","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve with hedge","projectedPublicReaction":"mixed","projectedVoteOutcome":"pass","projectedMissionRisk":"medium","expectedUtility":0.51,"keyRisks":[]}
                          ],
                          "selectedCandidateId":"C2",
                          "summary":"C2 remains preferred"
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("noop", "deterministic-fallback", 10L, 6L));
                return result;
            }
            criticRequestRef.set(request);
            result.setPayload(support.json("""
                    {
                      "status":"SUPPORT",
                      "riskFindings":["limited risk"],
                      "counterSignals":["line remains reversible"],
                      "recommendedAdjustments":["stay concise"],
                      "summary":"actor slot fallback is acceptable"
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(support.metadata("noop", "deterministic-fallback", 10L, 6L));
            return result;
        };

        TomTotCriticDeliberationPolicy policy = new TomTotCriticDeliberationPolicy(
                structuredModelGateway,
                request -> {
                    AgentTurnResult result = new AgentTurnResult();
                    result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"REJECT\"}");
                    result.setAuditReason(new AuditReason());
                    result.setModelMetadata(support.metadata("noop", "deterministic-fallback", 10L, 6L));
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
                support.config(AgentPolicyIds.TOM_TOT_CRITIC_V1, "tom-tot-critic-v1-baseline")
        );

        assertEquals("actor", criticRequestRef.get().getModelSlotId());
        assertEquals(List.of("actor"), result.policySummary().get("modelSlotIds"));
        assertEquals("actor", result.executionTrace().get(2).get("modelSlotId"));
    }

    @Test
    void shouldWrapCriticStageFailureWithCompletedPriorStages() {
        StructuredModelGateway structuredModelGateway = request -> {
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                StructuredInferenceResult result = new StructuredInferenceResult();
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.69,"secondOrderAwarenessScore":0.33,"thirdOrderManipulationRisk":0.51,"confidence":0.64}
                          },
                          "strategyMode":"CAUTIOUS",
                          "lastSummary":"keep pressure available",
                          "observationsToAdd":["P2 remains unstable"],
                          "inferredFactsToAdd":[]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.2", 18L, 8L));
                return result;
            }
            if (request.getDeveloperPrompt().contains("stageId=tot-stage")) {
                StructuredInferenceResult result = new StructuredInferenceResult();
                result.setPayload(support.json("""
                        {
                          "candidates":[
                            {"candidateId":"C1","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve","projectedPublicReaction":"calm","projectedVoteOutcome":"pass","projectedMissionRisk":"low","expectedUtility":0.46,"keyRisks":[]},
                            {"candidateId":"C2","actionDraft":{"vote":"REJECT"},"actionPlanSummary":"reject","projectedPublicReaction":"mixed","projectedVoteOutcome":"mixed","projectedMissionRisk":"medium","expectedUtility":0.71,"keyRisks":[]},
                            {"candidateId":"C3","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve with hedge","projectedPublicReaction":"mixed","projectedVoteOutcome":"pass","projectedMissionRisk":"medium","expectedUtility":0.53,"keyRisks":[]}
                          ],
                          "selectedCandidateId":"C2",
                          "summary":"C2 remains preferred"
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.2", 17L, 7L));
                return result;
            }
            throw new IllegalStateException("critic exploded");
        };

        TomTotCriticDeliberationPolicy policy = new TomTotCriticDeliberationPolicy(
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
                        support.configWithCriticSlot(AgentPolicyIds.TOM_TOT_CRITIC_V1, "tom-tot-critic-v1-baseline")
                )
        );

        assertEquals("tom-tot-critic-v1 critic stage failed", error.getMessage());
        assertEquals(3, error.attempts());
        assertEquals(3, error.executionTrace().size());
        assertEquals("belief-stage", error.executionTrace().get(0).get("stageId"));
        assertEquals("tot-stage", error.executionTrace().get(1).get("stageId"));
        assertEquals("critic-stage", error.executionTrace().get(2).get("stageId"));
        assertEquals("FAILED", error.executionTrace().get(2).get("status"));
        assertEquals("critic-stage", error.policySummary().get("failedStage"));
        assertEquals(3, error.policySummary().get("modelCalls"));
        assertEquals(List.of("actor", "critic"), error.policySummary().get("modelSlotIds"));
    }

    @Test
    void shouldRetryCriticStageCompressionFailuresAndReflectActualAttempts() {
        AtomicInteger criticAttempts = new AtomicInteger();

        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                result.setPayload(support.json("""
                        {
                          "beliefsByPlayerId":{
                            "P2":{"firstOrderEvilScore":0.68,"secondOrderAwarenessScore":0.34,"thirdOrderManipulationRisk":0.52,"confidence":0.67}
                          },
                          "strategyMode":"SAFE_DEFAULT",
                          "lastSummary":"先保持可回退压力",
                          "observationsToAdd":["P2 仍值得持续观察"],
                          "inferredFactsToAdd":[]
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.4", 18L, 8L));
                return result;
            }
            if (request.getDeveloperPrompt().contains("stageId=tot-stage")) {
                result.setPayload(support.json("""
                        {
                          "candidates":[
                            {"candidateId":"C1","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve","projectedPublicReaction":"calm","projectedVoteOutcome":"pass","projectedMissionRisk":"low","expectedUtility":0.41,"keyRisks":["soft"]},
                            {"candidateId":"C2","actionDraft":{"vote":"REJECT"},"actionPlanSummary":"reject","projectedPublicReaction":"mixed","projectedVoteOutcome":"mixed","projectedMissionRisk":"medium","expectedUtility":0.72,"keyRisks":["heat"]},
                            {"candidateId":"C3","actionDraft":{"vote":"APPROVE"},"actionPlanSummary":"approve with hedge","projectedPublicReaction":"mixed","projectedVoteOutcome":"pass","projectedMissionRisk":"medium","expectedUtility":0.53,"keyRisks":["mixed"]}
                          ],
                          "selectedCandidateId":"C2",
                          "summary":"C2 remains preferred"
                        }
                        """));
                result.setRawJson(result.getPayload().toString());
                result.setModelMetadata(support.metadata("openai", "gpt-5.4", 24L, 12L));
                return result;
            }
            if (criticAttempts.incrementAndGet() == 1) {
                throw support.truncatedJsonFailure("openai", "gpt-5.4");
            }
            result.setPayload(support.json("""
                    {
                      "status":"MIXED",
                      "riskFindings":["hard reject may expose pattern"],
                      "counterSignals":["still plausible as cautious good"],
                      "recommendedAdjustments":["keep speech short"],
                      "summary":"use C2 but soften the rationale"
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(support.metadata("openai", "gpt-5.4", 21L, 9L));
            return result;
        };

        TomTotCriticDeliberationPolicy policy = new TomTotCriticDeliberationPolicy(
                structuredModelGateway,
                request -> {
                    AgentTurnResult result = new AgentTurnResult();
                    result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"REJECT\"}");
                    result.setAuditReason(new AuditReason());
                    result.setModelMetadata(support.metadata("openai", "gpt-5.4", 19L, 8L));
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
                support.configWithCriticSlot(AgentPolicyIds.TOM_TOT_CRITIC_V1, "tom-tot-critic-v1-baseline")
        );

        assertEquals(5, result.attempts());
        assertEquals(2, result.executionTrace().get(2).get("attempts"));
        assertEquals(5, result.policySummary().get("modelCalls"));
        assertEquals(List.of("actor", "critic"), result.policySummary().get("modelSlotIds"));
    }

    @Test
    void shouldTolerateLooseStructuredPayloadsAcrossAllStages() {
        TomTotCriticDeliberationPolicy policy = new TomTotCriticDeliberationPolicy(
                request -> {
                    StructuredInferenceResult result = new StructuredInferenceResult();
                    if (request.getDeveloperPrompt().contains("stageId=belief-stage")) {
                        result.setPayload(support.json("""
                                {
                                  "beliefsByPlayerId":{
                                    "P2":{"firstOrderEvilScore":"0.81","confidence":"0.74"},
                                    "P3":"suspicious",
                                    "P1":{"firstOrderEvilScore":0.99}
                                  },
                                  "strategyMode":true,
                                  "lastSummary":9,
                                  "observationsToAdd":"P2 kept hedging",
                                  "inferredFactsToAdd":[null,"P2 may be baiting reactions"]
                                }
                                """));
                        result.setRawJson(result.getPayload().toString());
                        result.setModelMetadata(support.metadata("openai", "gpt-5.4", 22L, 11L));
                        return result;
                    }
                    if (request.getDeveloperPrompt().contains("stageId=tot-stage")) {
                        result.setPayload(support.json("""
                                {
                                  "candidates":{
                                    "candidateId":"C7",
                                    "actionDraft":"REJECT",
                                    "actionPlanSummary":false,
                                    "projectedPublicReaction":"mixed",
                                    "projectedVoteOutcome":true,
                                    "projectedMissionRisk":0.6,
                                    "expectedUtility":"0.73",
                                    "keyRisks":"heat"
                                  },
                                  "summary":false
                                }
                                """));
                        result.setRawJson(result.getPayload().toString());
                        result.setModelMetadata(support.metadata("openai", "gpt-5.4", 24L, 12L));
                        return result;
                    }
                    result.setPayload(support.json("""
                            {
                              "status":1,
                              "riskFindings":"hard push may expose alignment",
                              "counterSignals":["line still looks plausible"],
                              "recommendedAdjustments":true
                            }
                            """));
                    result.setRawJson(result.getPayload().toString());
                    result.setModelMetadata(support.metadata("openai", "gpt-5.4-mini", 18L, 8L));
                    return result;
                },
                request -> {
                    AgentTurnResult result = new AgentTurnResult();
                    result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"REJECT\"}");
                    result.setAuditReason(new AuditReason());
                    result.setModelMetadata(support.metadata("openai", "gpt-5.4", 20L, 9L));
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
                support.configWithCriticSlot(AgentPolicyIds.TOM_TOT_CRITIC_V1, "tom-tot-critic-v1-baseline")
        );

        assertEquals(4, result.attempts());
        assertEquals(1, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().size());
        assertEquals(0.81, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").firstOrderEvilScore());
        assertEquals(0.5, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").secondOrderAwarenessScore());
        assertEquals("1", result.policySummary().get("criticStatus"));
        assertEquals(1, result.policySummary().get("candidateCount"));
        assertEquals("C7", result.policySummary().get("selectedCandidateId"));
    }
}
