package com.example.avalon.runtime.service;

import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        PlayerTurnContext leaderContext = builder.build(proposalState, proposalState.playerById("P1"));
        PlayerTurnContext nonLeaderContext = builder.build(proposalState, proposalState.playerById("P2"));

        assertEquals(Set.of(PlayerActionType.TEAM_PROPOSAL), leaderContext.allowedActions().allowedActionTypes());
        assertEquals(Set.of(), nonLeaderContext.allowedActions().allowedActionTypes());
    }
}
