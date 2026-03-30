package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivateKnowledgeExpressionValidatorTest {
    private final PrivateKnowledgeExpressionValidator validator = new PrivateKnowledgeExpressionValidator();

    @Test
    void shouldRejectCertainAssertionAboutCandidateOnlyKnowledge() {
        AgentTurnResult result = new AgentTurnResult();
        result.setPrivateThought("P5是梅林，P3是莫甘娜。");
        result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");

        assertThrows(
                CandidateKnowledgeAssertionException.class,
                () -> validator.validate(percivalTeamVoteContext(), result)
        );
    }

    @Test
    void shouldAllowUncertainCandidateExpression() {
        AgentTurnResult result = new AgentTurnResult();
        result.setPrivateThought("我怀疑 P5 更像梅林。");
        result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
        AuditReason auditReason = new AuditReason();
        auditReason.setReasonSummary(List.of("我猜 P3 可能更像莫甘娜。"));
        result.setAuditReason(auditReason);

        assertDoesNotThrow(() -> validator.validate(percivalTeamVoteContext(), result));
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
