package com.example.avalon.runtime.orchestrator;

import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.service.SeededLeaderSelector;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameOrchestratorTest {
    @Test
    void startShouldPickInitialLeaderSeatFromSeed() {
        GameSetup setup = RuntimeTestFixtures.classicFivePlayerSetup(99L);
        GameOrchestrator orchestrator = new GameOrchestrator();

        GameRuntimeState created = orchestrator.createGame(setup);
        GameRuntimeState started = orchestrator.start(created.generatedGameId());

        int expectedLeaderSeat = SeededLeaderSelector.initialLeaderSeat(setup.players(), setup.seed());
        assertEquals(expectedLeaderSeat, started.currentLeaderSeat());
        GameEvent startEvent = started.events().stream()
                .filter(event -> "GAME_STARTED".equals(event.type()))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedLeaderSeat, startEvent.payload().get("leaderSeat"));
    }
}
