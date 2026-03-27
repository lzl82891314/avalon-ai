package com.example.avalon.core.role.service;

import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVisibilityServiceTest {
    private final RoleAssignmentService assignmentService = new DeterministicRoleAssignmentService();
    private final VisibilityService visibilityService = new DefaultVisibilityService();

    @Test
    void merlinSeesEvilPlayersAndPercivalSeesCandidates() {
        List<RoleAssignment> assignments = assignmentService.assignRoles(
                TestFixtures.waitingSession(),
                TestFixtures.classicRuleSet(),
                TestFixtures.classicSetupTemplate(),
                TestFixtures.classicPlayers(),
                TestFixtures.classicRoles(),
                7L
        );
        GameRuleContext context = new GameRuleContext(
                TestFixtures.runningSession(),
                TestFixtures.classicPlayers(),
                assignments,
                TestFixtures.classicRuleSet(),
                TestFixtures.classicSetupTemplate(),
                TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role))
        );

        String merlinPlayerId = assignments.stream().filter(assignment -> "MERLIN".equals(assignment.roleId())).findFirst().orElseThrow().playerId();
        String percivalPlayerId = assignments.stream().filter(assignment -> "PERCIVAL".equals(assignment.roleId())).findFirst().orElseThrow().playerId();
        String loyalPlayerId = assignments.stream().filter(assignment -> "LOYAL_SERVANT".equals(assignment.roleId())).findFirst().orElseThrow().playerId();

        var merlinView = visibilityService.buildPrivateView(context, merlinPlayerId);
        var merlinVisibleRoles = merlinView.knowledge().visiblePlayers().stream().map(info -> info.exactRoleId()).toList();
        assertTrue(merlinVisibleRoles.contains("MORGANA"));
        assertTrue(merlinVisibleRoles.contains("ASSASSIN"));

        var percivalView = visibilityService.buildPrivateView(context, percivalPlayerId);
        var percivalCandidates = percivalView.knowledge().visiblePlayers().stream()
                .flatMap(info -> info.candidateRoleIds().stream())
                .toList();
        assertTrue(percivalCandidates.contains("MERLIN"));
        assertTrue(percivalCandidates.contains("MORGANA"));

        var loyalView = visibilityService.buildPrivateView(context, loyalPlayerId);
        assertEquals(0, loyalView.knowledge().visiblePlayers().size());
    }
}

