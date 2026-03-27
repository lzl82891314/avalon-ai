package com.example.avalon.testkit;

import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.runtime.orchestrator.GameRunResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassicFivePlayerRunToEndTest {
    @Test
    void scriptedClassicFivePlayerGameRunsToEnd() {
        GameRunResult result = ScriptedAvalonFixture.orchestrator()
                .runToEnd(ScriptedAvalonFixture.classicFivePlayerSetup(123456789L));

        assertTrue(result.state().status() == GameStatus.ENDED, "game should end");
        assertNotNull(result.state().winnerCamp(), "winner should be decided");
        assertFalse(result.events().isEmpty(), "events should be recorded");
        assertTrue(result.state().events().stream().anyMatch(event -> event.type().equals("GAME_ENDED")));
    }
}
