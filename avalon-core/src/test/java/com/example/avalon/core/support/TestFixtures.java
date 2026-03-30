package com.example.avalon.core.support;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static RuleSetDefinition classicRuleSet() {
        return new RuleSetDefinition(
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
    }

    public static SetupTemplate classicSetupTemplate() {
        return new SetupTemplate(
                "classic-5p-v1",
                5,
                true,
                List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")
        );
    }

    public static List<RoleDefinition> classicRoles() {
        return List.of(
                new RoleDefinition(
                        "MERLIN",
                        "Merlin",
                        Camp.GOOD,
                        "Knows evil players.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_PLAYERS_BY_CAMP, Camp.EVIL, List.of(), List.of("MORDRED"))),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of("HIDDEN_HIGH_VALUE_TARGET")
                ),
                new RoleDefinition(
                        "PERCIVAL",
                        "Percival",
                        Camp.GOOD,
                        "Knows Merlin and Morgana candidates.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ROLE_AMBIGUITY, null, List.of("MERLIN", "MORGANA"), List.of())),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()
                ),
                new RoleDefinition(
                        "LOYAL_SERVANT",
                        "Loyal Servant",
                        Camp.GOOD,
                        "No special knowledge.",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()
                ),
                new RoleDefinition(
                        "MORGANA",
                        "Morgana",
                        Camp.EVIL,
                        "Looks like Merlin to Percival.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()
                ),
                new RoleDefinition(
                        "ASSASSIN",
                        "Assassin",
                        Camp.EVIL,
                        "Can assassinate Merlin.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of("ASSASSINATE"),
                        true,
                        true,
                        true,
                        true,
                        List.of()
                )
        );
    }

    public static List<GamePlayer> classicPlayers() {
        return List.of(
                new GamePlayer("game-1", "P1", 1, "P1", PlayerControllerType.SCRIPTED, "{}", PlayerConnectionState.CONNECTED),
                new GamePlayer("game-1", "P2", 2, "P2", PlayerControllerType.SCRIPTED, "{}", PlayerConnectionState.CONNECTED),
                new GamePlayer("game-1", "P3", 3, "P3", PlayerControllerType.SCRIPTED, "{}", PlayerConnectionState.CONNECTED),
                new GamePlayer("game-1", "P4", 4, "P4", PlayerControllerType.SCRIPTED, "{}", PlayerConnectionState.CONNECTED),
                new GamePlayer("game-1", "P5", 5, "P5", PlayerControllerType.SCRIPTED, "{}", PlayerConnectionState.CONNECTED)
        );
    }

    public static GameSession waitingSession() {
        Instant now = Instant.parse("2026-03-23T00:00:00Z");
        return GameSession.createWaiting("game-1", "avalon-classic-5p-v1", "1.0.0", "classic-5p-v1", 42L, now, now);
    }

    public static GameSession runningSession() {
        return waitingSession().startRunning(Instant.parse("2026-03-23T00:00:00Z"));
    }
}
