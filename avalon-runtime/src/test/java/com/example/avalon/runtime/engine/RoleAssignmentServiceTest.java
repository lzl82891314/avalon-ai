package com.example.avalon.runtime.engine;

import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.service.RuntimeCoreContextFactory;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleAssignmentServiceTest {
    @Test
    void shouldMatchCoreDeterministicAssignmentForSameSetup() {
        GameSetup setup = RuntimeTestFixtures.classicFivePlayerSetup(42L);
        RuntimeCoreContextFactory contextFactory = new RuntimeCoreContextFactory();

        RoleAssignmentService runtimeService = new RoleAssignmentService();
        com.example.avalon.core.role.service.RoleAssignmentService coreService =
                new com.example.avalon.core.role.service.DeterministicRoleAssignmentService();

        Map<String, String> runtimeRoles = runtimeService.assignRoles(setup).stream()
                .collect(Collectors.toMap(RoleAssignment::playerId, RoleAssignment::roleId));
        Map<String, String> coreRoles = coreService.assignRoles(
                        contextFactory.toWaitingSession(setup, Instant.parse("2026-03-24T00:00:00Z")),
                        setup.ruleSetDefinition(),
                        setup.setupTemplate(),
                        contextFactory.toGamePlayers(setup),
                        setup.activeRoleDefinitions(),
                        setup.seed())
                .stream()
                .collect(Collectors.toMap(RoleAssignment::playerId, RoleAssignment::roleId));

        assertEquals(coreRoles, runtimeRoles);
    }
}
