package com.example.avalon.runtime.recovery;

import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.store.AuditRecordStore;
import com.example.avalon.persistence.store.GameEventStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayQueryServiceTest {
    @Test
    void shouldProjectReplayStepsIntoHumanReadableFrames() {
        InMemoryGameEventStore eventStore = new InMemoryGameEventStore();
        eventStore.save(new GameEventRecord("g-1", "g", 1L, "GAME_CREATED", "ROLE_REVEAL", "SYSTEM", "SYSTEM", "{\"gameId\":\"g\"}", Instant.parse("2026-03-24T00:00:00Z")));
        eventStore.save(new GameEventRecord("g-2", "g", 2L, "GAME_STARTED", "DISCUSSION", "SYSTEM", "SYSTEM", "{\"leaderSeat\":1}", Instant.parse("2026-03-24T00:00:01Z")));
        eventStore.save(new GameEventRecord("g-3", "g", 3L, "TEAM_PROPOSED", "TEAM_PROPOSAL", "P1", "SYSTEM", "{\"playerIds\":[\"P1\",\"P2\"]}", Instant.parse("2026-03-24T00:00:02Z")));

        ReplayQueryService service = new ReplayQueryService(eventStore, new EmptyAuditRecordStore());
        List<ReplayProjectionStep> replay = service.replay("g");

        assertEquals(3, replay.size());
        assertEquals("GAME_STARTUP", replay.get(0).replayKind());
        assertEquals("游戏已创建", replay.get(0).summary());
        assertEquals("ROUND_OPENING", replay.get(1).replayKind());
        assertTrue(replay.get(1).summary().contains("首位队长"));
        assertEquals("TEAM_FORMED", replay.get(2).replayKind());
        assertNotEquals("TEAM_PROPOSED", replay.get(2).replayKind());
    }

    @Test
    void shouldProjectPausedFrames() {
        InMemoryGameEventStore eventStore = new InMemoryGameEventStore();
        eventStore.save(new GameEventRecord(
                "g-1",
                "g",
                1L,
                "GAME_PAUSED",
                "DISCUSSION",
                "P1",
                "SYSTEM",
                "{\"reason\":\"LLM_ACTION_FAILURE\",\"playerId\":\"P1\"}",
                Instant.parse("2026-03-24T00:00:03Z")));

        ReplayQueryService service = new ReplayQueryService(eventStore, new EmptyAuditRecordStore());
        List<ReplayProjectionStep> replay = service.replay("g");

        assertEquals(1, replay.size());
        assertEquals("RUN_PAUSED", replay.get(0).replayKind());
        assertTrue(replay.get(0).summary().contains("LLM_ACTION_FAILURE"));
        assertTrue(replay.get(0).summary().contains("P1"));
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

    private static final class EmptyAuditRecordStore implements AuditRecordStore {
        @Override
        public AuditRecord save(AuditRecord record) {
            return record;
        }

        @Override
        public List<AuditRecord> findByGameId(String gameId) {
            return List.of();
        }

        @Override
        public List<AuditRecord> findByGameIdAndPlayerId(String gameId, String playerId) {
            return List.of();
        }

        @Override
        public List<AuditRecord> findByGameIdAndEventSeqNo(String gameId, long eventSeqNo) {
            return List.of();
        }

        @Override
        public Optional<AuditRecord> findLatestByGameId(String gameId) {
            return Optional.empty();
        }
    }
}
