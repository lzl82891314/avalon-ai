package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.game.model.TeamProposalAction;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseParserTest {
    private final ResponseParser responseParser = new ResponseParser();

    @Test
    void shouldParseTeamProposalJsonIntoAction() {
        PlayerTurnContext context = teamProposalContext();
        AgentTurnResult result = new AgentTurnResult();
        result.setActionJson("""
                {"actionType":"TEAM_PROPOSAL","selectedPlayerIds":["P1","P2"]}
                """);

        TeamProposalAction action = (TeamProposalAction) responseParser.parse(context, result);

        assertEquals(List.of("P1", "P2"), action.selectedPlayerIds());
    }

    private PlayerTurnContext teamProposalContext() {
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
                GamePhase.TEAM_PROPOSAL.name(),
                "P1",
                1,
                "MERLIN",
                new PublicGameSnapshot(
                        "game-1",
                        GameStatus.RUNNING,
                        GamePhase.TEAM_PROPOSAL,
                        1,
                        0,
                        0,
                        0,
                        1,
                        List.of(),
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
                new AllowedActionSet("game-1", "P1", 1, EnumSet.of(PlayerActionType.TEAM_PROPOSAL)),
                ruleSetDefinition,
                new SetupTemplate("classic-5p-v1", 5, true, List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")),
                "Classic five-player Avalon"
        );
    }
}
