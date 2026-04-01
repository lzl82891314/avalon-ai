package com.example.avalon.core.setup.service;

import com.example.avalon.core.common.exception.GameConfigurationException;
import com.example.avalon.core.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSetupValidationServiceTest {
    private final SetupValidationService service = new DefaultSetupValidationService();

    @Test
    void validatesClassicFivePlayerConfig() {
        service.validate(TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles());
    }

    @Test
    void rejectsInvalidPlayerCount() {
        var players = TestFixtures.classicPlayers().subList(0, 4);
        assertThrows(GameConfigurationException.class,
                () -> service.validate(TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), players, TestFixtures.classicRoles()));
    }

    @Test
    void allowsRepeatedBaseRoleIdsInSetupTemplate() {
        var ruleSet = new com.example.avalon.core.setup.model.RuleSetDefinition(
                "avalon-classic-6p-v2",
                "Avalon Classic 6 Players V2",
                "2.0.0",
                6,
                6,
                java.util.List.of(
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(1, 2),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(2, 3),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(3, 4),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(4, 3),
                        new com.example.avalon.core.setup.model.RoundTeamSizeRule(5, 4)),
                java.util.Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1),
                java.util.List.of("classic-6p-v2"),
                new com.example.avalon.core.setup.model.AssassinationRuleDefinition(true, "ASSASSIN", "MERLIN"),
                new com.example.avalon.core.setup.model.VisibilityPolicyDefinition(true),
                true
        );
        var template = new com.example.avalon.core.setup.model.SetupTemplate(
                "classic-6p-v2",
                6,
                true,
                java.util.List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")
        );
        var players = new java.util.ArrayList<>(TestFixtures.classicPlayers());
        players.add(new com.example.avalon.core.game.model.GamePlayer(
                "game-1",
                "P6",
                6,
                "P6",
                com.example.avalon.core.player.enums.PlayerControllerType.SCRIPTED,
                "{}",
                com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED
        ));

        service.validate(ruleSet, template, players, TestFixtures.classicRoles());
    }
}
