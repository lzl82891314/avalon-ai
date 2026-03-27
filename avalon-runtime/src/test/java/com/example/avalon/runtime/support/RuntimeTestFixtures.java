package com.example.avalon.runtime.support;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;

import java.util.List;
import java.util.Map;

public final class RuntimeTestFixtures {
    private RuntimeTestFixtures() {
    }

    public static GameSetup classicFivePlayerSetup(long seed) {
        return new GameSetup(
                "scripted-classic-5p-" + seed,
                "avalon-classic-5p-v1",
                classicRuleSet(),
                "classic-5p-v1",
                classicSetupTemplate(),
                seed,
                classicRoleDefinitions(),
                List.of(
                        new PlayerRegistration("P1", 1, "P1", PlayerControllerType.SCRIPTED),
                        new PlayerRegistration("P2", 2, "P2", PlayerControllerType.SCRIPTED),
                        new PlayerRegistration("P3", 3, "P3", PlayerControllerType.SCRIPTED),
                        new PlayerRegistration("P4", 4, "P4", PlayerControllerType.SCRIPTED),
                        new PlayerRegistration("P5", 5, "P5", PlayerControllerType.SCRIPTED))
        );
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

    public static Map<String, RoleDefinition> classicRoleDefinitions() {
        return Map.of(
                "MERLIN", new RoleDefinition(
                        "MERLIN",
                        "Merlin",
                        Camp.GOOD,
                        "Knows most evil players, but must avoid being identified by the assassin.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_PLAYERS_BY_CAMP, Camp.EVIL, List.of(), List.of("MORDRED"))),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of("HIDDEN_HIGH_VALUE_TARGET")),
                "PERCIVAL", new RoleDefinition(
                        "PERCIVAL",
                        "Percival",
                        Camp.GOOD,
                        "Sees Merlin or Morgana as a possible Merlin.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_PLAYERS_BY_ROLE, null, List.of("MERLIN", "MORGANA"), List.of())),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()),
                "LOYAL_SERVANT", new RoleDefinition(
                        "LOYAL_SERVANT",
                        "Loyal Servant",
                        Camp.GOOD,
                        "Ordinary loyal good player with no special knowledge.",
                        List.of(),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()),
                "MORGANA", new RoleDefinition(
                        "MORGANA",
                        "Morgana",
                        Camp.EVIL,
                        "Appears as Merlin to Percival.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_PLAYERS_BY_ROLE, null, List.of("MERLIN", "MORGANA"), List.of())),
                        List.of(),
                        true,
                        true,
                        true,
                        false,
                        List.of()),
                "ASSASSIN", new RoleDefinition(
                        "ASSASSIN",
                        "Assassin",
                        Camp.EVIL,
                        "Can assassinate Merlin at the end of the game if evil still has a chance.",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of("ASSASSINATE"),
                        true,
                        true,
                        true,
                        true,
                        List.of())
        );
    }
}
