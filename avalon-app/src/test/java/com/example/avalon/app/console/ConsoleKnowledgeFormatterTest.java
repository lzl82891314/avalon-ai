package com.example.avalon.app.console;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleKnowledgeFormatterTest {
    @Test
    void shouldSummarizeVisiblePlayersFromRuntimeObjectsAndMaps() {
        String summary = ConsoleKnowledgeFormatter.summarize(Map.of(
                "notes", List.of("You see Merlin and Morgana as candidates."),
                "visiblePlayers", List.of(
                        new VisiblePlayerInfo("P3", 3, "Cara", null, Camp.GOOD, List.of("MERLIN", "MORGANA")),
                        Map.of(
                                "playerId", "P4",
                                "displayName", "Dylan",
                                "camp", "EVIL",
                                "candidateRoleIds", List.of()
                        )
                )
        ));

        assertThat(summary)
                .contains("P3/Cara∈[梅林, 莫甘娜]")
                .contains("P4/Dylan=邪恶阵营")
                .contains("notes: You see Merlin and Morgana as candidates.");
    }
}
