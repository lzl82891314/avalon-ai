package com.example.avalon.persistence.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceMigrationScriptTest {
    @Test
    void initMigrationDefinesPersistenceTablesAndIndexes() throws Exception {
        Path migration = Path.of("..", "avalon-app", "src", "main", "resources", "db", "migration", "V1__init_persistence.sql");
        String sql = Files.readString(migration);
        String extensionSql = Files.readString(Path.of("..", "avalon-app", "src", "main", "resources", "db", "migration", "V2__agent_audit_extensions.sql"));

        assertTrue(sql.contains("create table game_event"));
        assertTrue(sql.contains("create table game_snapshot"));
        assertTrue(sql.contains("create table player_memory_snapshot"));
        assertTrue(sql.contains("create table audit_record"));
        assertTrue(sql.contains("create table model_profile"));
        assertTrue(sql.contains("create unique index idx_game_event_game_seq"));
        assertTrue(sql.contains("create unique index idx_game_snapshot_game_seq"));
        assertTrue(sql.contains("create unique index idx_player_memory_game_player_seq"));
        assertTrue(sql.contains("create index idx_model_profile_enabled_created"));
        assertTrue(extensionSql.contains("alter table audit_record add column execution_trace_json"));
        assertTrue(extensionSql.contains("alter table audit_record add column policy_summary_json"));
    }
}
