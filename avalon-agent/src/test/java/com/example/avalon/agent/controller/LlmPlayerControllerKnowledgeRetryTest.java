package com.example.avalon.agent.controller;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
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
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPlayerControllerKnowledgeRetryTest {
    @Test
    void shouldRetryWhenCandidateOnlyKnowledgeIsAssertedAsFact() {
        AtomicInteger calls = new AtomicInteger();
        AgentGateway agentGateway = request -> {
            AgentTurnResult result = new AgentTurnResult();
            result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
            if (calls.incrementAndGet() == 1) {
                result.setPrivateThought("P5是梅林，P3是莫甘娜。");
                return result;
            }
            assertTrue(request.getPromptText().contains("候选身份说成了确定事实"));
            result.setPrivateThought("我怀疑 P5 更像梅林。");
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

        PlayerActionResult result = controller.act(percivalTeamVoteContext());

        assertEquals(2, result.rawMetadata().get("attempts"));
        Map<String, Object> inputContext = rawMap(result.rawMetadata().get("inputContext"));
        assertTrue(String.valueOf(inputContext.get("promptText")).contains("candidateRoleIds"));
        assertTrue(String.valueOf(inputContext.get("promptText")).contains("候选身份说成了确定事实"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rawMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }

    private PlayerTurnContext percivalTeamVoteContext() {
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
                "PERCIVAL",
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
                        "PERCIVAL",
                        Camp.GOOD,
                        new PlayerPrivateKnowledge(List.of(
                                new VisiblePlayerInfo("P3", 3, "Cara", null, Camp.GOOD, List.of("MERLIN", "MORGANA")),
                                new VisiblePlayerInfo("P5", 5, "Eva", null, Camp.GOOD, List.of("MERLIN", "MORGANA"))
                        ), List.of("You see Merlin and Morgana as candidates.")),
                        List.of()
                ),
                PlayerMemoryState.empty("game-1", "P1", "PERCIVAL", Camp.GOOD, Instant.parse("2026-03-24T00:00:00Z")),
                new AllowedActionSet("game-1", "P1", 1, EnumSet.of(PlayerActionType.TEAM_VOTE)),
                ruleSetDefinition,
                new SetupTemplate("classic-5p-v1", 5, true, List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")),
                "Classic five-player Avalon"
        );
    }
}
