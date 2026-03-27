package com.example.avalon.runtime.persistence;

import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeStateCodecTest {
    @Test
    void shouldRoundTripRuntimeState() {
        GameOrchestrator orchestrator = new GameOrchestrator();
        GameRuntimeState state = orchestrator.runToEnd(RuntimeTestFixtures.classicFivePlayerSetup(7L)).state();

        RuntimeStateCodec codec = new RuntimeStateCodec();
        String json = codec.serialize(state);
        GameRuntimeState restored = codec.deserialize(json);

        assertEquals(state.generatedGameId(), restored.generatedGameId());
        assertEquals(state.status(), restored.status());
        assertEquals(state.phase(), restored.phase());
        assertEquals(state.events().size(), restored.events().size());
        assertEquals(state.winnerCamp(), restored.winnerCamp());
    }
}
