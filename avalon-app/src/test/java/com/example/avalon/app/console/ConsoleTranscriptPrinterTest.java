package com.example.avalon.app.console;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.ModelProfileProbeCheckResponse;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleTranscriptPrinterTest {
    private final ConsoleTranscriptPrinter printer = new ConsoleTranscriptPrinter();

    @Test
    void shouldRenderSpeechAndProposalEventsWithReadableDetails() {
        ConsoleGameSession session = buildSession();

        GameEventEntryResponse speechEvent = new GameEventEntryResponse();
        speechEvent.setSeqNo(8L);
        speechEvent.setType("PLAYER_ACTION");
        speechEvent.setPhase("DISCUSSION");
        speechEvent.setActorId("P1");
        speechEvent.setPayload(Map.of(
                "seatNo", 1,
                "actionType", "PUBLIC_SPEECH",
                "speech", "我想和 P2 一起上队试探一下。"
        ));

        GameEventEntryResponse proposalEvent = new GameEventEntryResponse();
        proposalEvent.setSeqNo(9L);
        proposalEvent.setType("TEAM_PROPOSED");
        proposalEvent.setPhase("TEAM_PROPOSAL");
        proposalEvent.setActorId("P1");
        proposalEvent.setPayload(Map.of("playerIds", List.of("P1", "P2")));

        assertThat(printer.formatEvent(speechEvent, session))
                .contains("玩家行动")
                .contains("行动者=P1/Alice")
                .contains("公开发言=我想和 P2 一起上队试探一下。");
        assertThat(printer.formatEvent(proposalEvent, session))
                .contains("提出队伍")
                .contains("[P1/Alice, P2/Bob]");
    }

    @Test
    void shouldRenderStateWithSeatLabels() {
        ConsoleGameSession session = buildSession();
        GameStateResponse state = new GameStateResponse();
        state.setGameId("game-1");
        state.setStatus("RUNNING");
        state.setPhase("TEAM_VOTE");
        state.setRoundNo(2);
        Map<String, Object> publicState = new LinkedHashMap<>();
        publicState.put("leaderSeat", 3);
        publicState.put("failedTeamVoteCount", 1);
        publicState.put("approvedMissionRounds", List.of(1));
        publicState.put("failedMissionRounds", List.of());
        publicState.put("currentProposalTeam", List.of(2, 3, 4));
        publicState.put("winnerCamp", null);
        state.setPublicState(publicState);
        state.setNextRequiredActor("P2");
        state.setWaitingReason("等待玩家对队伍投票");

        assertThat(printer.formatState(state, session))
                .contains("状态=进行中 阶段=队伍投票 轮次=第2轮")
                .contains("队长=3号位(P3/Cara)")
                .contains("2号位(P2/Bob)")
                .contains("成功任务=[1]")
                .contains("下一位行动=P2/Bob");
    }

    @Test
    void shouldRenderInlineThoughtFromRawModelResponse() {
        ConsoleGameSession session = buildSession();
        GameAuditEntryResponse entry = new GameAuditEntryResponse();
        entry.setPlayerId("P1");
        entry.setEventSeqNo(8L);
        entry.setRawModelResponseJson("""
                {"publicSpeech":"我想和 P2 一起上队。","privateThought":"先测试 P2 的站位是否稳定。","assistantContentShape":"json_object","assistantContentPreview":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\"}}"}
                """.strip());
        entry.setAuditReasonJson("""
                {"goal":"生成一个合法动作","reasonSummary":["优先验证队友站位"]}
                """.strip());
        entry.setParsedActionJson("""
                {"actionType":"PUBLIC_SPEECH","speechText":"我想和 P2 一起上队。"}
                """.strip());

        assertThat(printer.formatInlineThought(entry, session))
                .contains("[思考] P1/Alice")
                .contains("私有思考=先测试 P2 的站位是否稳定。")
                .contains("响应形态=纯 JSON 对象")
                .contains("响应预览={\"action\":{\"actionType\":\"PUBLIC_SPEECH\"}}")
                .contains("决策依据=优先验证队友站位")
                .contains("结构化动作={\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"我想和 P2 一起上队。\"}");
    }

    @Test
    void shouldFallbackToReasoningPreviewWhenPrivateThoughtMissing() {
        ConsoleGameSession session = buildSession();
        GameAuditEntryResponse entry = new GameAuditEntryResponse();
        entry.setPlayerId("P1");
        entry.setEventSeqNo(8L);
        entry.setRawModelResponseJson("""
                {"assistantContentShape":"reasoning_only","reasoningDetailsPreview":"这里只返回了 reasoning。"}
                """.strip());
        entry.setValidationResultJson("""
                {"valid":false,"errorMessage":"OpenAI-compatible assistant content was empty (shape=reasoning_only)"}
                """.strip());

        assertThat(printer.formatInlineThought(entry, session))
                .contains("私有思考=这里只返回了 reasoning。")
                .contains("响应形态=仅返回 reasoning_details")
                .contains("错误=OpenAI-compatible assistant content was empty (shape=reasoning_only)");
    }

    @Test
    void shouldPreferValidationErrorOverOuterRetryMessage() {
        ConsoleGameSession session = buildSession();
        GameAuditEntryResponse entry = new GameAuditEntryResponse();
        entry.setPlayerId("P1");
        entry.setEventSeqNo(8L);
        entry.setErrorMessage("Agent turn validation failed after 2 attempts");
        entry.setValidationResultJson("""
                {"valid":false,"errorMessage":"OpenAI-compatible request failed with status 400: "}
                """.strip());

        assertThat(printer.formatInlineThought(entry, session))
                .contains("错误=OpenAI-compatible request failed with status 400: ")
                .doesNotContain("Agent turn validation failed after 2 attempts");
    }

    @Test
    void shouldRenderOptionalSectionWarningsFromValidationMetadata() {
        ConsoleGameSession session = buildSession();
        GameAuditEntryResponse entry = new GameAuditEntryResponse();
        entry.setPlayerId("P1");
        entry.setRawModelResponseJson("""
                {"privateThought":"先保持低风险验证。","assistantContentShape":"json_object"}
                """.strip());
        entry.setValidationResultJson("""
                {"valid":true,"optionalSectionWarnings":[{"field":"memoryUpdate","reason":"dto_conversion_failed","contentPreview":"{\\"trustDelta\\":{\\"P1\\":{\\"score\\":1}}}"}]}
                """.strip());

        assertThat(printer.formatInlineThought(entry, session))
                .contains("附加字段告警=memoryUpdate 对象字段形状不合法")
                .contains("内容预览={\"trustDelta\":{\"P1\":{\"score\":1}}}");
    }

    @Test
    void shouldRenderModelProbeSummary() {
        ModelProfileProbeResponse response = new ModelProfileProbeResponse();
        response.setModelId("minimax-m2.7");
        response.setProvider("minimax");
        response.setModelName("minimax-m2.7");
        response.setBaseUrl("https://gcapi.cn/v1");
        response.setReachable(true);
        response.setStructuredCompatible(false);
        response.setDiagnosis("NETWORK_OK_BUT_STRUCTURED_JSON_FAILED");

        ModelProfileProbeCheckResponse connectivity = new ModelProfileProbeCheckResponse();
        connectivity.setCheckType("CONNECTIVITY");
        connectivity.setSuccess(true);
        connectivity.setHttpStatus(200);
        connectivity.setLatencyMs(321L);
        connectivity.setFinishReason("stop");
        connectivity.setAssistantPreview("Hi there!");

        ModelProfileProbeCheckResponse structured = new ModelProfileProbeCheckResponse();
        structured.setCheckType("STRUCTURED_JSON");
        structured.setSuccess(false);
        structured.setHttpStatus(200);
        structured.setLatencyMs(456L);
        structured.setContentShape("reasoning_only");
        structured.setContentPresent(false);
        structured.setReasoningDetailsPresent(true);
        structured.setReasoningDetailsPreview("这里只返回了 reasoning。");
        structured.setErrorMessage("OpenAI-compatible assistant content was empty (shape=reasoning_only)");

        response.setChecks(List.of(connectivity, structured));

        assertThat(printer.formatModelProbe(response))
                .contains("模型探测 minimax-m2.7")
                .contains("诊断=网络可达，但结构化 JSON 不兼容")
                .contains("[连通性] 通过 HTTP=200 耗时=321ms 结束原因=stop")
                .contains("响应预览=Hi there!")
                .contains("[结构化 JSON] 失败 HTTP=200 耗时=456ms")
                .contains("内容形态=仅返回 reasoning_details")
                .contains("content=无")
                .contains("reasoning_details=有")
                .contains("推理预览=这里只返回了 reasoning。")
                .contains("错误=OpenAI-compatible assistant content was empty (shape=reasoning_only)");
    }

    @Test
    void shouldRenderDecisionReportForConsoleAndMarkdown() {
        ConsoleGameSession session = buildSession();
        ConsoleDecisionReport report = new ConsoleDecisionReport(
                "game-1",
                "ENDED",
                "GAME_END",
                "GOOD",
                2,
                List.of(
                        new ConsoleDecisionPlayer("P1", 1, "Alice", "PERCIVAL", "GOOD", "P3/Cara∈[梅林, 莫甘娜]；notes: You see Merlin and Morgana as candidates."),
                        new ConsoleDecisionPlayer("P2", 2, "Bob", "MERLIN", "GOOD", "P4/Dylan=莫甘娜；P5/Eva=刺客"),
                        new ConsoleDecisionPlayer("P3", 3, "Cara", "MORGANA", "EVIL", "P5/Eva=刺客")
                ),
                List.of(new ConsoleDecisionSection(
                        "ROUND-1",
                        "第1轮",
                        "P1",
                        List.of("P1", "P2"),
                        List.of(new ConsoleDecisionVote("P1", "APPROVE"), new ConsoleDecisionVote("P2", "REJECT")),
                        false,
                        "SUCCESS",
                        0L,
                        null,
                        null,
                        List.of(new ConsoleDecisionRow(
                                8L,
                                "TEAM_PROPOSAL",
                                "P1",
                                "PERCIVAL",
                                "TEAM_PROPOSAL",
                                "[P1, P2]",
                                "我先提一个可验证的队伍。",
                                "先低风险试探一下。",
                                null,
                                false
                        ))
                ))
        );

        assertThat(printer.formatDecisionReport(report, session, java.nio.file.Path.of("target/reports/avalon/game-1-decision-report.md")))
                .contains("局后决策报告 game-1")
                .contains("角色总表")
                .contains("说明=privateThought 是模型原始文本，不等同于规则允许的确定知识")
                .contains("第1轮")
                .contains("摘要=队长=P1/Alice")
                .contains("| 座位 | 玩家 | 角色 | 阵营 | 初始私有知识 |")
                .contains("| 序号 | 阶段 | 玩家 | 角色 | 动作 | 公开发言 | 私有思考 | 备注 |");
        assertThat(printer.formatDecisionReportMarkdown(report, session))
                .contains("# 决策报告：game-1")
                .contains("## 角色总表")
                .contains("- 说明：privateThought 是模型原始文本，不等同于规则允许的确定知识")
                .contains("## 第1轮")
                .contains("| 序号 | 阶段 | 玩家 | 角色 | 动作 | 公开发言 | 私有思考 | 备注 |")
                .contains("派西维尔")
                .contains("提出队伍（[P1, P2]）");
    }

    private ConsoleGameSession buildSession() {
        CreateGameRequest request = new CreateGameRequest();
        request.setPlayers(List.of(
                player(1, "Alice"),
                player(2, "Bob"),
                player(3, "Cara"),
                player(4, "Dylan"),
                player(5, "Eva")
        ));
        ConsoleGameSession session = new ConsoleGameSession();
        session.activateNewGame("game-1", request);
        return session;
    }

    private CreateGameRequest.PlayerSlotRequest player(int seatNo, String name) {
        CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
        player.setSeatNo(seatNo);
        player.setDisplayName(name);
        player.setControllerType("LLM");
        player.setAgentConfig(new PlayerAgentConfig());
        return player;
    }
}
