package com.example.avalon.persistence.mapper;

import com.example.avalon.persistence.entity.AuditRecordEntity;
import com.example.avalon.persistence.entity.GameEventEntity;
import com.example.avalon.persistence.entity.GameSnapshotEntity;
import com.example.avalon.persistence.entity.ModelProfileEntity;
import com.example.avalon.persistence.entity.PlayerMemorySnapshotEntity;
import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.model.ModelProfileRecord;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PersistenceEntityMapperTest {
    @Test
    void gameEventRoundTripPreservesFields() {
        Instant now = Instant.parse("2026-03-23T08:00:00Z");
        GameEventRecord record = new GameEventRecord("e1", "g1", 12L, "PLAYER_ACTION", "DISCUSSION", "p1", "PUBLIC", "{\"a\":1}", now);

        GameEventEntity entity = PersistenceEntityMapper.toEntity(record);
        GameEventRecord mapped = PersistenceEntityMapper.toRecord(entity);

        assertEquals(record, mapped);
        assertEquals("PUBLIC", entity.getVisibility());
    }

    @Test
    void snapshotRoundTripPreservesFields() {
        Instant now = Instant.parse("2026-03-23T08:05:00Z");
        GameSnapshotRecord record = new GameSnapshotRecord("s1", "g1", 9L, 2, "MISSION_RESOLUTION", "{\"phase\":\"MISSION_RESOLUTION\"}", now);

        GameSnapshotEntity entity = PersistenceEntityMapper.toEntity(record);
        GameSnapshotRecord mapped = PersistenceEntityMapper.toRecord(entity);

        assertEquals(record, mapped);
        assertNotNull(entity.getStateJson());
    }

    @Test
    void memorySnapshotRoundTripPreservesFields() {
        Instant now = Instant.parse("2026-03-23T08:10:00Z");
        PlayerMemorySnapshotRecord record = new PlayerMemorySnapshotRecord("m1", "g1", "p1", 11L, "{\"trust\":{}}", now);

        PlayerMemorySnapshotEntity entity = PersistenceEntityMapper.toEntity(record);
        PlayerMemorySnapshotRecord mapped = PersistenceEntityMapper.toRecord(entity);

        assertEquals(record, mapped);
    }

    @Test
    void auditRoundTripPreservesFields() {
        Instant now = Instant.parse("2026-03-23T08:15:00Z");
        AuditRecord record = new AuditRecord("a1", "g1", 7L, "p1", "ADMIN_ONLY", "{\"ctx\":true}", "hash", "{\"raw\":1}", "{\"parsed\":1}", "{\"audit\":1}", "{\"valid\":true}", null, now);

        AuditRecordEntity entity = PersistenceEntityMapper.toEntity(record);
        AuditRecordRecordAssert.assertRoundTrip(record, entity);
    }

    @Test
    void modelProfileRoundTripPreservesFields() {
        Instant now = Instant.parse("2026-03-23T08:20:00Z");
        ModelProfileRecord record = new ModelProfileRecord(
                "openai-gpt-5.2",
                "OpenAI GPT-5.2",
                "openai",
                "gpt-5.2",
                0.2,
                180,
                "{\"apiKeyEnv\":\"OPENAI_API_KEY\"}",
                true,
                now,
                now
        );

        ModelProfileEntity entity = PersistenceEntityMapper.toEntity(record);
        ModelProfileRecord mapped = PersistenceEntityMapper.toRecord(entity);

        assertEquals(record, mapped);
    }

    private static final class AuditRecordRecordAssert {
        private static void assertRoundTrip(AuditRecord expected, AuditRecordEntity entity) {
            AuditRecord mapped = PersistenceEntityMapper.toRecord(entity);
            assertEquals(expected, mapped);
        }
    }
}
