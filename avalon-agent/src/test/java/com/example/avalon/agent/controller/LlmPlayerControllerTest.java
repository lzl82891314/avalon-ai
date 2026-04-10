package com.example.avalon.agent.controller;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.service.AgentTurnExecutionException;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.TurnAgent;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.player.controller.PlayerActionGenerationException;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPlayerControllerTest {
    @Test
    void shouldRetryOnceAndReturnParsedAction() {
        AtomicInteger calls = new AtomicInteger();
        AgentGateway agentGateway = request -> {
            AgentTurnResult result = new AgentTurnResult();
            if (calls.incrementAndGet() == 1) {
                result.setActionJson("{\"actionType\":\"TEAM_VOTE\"}");
                return result;
            }
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            return result;
        };
        LlmPlayerController controller = new LlmPlayerController(
                agentGateway,
                new AgentTurnRequestFactory(),
                new PromptBuilder(),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new PlayerAgentConfig()
        );

        PlayerActionResult result = controller.act(teamVoteContext());

        assertEquals("APPROVE", ((com.example.avalon.core.game.model.TeamVoteAction) result.action()).vote().name());
        assertEquals(2, result.rawMetadata().get("attempts"));
        assertEquals("legacy-single-shot", result.rawMetadata().get("policyId"));
        assertTrue(result.rawMetadata().containsKey("inputContext"));
        assertTrue(result.rawMetadata().containsKey("rawModelResponse"));
        assertTrue(result.rawMetadata().containsKey("validation"));
        assertTrue(result.rawMetadata().containsKey("executionTrace"));
        assertTrue(result.rawMetadata().containsKey("policySummary"));
    }

    @Test
    void shouldExposeGatewayDiagnosticsInFailureMetadata() {
        AgentGateway agentGateway = request -> {
            throw new OpenAiCompatibleResponseException(
                    "OpenAI-compatible assistant content was not valid JSON (shape=plain_text, bodyPreview=hi)",
                    null,
                    "minimax",
                    "minimax-m2.7",
                    "stop",
                    new OpenAiCompatibleMessageAnalysis(
                            true,
                            true,
                            "plain_text",
                            "hi",
                            "这里只返回了推理。",
                            null
                    )
            );
        };
        LlmPlayerController controller = new LlmPlayerController(
                agentGateway,
                new AgentTurnRequestFactory(),
                new PromptBuilder(),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new PlayerAgentConfig()
        );

        PlayerActionGenerationException error = assertThrows(
                PlayerActionGenerationException.class,
                () -> controller.act(teamVoteContext())
        );

        assertEquals("Agent turn validation failed after 2 attempts", error.getMessage());
        Map<String, Object> rawModelResponse = rawMap(error.rawMetadata().get("rawModelResponse"));
        Map<String, Object> validation = rawMap(error.rawMetadata().get("validation"));
        Map<String, Object> policySummary = rawMap(error.rawMetadata().get("policySummary"));
        List<Map<String, Object>> executionTrace = rawWarnings(error.rawMetadata().get("executionTrace"));
        assertEquals("plain_text", rawModelResponse.get("assistantContentShape"));
        assertEquals("hi", rawModelResponse.get("assistantContentPreview"));
        assertEquals("这里只返回了推理。", rawModelResponse.get("reasoningDetailsPreview"));
        assertEquals("plain_text", validation.get("assistantContentShape"));
        assertEquals("legacy-single-shot", error.rawMetadata().get("policyId"));
        assertEquals("legacy-single-shot", validation.get("policyId"));
        assertEquals("legacy-single-shot", policySummary.get("policyId"));
        assertEquals("FAILED", policySummary.get("status"));
        assertEquals("single-shot-failed", executionTrace.get(0).get("stageId"));
        assertEquals("FAILED", executionTrace.get(0).get("status"));
        assertEquals("ADMIN_ONLY", error.rawMetadata().get("auditVisibility"));
    }

    @Test
    void shouldCopyReasoningDiagnosticsFromSuccessfulMetadata() {
        AgentGateway agentGateway = request -> {
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            RawCompletionMetadata metadata = new RawCompletionMetadata();
            metadata.setProvider("minimax");
            metadata.setModelName("minimax-m2.7");
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("assistantContentShape", "json_object");
            attributes.put("assistantContentPreview", "{\"action\":{\"actionType\":\"TEAM_VOTE\"}}");
            attributes.put("reasoningDetailsPreview", "先过一轮投票信息。");
            metadata.setAttributes(attributes);
            result.setModelMetadata(metadata);
            return result;
        };
        LlmPlayerController controller = new LlmPlayerController(
                agentGateway,
                new AgentTurnRequestFactory(),
                new PromptBuilder(),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new PlayerAgentConfig()
        );

        PlayerActionResult result = controller.act(teamVoteContext());
        Map<String, Object> rawModelResponse = rawMap(result.rawMetadata().get("rawModelResponse"));

        assertEquals("json_object", rawModelResponse.get("assistantContentShape"));
        assertEquals("先过一轮投票信息。", rawModelResponse.get("reasoningDetailsPreview"));
    }

    @Test
    void shouldExposeOptionalSectionWarningsInSuccessfulValidationMetadata() {
        AgentGateway agentGateway = request -> {
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            RawCompletionMetadata metadata = new RawCompletionMetadata();
            metadata.setAttributes(new LinkedHashMap<>(Map.of(
                    "optionalSectionWarnings",
                    List.of(Map.of(
                            "field", "memoryUpdate",
                            "reason", "expected_json_object",
                            "contentPreview", "\"oops\""
                    ))
            )));
            result.setModelMetadata(metadata);
            return result;
        };
        LlmPlayerController controller = new LlmPlayerController(
                agentGateway,
                new AgentTurnRequestFactory(),
                new PromptBuilder(),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new PlayerAgentConfig()
        );

        PlayerActionResult result = controller.act(teamVoteContext());
        Map<String, Object> validation = rawMap(result.rawMetadata().get("validation"));
        List<Map<String, Object>> warnings = rawWarnings(validation.get("optionalSectionWarnings"));

        assertEquals(1, warnings.size());
        assertEquals("memoryUpdate", warnings.get(0).get("field"));
        assertEquals("expected_json_object", warnings.get(0).get("reason"));
        assertEquals("\"oops\"", warnings.get(0).get("contentPreview"));
    }

    @Test
    void shouldApplyCorrectiveRetryPromptAndRaiseTokenBudgetAfterTruncatedJsonFailure() {
        AtomicInteger calls = new AtomicInteger();
        AgentGateway agentGateway = request -> {
            if (calls.incrementAndGet() == 1) {
                throw new OpenAiCompatibleResponseException(
                        "OpenAI-compatible assistant content looked like truncated JSON (shape=truncated_json_candidate, bodyPreview={)",
                        null,
                        "openai",
                        "openai/gpt-5.4",
                        "length",
                        new OpenAiCompatibleMessageAnalysis(
                                true,
                                false,
                                "truncated_json_candidate",
                                "{\"publicSpeech\":\"我是1号\"",
                                null,
                                null
                        )
                );
            }
            assertEquals(960, request.getMaxTokens());
            assertTrue(request.getPromptText().contains("优先先写 action"));
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            return result;
        };
        PlayerAgentConfig config = new PlayerAgentConfig();
        config.setOutputSchemaVersion("v1");
        com.example.avalon.agent.model.ModelProfile modelProfile = new com.example.avalon.agent.model.ModelProfile();
        modelProfile.setMaxTokens(320);
        config.setModelProfile(modelProfile);
        LlmPlayerController controller = new LlmPlayerController(
                agentGateway,
                new AgentTurnRequestFactory(),
                new PromptBuilder(),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                config
        );

        PlayerActionResult result = controller.act(teamVoteContext());

        assertEquals(2, result.rawMetadata().get("attempts"));
        Map<String, Object> inputContext = rawMap(result.rawMetadata().get("inputContext"));
        assertEquals(960, inputContext.get("maxTokens"));
        assertTrue(String.valueOf(inputContext.get("promptText")).contains("优先先写 action"));
    }

    @Test
    void shouldPreserveExecutionTraceAndPolicySummaryFromTurnAgentFailure() {
        AgentTurnRequestFactory requestFactory = new AgentTurnRequestFactory();
        PlayerTurnContext context = teamVoteContext();
        com.example.avalon.agent.model.AgentTurnRequest request = requestFactory.create(context, new PlayerAgentConfig());
        TurnAgent turnAgent = (turnContext, config) -> {
            throw new AgentTurnExecutionException(
                    "tom-v1 belief stage failed",
                    request,
                    null,
                    1,
                    List.of(Map.of(
                            "stageId", "belief-stage",
                            "status", "FAILED"
                    )),
                    Map.of(
                            "policyId", "tom-v1",
                            "failedStage", "belief-stage"
                    ),
                    new IllegalStateException("boom")
            );
        };
        LlmPlayerController controller = new LlmPlayerController(
                turnAgent,
                requestFactory,
                new PromptBuilder(),
                new ResponseParser(),
                new ValidationRetryPolicy(),
                new PlayerAgentConfig()
        );

        PlayerActionGenerationException error = assertThrows(
                PlayerActionGenerationException.class,
                () -> controller.act(context)
        );

        List<Map<String, Object>> executionTrace = rawWarnings(error.rawMetadata().get("executionTrace"));
        Map<String, Object> policySummary = rawMap(error.rawMetadata().get("policySummary"));
        assertEquals("belief-stage", executionTrace.get(0).get("stageId"));
        assertEquals("FAILED", executionTrace.get(0).get("status"));
        assertEquals("tom-v1", policySummary.get("policyId"));
        assertEquals("belief-stage", policySummary.get("failedStage"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rawWarnings(Object value) {
        return assertInstanceOf(List.class, value);
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
                                new PublicPlayerSummary("game-1", "P2", 2, "P2", PlayerControllerType.SCRIPTED, PlayerConnectionState.DISCONNECTED)
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
