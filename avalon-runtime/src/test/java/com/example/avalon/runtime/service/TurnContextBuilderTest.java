package com.example.avalon.runtime.service;

import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.memory.PlayerBeliefState;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnContextBuilderTest {
    @Test
    void shouldUseCoreAllowedActionsForCurrentPlayer() {
        GameOrchestrator orchestrator = new GameOrchestrator();
        GameRuntimeState state = orchestrator.createGame(RuntimeTestFixtures.classicFivePlayerSetup(11L));
        String gameId = state.generatedGameId();

        orchestrator.start(gameId);
        for (int step = 0; step < 5; step++) {
            orchestrator.step(gameId);
        }

        GameRuntimeState proposalState = state;
        TurnContextBuilder builder = new TurnContextBuilder(new VisibilityService());
        var leader = proposalState.playerBySeat(proposalState.currentLeaderSeat());
        var nonLeader = proposalState.players().stream()
                .filter(player -> player.seatNo() != proposalState.currentLeaderSeat())
                .findFirst()
                .orElseThrow();
        PlayerTurnContext leaderContext = builder.build(proposalState, leader);
        PlayerTurnContext nonLeaderContext = builder.build(proposalState, nonLeader);

        assertEquals(Set.of(PlayerActionType.TEAM_PROPOSAL), leaderContext.allowedActions().allowedActionTypes());
        assertEquals(Set.of(), nonLeaderContext.allowedActions().allowedActionTypes());
    }

    @Test
    void shouldInjectStoredPlayerMemoryInsteadOfAlwaysUsingEmptyMemory() {
        GameOrchestrator orchestrator = new GameOrchestrator();
        GameRuntimeState state = orchestrator.createGame(RuntimeTestFixtures.classicFivePlayerSetup(12L));
        orchestrator.start(state.generatedGameId());
        state.memoryOf("P1").put("version", 2);
        state.memoryOf("P1").put("suspicionScores", java.util.Map.of("P4", 0.8));
        state.memoryOf("P1").put("trustScores", java.util.Map.of("P2", 0.4));
        state.memoryOf("P1").put("observations", java.util.List.of("P4 在讨论阶段发言很重"));
        state.memoryOf("P1").put("beliefsByPlayerId", java.util.Map.of(
                "P4", new PlayerBeliefState(0.9, 0.5, 0.7, 0.8)
        ));
        state.memoryOf("P1").put("strategyMode", "CAUTIOUS");
        state.memoryOf("P1").put("lastSummary", "先观察 P4");

        TurnContextBuilder builder = new TurnContextBuilder(new VisibilityService());
        PlayerTurnContext leaderContext = builder.build(state, state.playerById("P1"));
        PlayerTurnContext otherContext = builder.build(state, state.playerById("P2"));

        assertEquals(2L, leaderContext.memoryState().version());
        assertEquals(0.8, leaderContext.memoryState().suspicionScores().get("P4"));
        assertEquals(0.4, leaderContext.memoryState().trustScores().get("P2"));
        assertEquals(0.9, leaderContext.memoryState().beliefsByPlayerId().get("P4").firstOrderEvilScore());
        assertEquals("CAUTIOUS", leaderContext.memoryState().strategyMode());
        assertEquals("先观察 P4", leaderContext.memoryState().lastSummary());
        assertTrue(otherContext.memoryState().suspicionScores().isEmpty());
        assertTrue(otherContext.memoryState().beliefsByPlayerId().isEmpty());
        assertEquals(0L, otherContext.memoryState().version());
    }
}
