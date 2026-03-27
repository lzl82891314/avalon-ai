package com.example.avalon.runtime.engine;

import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityServiceTest {
    @Test
    void shouldBuildRoleSpecificPrivateViewFromCoreRules() {
        GameOrchestrator orchestrator = new GameOrchestrator();
        GameSetup setup = RuntimeTestFixtures.classicFivePlayerSetup(7L);
        orchestrator.createGame(setup);
        GameRuntimeState state = orchestrator.start(setup.gameId());
        VisibilityService visibilityService = new VisibilityService();

        RoleAssignment merlin = assignmentByRole(state, "MERLIN");
        RoleAssignment percival = assignmentByRole(state, "PERCIVAL");
        RoleAssignment loyalServant = assignmentByRole(state, "LOYAL_SERVANT");
        RoleAssignment assassin = assignmentByRole(state, "ASSASSIN");

        PlayerPrivateView merlinView = visibilityService.buildPrivateView(state, merlin);
        PlayerPrivateView percivalView = visibilityService.buildPrivateView(state, percival);
        PlayerPrivateView loyalServantView = visibilityService.buildPrivateView(state, loyalServant);
        PlayerPrivateView assassinView = visibilityService.buildPrivateView(state, assassin);

        List<String> merlinVisibleRoles = merlinView.knowledge().visiblePlayers().stream().map(player -> player.exactRoleId()).toList();
        assertEquals(2, merlinVisibleRoles.size());
        assertTrue(merlinVisibleRoles.contains("MORGANA"));
        assertTrue(merlinVisibleRoles.contains("ASSASSIN"));

        assertEquals(2, percivalView.knowledge().visiblePlayers().size());
        List<String> percivalCandidates = percivalView.knowledge().visiblePlayers().stream()
                .flatMap(player -> player.candidateRoleIds().stream())
                .distinct()
                .toList();
        assertEquals(2, percivalCandidates.size());
        assertTrue(percivalCandidates.contains("MERLIN"));
        assertTrue(percivalCandidates.contains("MORGANA"));

        assertTrue(loyalServantView.knowledge().visiblePlayers().isEmpty());

        List<String> assassinVisibleRoles = assassinView.knowledge().visiblePlayers().stream().map(player -> player.exactRoleId()).toList();
        assertEquals(List.of("MORGANA"), assassinVisibleRoles);
    }

    private RoleAssignment assignmentByRole(GameRuntimeState state, String roleId) {
        return state.roleAssignments().values().stream()
                .filter(assignment -> roleId.equals(assignment.roleId()))
                .findFirst()
                .orElseThrow();
    }
}
