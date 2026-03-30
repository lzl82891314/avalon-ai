package com.example.avalon.app.console;

import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleDecisionReportBuilderTest {
    private final ConsoleDecisionReportBuilder builder = new ConsoleDecisionReportBuilder();

    @Test
    void shouldGroupRowsByRoundAndEnrichActionDetails() {
        GameStateResponse finalState = state("game-1", "RUNNING", "DISCUSSION", 2, null);
        ConsoleDecisionReport report = builder.build(finalState, List.of(
                event(1L, "GAME_STARTED", "DISCUSSION", "SYSTEM", Map.of("leaderSeat", 1)),
                event(2L, "PLAYER_ACTION", "DISCUSSION", "P1", Map.of(
                        "seatNo", 1,
                        "actionType", "PUBLIC_SPEECH",
                        "speech", "我先说公开信息。"
                )),
                event(3L, "PLAYER_ACTION", "TEAM_PROPOSAL", "P1", Map.of(
                        "seatNo", 1,
                        "actionType", "TEAM_PROPOSAL",
                        "speech", "我先提一个可验证的队伍。"
                )),
                event(4L, "TEAM_PROPOSED", "TEAM_PROPOSAL", "P1", Map.of("playerIds", List.of("P1", "P2"))),
                event(5L, "PLAYER_ACTION", "TEAM_VOTE", "P2", Map.of(
                        "seatNo", 2,
                        "actionType", "TEAM_VOTE",
                        "speech", "我暂时支持这支队伍。"
                )),
                event(6L, "TEAM_VOTE_CAST", "TEAM_VOTE", "P2", Map.of("vote", "APPROVE")),
                event(7L, "PLAYER_ACTION", "MISSION_ACTION", "P1", Map.of(
                        "seatNo", 1,
                        "actionType", "MISSION_ACTION",
                        "speech", ""
                )),
                event(8L, "MISSION_ACTION_CAST", "MISSION_ACTION", "P1", Map.of("choice", "SUCCESS")),
                event(9L, "MISSION_SUCCESS", "MISSION_RESOLUTION", "SYSTEM", Map.of("roundNo", 1)),
                event(10L, "PLAYER_ACTION", "DISCUSSION", "P3", Map.of(
                        "seatNo", 3,
                        "actionType", "PUBLIC_SPEECH",
                        "speech", "第二轮我先观察。"
                ))
        ), List.of(
                audit(3L,
                        """
                        {"privateThought":"先测一下 P2。"}
                        """.strip(),
                        """
                        {"actionType":"TEAM_PROPOSAL","selectedPlayerIds":["P1","P2"]}
                        """.strip(),
                        """
                        {"reasonSummary":["先做低风险验证"]}
                        """.strip(),
                        """
                        {"valid":true}
                        """.strip()),
                audit(7L,
                        """
                        {"reasoningDetailsPreview":"先把任务做成。"}
                        """.strip(),
                        """
                        {"actionType":"MISSION_ACTION","choice":"SUCCESS"}
                        """.strip(),
                        """
                        {"reasonSummary":["先执行任务动作"]}
                        """.strip(),
                        """
                        {"valid":true}
                        """.strip())
        ), players(
                player("P1", 1, "Alice", "MERLIN", "GOOD"),
                player("P2", 2, "Bob", "PERCIVAL", "GOOD"),
                player("P3", 3, "Cara", "MORGANA", "EVIL")
        ));

        assertThat(report.players()).extracting(ConsoleDecisionPlayer::roleId)
                .containsExactly("MERLIN", "PERCIVAL", "MORGANA");
        assertThat(report.sections()).hasSize(2);

        ConsoleDecisionSection firstRound = report.sections().get(0);
        assertThat(firstRound.title()).isEqualTo("第1轮");
        assertThat(firstRound.leaderPlayerId()).isEqualTo("P1");
        assertThat(firstRound.teamPlayerIds()).containsExactly("P1", "P2");
        assertThat(firstRound.votes()).containsExactly(new ConsoleDecisionVote("P2", "APPROVE"));
        assertThat(firstRound.missionOutcome()).isEqualTo("SUCCESS");
        assertThat(firstRound.rows()).hasSize(4);
        assertThat(firstRound.rows().get(1).roleId()).isEqualTo("MERLIN");
        assertThat(firstRound.rows().get(1).actionDetail()).isEqualTo("[P1, P2]");
        assertThat(firstRound.rows().get(1).privateThought()).isEqualTo("先测一下 P2。");
        assertThat(firstRound.rows().get(2).actionDetail()).isEqualTo("APPROVE");
        assertThat(firstRound.rows().get(3).actionDetail()).isEqualTo("SUCCESS");
        assertThat(firstRound.rows().get(3).privateThought()).isEqualTo("先把任务做成。");

        ConsoleDecisionSection secondRound = report.sections().get(1);
        assertThat(secondRound.title()).isEqualTo("第2轮");
        assertThat(secondRound.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.eventSeqNo()).isEqualTo(10L);
                    assertThat(row.playerId()).isEqualTo("P3");
                    assertThat(row.roleId()).isEqualTo("MORGANA");
                    assertThat(row.actionType()).isEqualTo("PUBLIC_SPEECH");
                });
    }

    @Test
    void shouldCreateFailedRowForPausedTurn() {
        GameStateResponse finalState = state("game-2", "PAUSED", "TEAM_VOTE", 1, "P3");
        ConsoleDecisionReport report = builder.build(finalState, List.of(
                event(1L, "GAME_STARTED", "DISCUSSION", "SYSTEM", Map.of("leaderSeat", 1)),
                event(2L, "GAME_PAUSED", "TEAM_VOTE", "P3", Map.of(
                        "reason", "LLM_ACTION_FAILURE",
                        "playerId", "P3"
                ))
        ), List.of(
                audit(2L,
                        """
                        {"reasoningDetailsPreview":"我本来想先保守通过。","publicSpeech":"我暂时支持这队。"}
                        """.strip(),
                        "{}",
                        "{}",
                        """
                        {"valid":false,"errorMessage":"OpenAI-compatible assistant content was empty (shape=reasoning_only)"}
                        """.strip())
        ), players(
                player("P1", 1, "Alice", "MERLIN", "GOOD"),
                player("P2", 2, "Bob", "LOYAL_SERVANT", "GOOD"),
                player("P3", 3, "Cara", "ASSASSIN", "EVIL")
        ));

        assertThat(report.sections()).singleElement()
                .satisfies(section -> {
                    assertThat(section.pauseReason()).isEqualTo("LLM_ACTION_FAILURE");
                    assertThat(section.rows()).singleElement()
                            .satisfies(row -> {
                                assertThat(row.failed()).isTrue();
                                assertThat(row.playerId()).isEqualTo("P3");
                                assertThat(row.roleId()).isEqualTo("ASSASSIN");
                                assertThat(row.actionType()).isEqualTo("TEAM_VOTE");
                                assertThat(row.publicSpeech()).isEqualTo("我暂时支持这队。");
                                assertThat(row.privateThought()).isEqualTo("我本来想先保守通过。");
                                assertThat(row.note()).contains("assistant content was empty");
                            });
                });
    }

    @Test
    void shouldExposeModelInputPrivateKnowledgeInRowNote() {
        GameAuditEntryResponse auditEntry = audit(2L,
                """
                {"privateThought":"我怀疑 P5 更像梅林。"}
                """.strip(),
                """
                {"actionType":"TEAM_VOTE","vote":"APPROVE"}
                """.strip(),
                """
                {"reasonSummary":["我怀疑 P5 更像梅林。"]}
                """.strip(),
                """
                {"valid":true}
                """.strip());
        auditEntry.setInputContextJson("""
                {"privateKnowledge":{"notes":["You see Merlin and Morgana as candidates."],"visiblePlayers":[{"playerId":"P5","displayName":"Eva","candidateRoleIds":["MERLIN","MORGANA"]}]}}
                """.strip());

        ConsoleDecisionReport report = builder.build(state("game-3", "RUNNING", "TEAM_VOTE", 1, null), List.of(
                event(1L, "GAME_STARTED", "DISCUSSION", "SYSTEM", Map.of("leaderSeat", 1)),
                event(2L, "PLAYER_ACTION", "TEAM_VOTE", "P1", Map.of(
                        "seatNo", 1,
                        "actionType", "TEAM_VOTE",
                        "speech", "我先过一轮。"
                ))
        ), List.of(auditEntry), players(
                player("P1", 1, "Alice", "PERCIVAL", "GOOD")
        ));

        assertThat(report.sections()).singleElement()
                .satisfies(section -> assertThat(section.rows()).singleElement()
                        .satisfies(row -> assertThat(row.note())
                                .contains("模型私有知识: P5/Eva∈[梅林, 莫甘娜]")
                                .contains("You see Merlin and Morgana as candidates.")));
    }

    private GameStateResponse state(String gameId, String status, String phase, int roundNo, String nextRequiredActor) {
        GameStateResponse state = new GameStateResponse();
        state.setGameId(gameId);
        state.setStatus(status);
        state.setPhase(phase);
        state.setRoundNo(roundNo);
        state.setNextRequiredActor(nextRequiredActor);
        Map<String, Object> publicState = new LinkedHashMap<>();
        publicState.put("leaderSeat", 1);
        publicState.put("failedTeamVoteCount", 0);
        publicState.put("approvedMissionRounds", List.of());
        publicState.put("failedMissionRounds", List.of());
        publicState.put("currentProposalTeam", List.of());
        publicState.put("winnerCamp", null);
        state.setPublicState(publicState);
        return state;
    }

    private GameEventEntryResponse event(long seqNo,
                                         String type,
                                         String phase,
                                         String actorId,
                                         Map<String, Object> payload) {
        GameEventEntryResponse event = new GameEventEntryResponse();
        event.setSeqNo(seqNo);
        event.setType(type);
        event.setPhase(phase);
        event.setActorId(actorId);
        event.setPayload(payload);
        return event;
    }

    private GameAuditEntryResponse audit(long eventSeqNo,
                                         String rawModelResponseJson,
                                         String parsedActionJson,
                                         String auditReasonJson,
                                         String validationResultJson) {
        GameAuditEntryResponse entry = new GameAuditEntryResponse();
        entry.setEventSeqNo(eventSeqNo);
        entry.setRawModelResponseJson(rawModelResponseJson);
        entry.setParsedActionJson(parsedActionJson);
        entry.setAuditReasonJson(auditReasonJson);
        entry.setValidationResultJson(validationResultJson);
        return entry;
    }

    private List<ConsoleDecisionPlayer> players(ConsoleDecisionPlayer... players) {
        return List.of(players);
    }

    private ConsoleDecisionPlayer player(String playerId, int seatNo, String displayName, String roleId, String camp) {
        return new ConsoleDecisionPlayer(playerId, seatNo, displayName, roleId, camp, "summary");
    }
}
