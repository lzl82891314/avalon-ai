package com.example.avalon.runtime.orchestrator;

import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.MemoryUpdate;
import com.example.avalon.core.player.memory.PlayerBeliefState;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.engine.ConfigDrivenGameRuleEngine;
import com.example.avalon.runtime.engine.RoleAssignmentService;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.SeededLeaderSelector;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    @Test
    void stepShouldMergeAcceptedMemoryUpdateIntoRuntimeState() {
        GameSessionService sessionService = new GameSessionService();
        PlayerControllerResolver resolver = new PlayerControllerResolver();
        resolver.registerFactory(PlayerControllerType.SCRIPTED, (state, player) -> context -> new PlayerActionResult(
                "先做一轮观察。",
                new PublicSpeechAction("先做一轮观察。"),
                null,
                new MemoryUpdate(
                        java.util.Map.of("P4", 0.7),
                        java.util.Map.of("P2", 0.3),
                        java.util.List.of("P4 首轮发言偏激进"),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.Map.of("P3", new PlayerBeliefState(0.8, 0.4, 0.6, 0.7)),
                        "CAUTIOUS",
                        "先观察 P4"
                ),
                java.util.Map.of()
        ));
        GameOrchestrator orchestrator = new GameOrchestrator(
                sessionService,
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                resolver
        );

        GameSetup setup = RuntimeTestFixtures.classicFivePlayerSetup(199L);
        GameRuntimeState state = orchestrator.createGame(setup);
        orchestrator.start(state.generatedGameId());

        orchestrator.step(state.generatedGameId());

        assertEquals(1L, assertInstanceOf(Number.class, state.memoryOf("P1").get("version")).longValue());
        assertEquals("CAUTIOUS", state.memoryOf("P1").get("strategyMode"));
        assertEquals("先观察 P4", state.memoryOf("P1").get("lastSummary"));
        assertEquals(0.7, ((Number) ((java.util.Map<?, ?>) state.memoryOf("P1").get("suspicionScores")).get("P4")).doubleValue());
        assertEquals(0.3, ((Number) ((java.util.Map<?, ?>) state.memoryOf("P1").get("trustScores")).get("P2")).doubleValue());
        assertEquals(
                0.8,
                ((Number) ((java.util.Map<?, ?>) ((java.util.Map<?, ?>) state.memoryOf("P1").get("beliefsByPlayerId")).get("P3")).get("firstOrderEvilScore")).doubleValue()
        );
    }
}
