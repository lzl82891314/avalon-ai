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
}

