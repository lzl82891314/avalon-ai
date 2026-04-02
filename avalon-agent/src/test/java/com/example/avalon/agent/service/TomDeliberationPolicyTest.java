package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.ModelGateway;
import com.example.avalon.agent.gateway.StructuredModelGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomDeliberationPolicyTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldExecuteBeliefAndDecisionStagesAndMergeBeliefOutputs() {
        AtomicReference<StructuredInferenceRequest> beliefRequestRef = new AtomicReference<>();
        AtomicReference<AgentTurnRequest> decisionRequestRef = new AtomicReference<>();
        StructuredModelGateway structuredModelGateway = request -> {
            beliefRequestRef.set(request);
            StructuredInferenceResult result = new StructuredInferenceResult();
            result.setPayload(json("""
                    {
                      "beliefsByPlayerId":{
                        "P2":{"firstOrderEvilScore":0.82,"secondOrderAwarenessScore":0.41,"thirdOrderManipulationRisk":0.63,"confidence":0.76},
                        "P1":{"firstOrderEvilScore":0.99,"secondOrderAwarenessScore":0.99,"thirdOrderManipulationRisk":0.99,"confidence":0.99}
                      },
                      "strategyMode":"PRESSURE_TEST",
                      "lastSummary":"P2 当前最值得持续施压",
                      "observationsToAdd":["P2 讨论期抢节奏"],
                      "inferredFactsToAdd":["P2 可能在测试怀疑链条"]
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(metadata("openai", "gpt-5.2", 33L, 17L));
            return result;
        };
        ModelGateway modelGateway = request -> {
            decisionRequestRef.set(request.copy());
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            result.setAuditReason(new AuditReason());
            result.setModelMetadata(metadata("openai", "gpt-5.2", 25L, 11L));
            return result;
        };
        TomDeliberationPolicy policy = new TomDeliberationPolicy(
                structuredModelGateway,
                modelGateway,
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        TurnAgentResult result = policy.execute(teamVoteContext(), config());

        assertEquals("actor", beliefRequestRef.get().getModelSlotId());
        assertEquals("actor", decisionRequestRef.get().getModelSlotId());
        assertTrue(decisionRequestRef.get().getPromptText().contains("tomBeliefStage="));
        assertEquals("PRESSURE_TEST", decisionRequestRef.get().getMemory().get("strategyMode"));
        assertTrue(String.valueOf(decisionRequestRef.get().getMemory().get("beliefsByPlayerId")).contains("P2"));
        assertEquals(2, result.attempts());
        assertEquals("tom-v1", result.policyId());
        assertEquals(2, result.executionTrace().size());
        assertEquals("belief-stage", result.executionTrace().get(0).get("stageId"));
        assertEquals("decision-stage", result.executionTrace().get(1).get("stageId"));
        assertEquals(1, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().size());
        assertEquals("PRESSURE_TEST", result.turnResult().getMemoryUpdate().getStrategyMode());
        assertEquals("P2 当前最值得持续施压", result.turnResult().getMemoryUpdate().getLastSummary());
        assertEquals(
                0.82,
                result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").firstOrderEvilScore()
        );
        assertEquals("PRESSURE_TEST", result.turnResult().getAuditReason().getBeliefs().get("strategyMode"));
        assertEquals(1, assertInstanceOf(Map.class, result.turnResult().getAuditReason().getBeliefs().get("beliefsByPlayerId")).size());
        assertEquals(1, result.policySummary().get("beliefCount"));
        assertEquals(2, result.policySummary().get("stageCount"));
    }

    @Test
    void shouldWrapBeliefStageFailureWithTraceAndSummary() {
        TomDeliberationPolicy policy = new TomDeliberationPolicy(
                request -> {
                    throw new IllegalStateException("belief exploded");
                },
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
                () -> policy.execute(teamVoteContext(), config())
        );

        assertEquals("tom-v1 belief stage failed", error.getMessage());
        assertEquals(1, error.attempts());
        assertEquals(1, error.executionTrace().size());
        assertEquals("belief-stage", error.executionTrace().get(0).get("stageId"));
        assertEquals("FAILED", error.executionTrace().get(0).get("status"));
        assertEquals("belief-stage", error.policySummary().get("failedStage"));
        assertEquals(0, error.policySummary().get("beliefCount"));
    }

    @Test
    void shouldIncludeBothStagesWhenDecisionStageFails() {
        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            result.setPayload(json("""
                    {
                      "beliefsByPlayerId":{
                        "P2":{"firstOrderEvilScore":0.71,"secondOrderAwarenessScore":0.35,"thirdOrderManipulationRisk":0.52,"confidence":0.68}
                      },
                      "strategyMode":"CAUTIOUS",
                      "lastSummary":"先保持怀疑",
                      "observationsToAdd":["P2 投票不稳定"],
                      "inferredFactsToAdd":[]
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(metadata("openai", "gpt-5.2", 19L, 9L));
            return result;
        };
        ModelGateway modelGateway = request -> {
            throw new IllegalStateException("decision exploded");
        };
        TomDeliberationPolicy policy = new TomDeliberationPolicy(
                structuredModelGateway,
                modelGateway,
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        AgentTurnExecutionException error = assertThrows(
                AgentTurnExecutionException.class,
                () -> policy.execute(teamVoteContext(), config())
        );

        assertEquals(3, error.attempts());
        assertEquals(2, error.executionTrace().size());
        assertEquals("COMPLETED", error.executionTrace().get(0).get("status"));
        assertEquals("decision-stage", error.executionTrace().get(1).get("stageId"));
        assertEquals("FAILED", error.executionTrace().get(1).get("status"));
        assertEquals(2, error.executionTrace().get(1).get("attempts"));
        assertEquals("decision-stage", error.policySummary().get("failedStage"));
        assertEquals(1, error.policySummary().get("beliefCount"));
        assertEquals(3, error.policySummary().get("modelCalls"));
    }

    @Test
    void shouldRetryBeliefStageCompressionFailuresAndReflectActualAttempts() {
        AtomicInteger beliefAttempts = new AtomicInteger();
        StructuredModelGateway structuredModelGateway = request -> {
            if (beliefAttempts.incrementAndGet() == 1) {
                throw new TomPolicyTestSupport().truncatedJsonFailure("openai", "gpt-5.4");
            }
            StructuredInferenceResult result = new StructuredInferenceResult();
            result.setPayload(json("""
                    {
                      "beliefsByPlayerId":{
                        "P2":{"firstOrderEvilScore":0.74,"secondOrderAwarenessScore":0.34,"thirdOrderManipulationRisk":0.49,"confidence":0.69}
                      },
                      "strategyMode":"SAFE_DEFAULT",
                      "lastSummary":"先保持低承诺怀疑",
                      "observationsToAdd":["P2 仍然值得继续观察"],
                      "inferredFactsToAdd":[]
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(metadata("openai", "gpt-5.4", 40L, 22L));
            return result;
        };
        ModelGateway modelGateway = request -> {
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            result.setAuditReason(new AuditReason());
            result.setModelMetadata(metadata("openai", "gpt-5.4", 26L, 11L));
            return result;
        };
        TomDeliberationPolicy policy = new TomDeliberationPolicy(
                structuredModelGateway,
                modelGateway,
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        TurnAgentResult result = policy.execute(teamVoteContext(), config());

        assertEquals(3, result.attempts());
        assertEquals(2, result.executionTrace().get(0).get("attempts"));
        assertEquals(3, result.policySummary().get("modelCalls"));
    }

    @Test
    void shouldTolerateIncompleteBeliefPayloadShapes() {
        StructuredModelGateway structuredModelGateway = request -> {
            StructuredInferenceResult result = new StructuredInferenceResult();
            result.setPayload(json("""
                    {
                      "beliefsByPlayerId":{
                        "P2":{"firstOrderEvilScore":"0.74","confidence":"0.69"},
                        "P3":"suspicious",
                        "P1":{"firstOrderEvilScore":0.99,"secondOrderAwarenessScore":0.99,"thirdOrderManipulationRisk":0.99,"confidence":0.99}
                      },
                      "strategyMode":true,
                      "lastSummary":7,
                      "observationsToAdd":"P2 kept hedging",
                      "inferredFactsToAdd":[null,"P2 may be testing reactions"]
                    }
                    """));
            result.setRawJson(result.getPayload().toString());
            result.setModelMetadata(metadata("openai", "gpt-5.4", 28L, 13L));
            return result;
        };
        ModelGateway modelGateway = request -> {
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            result.setAuditReason(new AuditReason());
            result.setModelMetadata(metadata("openai", "gpt-5.4", 23L, 9L));
            return result;
        };
        TomDeliberationPolicy policy = new TomDeliberationPolicy(
                structuredModelGateway,
                modelGateway,
                new AgentTurnRequestFactory(),
                new TomPromptFactory(new PromptBuilder()),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new StructuredStageRetryPolicy()
        );

        TurnAgentResult result = policy.execute(teamVoteContext(), config());

        assertEquals(1, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().size());
        assertEquals(0.74, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").firstOrderEvilScore());
        assertEquals(0.5, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").secondOrderAwarenessScore());
        assertEquals(0.5, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").thirdOrderManipulationRisk());
        assertEquals(0.69, result.turnResult().getMemoryUpdate().getBeliefsToUpsert().get("P2").confidence());
        assertEquals("true", result.turnResult().getMemoryUpdate().getStrategyMode());
        assertEquals("7", result.turnResult().getMemoryUpdate().getLastSummary());
        assertEquals(List.of("P2 kept hedging"), result.turnResult().getMemoryUpdate().getObservationsToAdd());
        assertEquals(List.of("P2 may be testing reactions"), result.turnResult().getMemoryUpdate().getInferredFactsToAdd());
    }

    private PlayerAgentConfig config() {
        PlayerAgentConfig config = new PlayerAgentConfig();
        config.setAgentPolicyId(AgentPolicyIds.TOM_V1);
        config.setStrategyProfileId("tom-v1-baseline");
        return config;
    }

    private RawCompletionMetadata metadata(String provider, String modelName, long inputTokens, long outputTokens) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider(provider);
        metadata.setModelName(modelName);
        metadata.setInputTokens(inputTokens);
        metadata.setOutputTokens(outputTokens);
        metadata.setAttributes(new LinkedHashMap<>(Map.of("gatewayType", "test")));
        return metadata;
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build test JSON", exception);
        }
    }

    private PlayerTurnContext teamVoteContext() {
        RuleSetDefinition ruleSetDefinition = new RuleSetDefinition(
                "avalon-classic-5p-v1",
                "Avalon Classic 5 Players",
                "1.0.0",
                5,
                5,
                List.of(
                        new RoundTeamSizeRule(1, 2),
                        new RoundTeamSizeRule(2, 3),
                        new RoundTeamSizeRule(3, 2),
                        new RoundTeamSizeRule(4, 3),
                        new RoundTeamSizeRule(5, 3)
                ),
                Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1),
                List.of("classic-5p-v1"),
                new AssassinationRuleDefinition(true, "ASSASSIN", "MERLIN"),
                new VisibilityPolicyDefinition(true),
                true
        );
        return new PlayerTurnContext(
                "game-1",
                1,
                GamePhase.TEAM_VOTE.name(),
                "P1",
                1,
                "MERLIN",
                new PublicGameSnapshot(
                        "game-1",
                        GameStatus.RUNNING,
                        GamePhase.TEAM_VOTE,
                        1,
                        0,
                        0,
                        0,
                        1,
                        List.of("P1", "P2"),
                        null,
                        null,
                        List.of(
                                new PublicPlayerSummary("game-1", "P1", 1, "P1", PlayerControllerType.LLM, PlayerConnectionState.DISCONNECTED),
                                new PublicPlayerSummary("game-1", "P2", 2, "P2", PlayerControllerType.SCRIPTED, PlayerConnectionState.DISCONNECTED),
                                new PublicPlayerSummary("game-1", "P3", 3, "P3", PlayerControllerType.SCRIPTED, PlayerConnectionState.DISCONNECTED)
                        ),
                        Instant.parse("2026-03-24T00:00:00Z")
                ),
                new PlayerPrivateView(
                        "game-1",
                        "P1",
                        1,
                        "MERLIN",
                        Camp.GOOD,
                        new PlayerPrivateKnowledge(List.of(), List.of()),
                        List.of()
                ),
                PlayerMemoryState.empty("game-1", "P1", "MERLIN", Camp.GOOD, Instant.parse("2026-03-24T00:00:00Z")),
                new AllowedActionSet("game-1", "P1", 1, EnumSet.of(PlayerActionType.TEAM_VOTE)),
                ruleSetDefinition,
                new SetupTemplate("classic-5p-v1", 5, true, List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")),
                "Classic five-player Avalon"
        );
    }
}
