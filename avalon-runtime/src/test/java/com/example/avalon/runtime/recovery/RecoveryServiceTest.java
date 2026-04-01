package com.example.avalon.runtime.recovery;

import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;
import com.example.avalon.persistence.store.GameEventStore;
import com.example.avalon.persistence.store.GameSnapshotStore;
import com.example.avalon.persistence.store.PlayerMemorySnapshotStore;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.persistence.RuntimePersistenceService;
import com.example.avalon.runtime.persistence.RuntimeStateCodec;
import com.example.avalon.runtime.support.RuntimeTestFixtures;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.engine.ConfigDrivenGameRuleEngine;
import com.example.avalon.runtime.engine.RoleAssignmentService;
import com.example.avalon.runtime.engine.VisibilityService;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecoveryServiceTest {
    @Test
    void shouldRecoverLatestSnapshotAndPlayerMemories() {
        GameOrchestrator orchestrator = new GameOrchestrator();
        GameRuntimeState state = orchestrator.runToEnd(RuntimeTestFixtures.classicFivePlayerSetup(99L)).state();
        state.memoryOf("P1").put("trust", "medium");

        InMemoryGameEventStore eventStore = new InMemoryGameEventStore();
        InMemoryGameSnapshotStore snapshotStore = new InMemoryGameSnapshotStore();
        InMemoryPlayerMemorySnapshotStore memoryStore = new InMemoryPlayerMemorySnapshotStore();
        RuntimeStateCodec codec = new RuntimeStateCodec();
        RuntimePersistenceService persistenceService = new RuntimePersistenceService(eventStore, snapshotStore, memoryStore, codec, 1);
        persistenceService.persist(state);

        RecoveryService recoveryService = new RecoveryService(snapshotStore, eventStore, memoryStore, codec);
        RecoveryResult recovered = recoveryService.recover(state.generatedGameId());

        assertEquals(state.generatedGameId(), recovered.state().generatedGameId());
        assertEquals(state.status(), recovered.state().status());
        assertEquals(state.phase(), recovered.state().phase());
        assertFalse(recovered.restoredMemorySnapshots().isEmpty());
        assertEquals("medium", recovered.state().memoryOf("P1").get("trust"));
    }

    @Test
    void shouldReplayEventsAfterSnapshotToRestoreLiveState() throws Exception {
        GameSetup setup = RuntimeTestFixtures.classicFivePlayerSetup(99L);
        GameOrchestrator setupOrchestrator = new GameOrchestrator();
        GameRuntimeState snapshotState = setupOrchestrator.createGame(setup);

        InMemoryGameEventStore eventStore = new InMemoryGameEventStore();
        InMemoryGameSnapshotStore snapshotStore = new InMemoryGameSnapshotStore();
        InMemoryPlayerMemorySnapshotStore memoryStore = new InMemoryPlayerMemorySnapshotStore();
        RuntimeStateCodec codec = new RuntimeStateCodec();
        RuntimePersistenceService persistenceService = new RuntimePersistenceService(eventStore, snapshotStore, memoryStore, codec, 50);
        persistenceService.persist(snapshotState);

        GameOrchestrator finalOrchestrator = new GameOrchestrator();
        GameRuntimeState finalState = finalOrchestrator.runToEnd(setup).state();
        persistTailEvents(eventStore, finalState, snapshotState.events().size());

        RecoveryService recoveryService = new RecoveryService(snapshotStore, eventStore, memoryStore, codec);
        RecoveryResult recovered = recoveryService.recover(finalState.generatedGameId());

        assertEquals(finalState.generatedGameId(), recovered.state().generatedGameId());
        assertEquals(finalState.status(), recovered.state().status());
        assertEquals(finalState.phase(), recovered.state().phase());
        assertEquals(finalState.roundNo(), recovered.state().roundNo());
        assertEquals(finalState.currentLeaderSeat(), recovered.state().currentLeaderSeat());
        assertEquals(finalState.winnerCamp(), recovered.state().winnerCamp());
        assertEquals(finalState.approvedMissionRounds(), recovered.state().approvedMissionRounds());
        assertEquals(finalState.failedMissionRounds(), recovered.state().failedMissionRounds());
        assertEquals(finalState.events().size(), recovered.state().events().size());
        assertFalse(recovered.eventsAfterSnapshot().isEmpty());
    }

    @Test
    void shouldRecoverThenContinueRunningToEnd() {
        GameSetup setup = RuntimeTestFixtures.classicFivePlayerSetup(123L);
        GameOrchestrator baselineOrchestrator = new GameOrchestrator();
        GameRuntimeState expectedFinalState = baselineOrchestrator.runToEnd(setup).state();

        InMemoryGameEventStore eventStore = new InMemoryGameEventStore();
        InMemoryGameSnapshotStore snapshotStore = new InMemoryGameSnapshotStore();
        InMemoryPlayerMemorySnapshotStore memoryStore = new InMemoryPlayerMemorySnapshotStore();
        RuntimeStateCodec codec = new RuntimeStateCodec();
        RuntimePersistenceService persistenceService = new RuntimePersistenceService(eventStore, snapshotStore, memoryStore, codec, 100);

        GameSessionService liveSessionService = new GameSessionService();
        GameOrchestrator liveOrchestrator = new GameOrchestrator(
                liveSessionService,
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                new PlayerControllerResolver());
        GameRuntimeState liveState = liveOrchestrator.createGame(setup);
        persistenceService.persist(liveState);

        String gameId = liveState.generatedGameId();
        liveOrchestrator.start(gameId);
        liveOrchestrator.step(gameId);
        liveOrchestrator.step(gameId);
        persistenceService.persist(liveSessionService.require(gameId));

        GameSessionService recoveredSessionService = new GameSessionService();
        GameOrchestrator recoveredOrchestrator = new GameOrchestrator(
                recoveredSessionService,
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                new PlayerControllerResolver());
        RecoveryService recoveryService = new RecoveryService(snapshotStore, eventStore, memoryStore, codec);
        RecoveryResult recovered = recoveryService.recover(gameId);
        recoveredSessionService.save(recovered.state());

        GameRuntimeState resumedFinalState = recoveredOrchestrator.runToEnd(gameId).state();

        assertEquals(expectedFinalState.status(), resumedFinalState.status());
        assertEquals(expectedFinalState.phase(), resumedFinalState.phase());
        assertEquals(expectedFinalState.winnerCamp(), resumedFinalState.winnerCamp());
        assertEquals(expectedFinalState.roundNo(), resumedFinalState.roundNo());
        assertEquals(expectedFinalState.currentLeaderSeat(), resumedFinalState.currentLeaderSeat());
        assertEquals(expectedFinalState.approvedMissionRounds(), resumedFinalState.approvedMissionRounds());
        assertEquals(expectedFinalState.failedMissionRounds(), resumedFinalState.failedMissionRounds());
        assertEquals(expectedFinalState.events().size(), resumedFinalState.events().size());
        assertFalse(recovered.eventsAfterSnapshot().isEmpty());
    }

    @Test
    void shouldRecoverSevenPlayerGameThenContinueRunningToEnd() {
        GameSetup setup = RuntimeTestFixtures.classicSetup(7, 456L);
        GameOrchestrator baselineOrchestrator = new GameOrchestrator();
        GameRuntimeState expectedFinalState = baselineOrchestrator.runToEnd(setup).state();

        InMemoryGameEventStore eventStore = new InMemoryGameEventStore();
        InMemoryGameSnapshotStore snapshotStore = new InMemoryGameSnapshotStore();
        InMemoryPlayerMemorySnapshotStore memoryStore = new InMemoryPlayerMemorySnapshotStore();
        RuntimeStateCodec codec = new RuntimeStateCodec();
        RuntimePersistenceService persistenceService = new RuntimePersistenceService(eventStore, snapshotStore, memoryStore, codec, 100);

        GameSessionService liveSessionService = new GameSessionService();
        GameOrchestrator liveOrchestrator = new GameOrchestrator(
                liveSessionService,
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                new PlayerControllerResolver());
        GameRuntimeState liveState = liveOrchestrator.createGame(setup);
        persistenceService.persist(liveState);

        String gameId = liveState.generatedGameId();
        liveOrchestrator.start(gameId);
        liveOrchestrator.step(gameId);
        liveOrchestrator.step(gameId);
        persistenceService.persist(liveSessionService.require(gameId));

        GameSessionService recoveredSessionService = new GameSessionService();
        GameOrchestrator recoveredOrchestrator = new GameOrchestrator(
                recoveredSessionService,
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                new PlayerControllerResolver());
        RecoveryService recoveryService = new RecoveryService(snapshotStore, eventStore, memoryStore, codec);
        RecoveryResult recovered = recoveryService.recover(gameId);
        recoveredSessionService.save(recovered.state());

        GameRuntimeState resumedFinalState = recoveredOrchestrator.runToEnd(gameId).state();

        assertEquals(expectedFinalState.status(), resumedFinalState.status());
        assertEquals(expectedFinalState.phase(), resumedFinalState.phase());
        assertEquals(expectedFinalState.winnerCamp(), resumedFinalState.winnerCamp());
        assertEquals(expectedFinalState.roundNo(), resumedFinalState.roundNo());
        assertEquals(expectedFinalState.currentLeaderSeat(), resumedFinalState.currentLeaderSeat());
        assertEquals(expectedFinalState.approvedMissionRounds(), resumedFinalState.approvedMissionRounds());
        assertEquals(expectedFinalState.failedMissionRounds(), resumedFinalState.failedMissionRounds());
        assertEquals(expectedFinalState.events().size(), resumedFinalState.events().size());
        assertFalse(recovered.eventsAfterSnapshot().isEmpty());
    }

    private static final class InMemoryGameEventStore implements GameEventStore {
        private final List<GameEventRecord> records = new ArrayList<>();

        @Override
        public GameEventRecord save(GameEventRecord record) {
            records.removeIf(existing -> existing.eventId().equals(record.eventId()));
            records.add(record);
            records.sort(Comparator.comparing(GameEventRecord::seqNo));
            return record;
        }

        @Override
        public List<GameEventRecord> findByGameId(String gameId) {
            return records.stream().filter(record -> record.gameId().equals(gameId)).toList();
        }

        @Override
        public List<GameEventRecord> findByGameIdAfterSeqNo(String gameId, long seqNo) {
            return records.stream().filter(record -> record.gameId().equals(gameId) && record.seqNo() > seqNo).toList();
        }

        @Override
        public List<GameEventRecord> findByGameIdAndType(String gameId, String type) {
            return records.stream().filter(record -> record.gameId().equals(gameId) && record.type().equals(type)).toList();
        }

        @Override
        public List<GameEventRecord> findByGameIdAndActorPlayerId(String gameId, String actorPlayerId) {
            return records.stream().filter(record -> record.gameId().equals(gameId) && actorPlayerId.equals(record.actorPlayerId())).toList();
        }

        @Override
        public Optional<GameEventRecord> findLatestByGameId(String gameId) {
            return findByGameId(gameId).stream().max(Comparator.comparing(GameEventRecord::seqNo));
        }

        @Override
        public Optional<GameEventRecord> findLatestAtOrBefore(String gameId, long seqNo) {
            return findByGameId(gameId).stream().filter(record -> record.seqNo() <= seqNo).max(Comparator.comparing(GameEventRecord::seqNo));
        }

        @Override
        public long countByGameId(String gameId) {
            return findByGameId(gameId).size();
        }
    }

    private static final class InMemoryGameSnapshotStore implements GameSnapshotStore {
        private final List<GameSnapshotRecord> records = new ArrayList<>();

        @Override
        public GameSnapshotRecord save(GameSnapshotRecord record) {
            records.removeIf(existing -> existing.snapshotId().equals(record.snapshotId()));
            records.add(record);
            return record;
        }

        @Override
        public List<GameSnapshotRecord> findByGameId(String gameId) {
            return records.stream().filter(record -> record.gameId().equals(gameId)).toList();
        }

        @Override
        public Optional<GameSnapshotRecord> findLatestByGameId(String gameId) {
            return findByGameId(gameId).stream().max(Comparator.comparing(GameSnapshotRecord::basedOnEventSeqNo));
        }

        @Override
        public Optional<GameSnapshotRecord> findLatestAtOrBefore(String gameId, long basedOnEventSeqNo) {
            return findByGameId(gameId).stream()
                    .filter(record -> record.basedOnEventSeqNo() <= basedOnEventSeqNo)
                    .max(Comparator.comparing(GameSnapshotRecord::basedOnEventSeqNo));
        }
    }

    private static final class InMemoryPlayerMemorySnapshotStore implements PlayerMemorySnapshotStore {
        private final List<PlayerMemorySnapshotRecord> records = new ArrayList<>();

        @Override
        public PlayerMemorySnapshotRecord save(PlayerMemorySnapshotRecord record) {
            records.removeIf(existing -> existing.snapshotId().equals(record.snapshotId()));
            records.add(record);
            return record;
        }

        @Override
        public List<PlayerMemorySnapshotRecord> findByGameId(String gameId) {
            return records.stream().filter(record -> record.gameId().equals(gameId)).toList();
        }

        @Override
        public List<PlayerMemorySnapshotRecord> findByGameIdAndPlayerId(String gameId, String playerId) {
            return records.stream().filter(record -> record.gameId().equals(gameId) && record.playerId().equals(playerId)).toList();
        }

        @Override
        public Optional<PlayerMemorySnapshotRecord> findLatestByGameIdAndPlayerId(String gameId, String playerId) {
            return findByGameIdAndPlayerId(gameId, playerId).stream()
                    .max(Comparator.comparing(PlayerMemorySnapshotRecord::basedOnEventSeqNo));
        }

        @Override
        public Optional<PlayerMemorySnapshotRecord> findLatestAtOrBefore(String gameId, String playerId, long basedOnEventSeqNo) {
            return findByGameIdAndPlayerId(gameId, playerId).stream()
                    .filter(record -> record.basedOnEventSeqNo() <= basedOnEventSeqNo)
                    .max(Comparator.comparing(PlayerMemorySnapshotRecord::basedOnEventSeqNo));
        }
    }

    private void persistTailEvents(InMemoryGameEventStore eventStore, GameRuntimeState finalState, int snapshotSeqNo) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        for (GameEvent event : finalState.events()) {
            if (event.seqNo() > snapshotSeqNo) {
                eventStore.save(new GameEventRecord(
                        finalState.generatedGameId() + "-" + event.seqNo(),
                        finalState.generatedGameId(),
                        event.seqNo(),
                        event.type(),
                        event.phase().name(),
                        event.actorId(),
                        "SYSTEM",
                        objectMapper.writeValueAsString(new LinkedHashMap<>(event.payload())),
                        event.createdAt()));
            }
        }
    }
}
