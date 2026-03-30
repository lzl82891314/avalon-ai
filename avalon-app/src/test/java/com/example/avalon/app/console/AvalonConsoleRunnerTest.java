package com.example.avalon.app.console;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.GameSummaryResponse;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import com.example.avalon.api.dto.ModelProfileResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;
import com.example.avalon.api.service.GameApplicationService;
import com.example.avalon.api.service.ModelProfileCatalogService;
import com.example.avalon.api.service.ModelProfileProbeService;
import com.example.avalon.api.service.SeedGenerator;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.core.setup.model.SetupTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvalonConsoleRunnerTest {
    private static final long GENERATED_SEED = 246810L;
    private static final SetupTemplate CLASSIC_TEMPLATE = new SetupTemplate(
            "classic-5p-v1",
            5,
            true,
            List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")
    );

    @Test
    void buildCreateRequestShouldDefaultToRoleBoundModelPoolWhenUserPressesEnter() {
        AvalonConsoleRunner runner = runnerWithProfiles(List.of(profile("openai-gpt-5.4")));

        CreateGameRequest request = ReflectionTestUtils.invokeMethod(
                runner,
                "buildCreateRequest",
                reader(blankLines(9))
        );

        assertThat(request.getPlayers())
                .hasSize(5)
                .allSatisfy(player -> {
                    assertThat(player.getControllerType()).isEqualTo("LLM");
                    assertThat(player.getAgentConfig()).isNotNull();
                });
        assertThat(request.getSeed()).isEqualTo(GENERATED_SEED);
        assertThat(request.getLlmSelection()).isNotNull();
        assertThat(request.getLlmSelection().getMode()).isEqualTo("ROLE_BINDING");
        assertThat(request.getLlmSelection().getRoleBindings().values())
                .containsOnly("openai-gpt-5.4");
    }

    @Test
    void promptRoleBindingSelectionShouldCycleProfilesWhenEnabledCountIsBelowRoleCount() {
        AvalonConsoleRunner runner = runnerWithProfiles(List.of(
                profile("model-a"),
                profile("model-b")
        ));

        CreateGameRequest.LlmSelectionRequest request = ReflectionTestUtils.invokeMethod(
                runner,
                "promptRoleBindingSelection",
                reader(blankLines(5)),
                CLASSIC_TEMPLATE
        );

        assertThat(request.getRoleBindings())
                .containsExactlyEntriesOf(orderedBindings(
                        "MERLIN", "model-a",
                        "PERCIVAL", "model-b",
                        "LOYAL_SERVANT", "model-a",
                        "MORGANA", "model-b",
                        "ASSASSIN", "model-a"
                ));
    }

    @Test
    void promptRoleBindingSelectionShouldUseFirstFiveEnabledProfilesWhenMoreThanFiveExist() {
        AvalonConsoleRunner runner = runnerWithProfiles(List.of(
                profile("model-a"),
                profile("model-b"),
                profile("model-c"),
                profile("model-d"),
                profile("model-e"),
                profile("model-f")
        ));

        CreateGameRequest.LlmSelectionRequest request = ReflectionTestUtils.invokeMethod(
                runner,
                "promptRoleBindingSelection",
                reader(blankLines(5)),
                CLASSIC_TEMPLATE
        );

        assertThat(request.getRoleBindings())
                .containsExactlyEntriesOf(orderedBindings(
                        "MERLIN", "model-a",
                        "PERCIVAL", "model-b",
                        "LOYAL_SERVANT", "model-c",
                        "MORGANA", "model-d",
                        "ASSASSIN", "model-e"
                ));
    }

    @Test
    void runActiveGameShouldApplyPlaybackDelaysBetweenSteps() {
        GameApplicationService gameApplicationService = mock(GameApplicationService.class);
        ModelProfileCatalogService modelProfileCatalogService = mock(ModelProfileCatalogService.class);
        ModelProfileProbeService modelProfileProbeService = mock(ModelProfileProbeService.class);
        AvalonConfigRegistry configRegistry = mock(AvalonConfigRegistry.class);
        ConsoleTranscriptPrinter printer = new ConsoleTranscriptPrinter();
        ConsoleDecisionReportBuilder decisionReportBuilder = new ConsoleDecisionReportBuilder();
        List<Long> delays = new ArrayList<>();
        ConsolePlaybackSettings playbackSettings = new ConsolePlaybackSettings(true, 5L, 7L);
        ConsolePlaybackDelayer playbackDelayer = delays::add;
        SeedGenerator seedGenerator = () -> GENERATED_SEED;
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        AvalonConsoleRunner runner = new AvalonConsoleRunner(
                gameApplicationService,
                modelProfileCatalogService,
                modelProfileProbeService,
                configRegistry,
                seedGenerator,
                printer,
                decisionReportBuilder,
                playbackSettings,
                playbackDelayer,
                "target/test-reports",
                applicationContext
        );

        ConsoleGameSession session = (ConsoleGameSession) ReflectionTestUtils.getField(runner, "session");
        CreateGameRequest request = new CreateGameRequest();
        request.setPlayers(List.of(
                player(1, "Alice"),
                player(2, "Bob"),
                player(3, "Cara"),
                player(4, "Dylan"),
                player(5, "Eva")
        ));
        session.activateNewGame("game-1", request);

        when(gameApplicationService.getState("game-1"))
                .thenReturn(state("game-1", "RUNNING", "DISCUSSION", 1, "P1", "等待玩家公开发言"))
                .thenReturn(state("game-1", "ENDED", "GAME_END", 1, null, "游戏已结束"));
        when(gameApplicationService.stepGame("game-1")).thenReturn(new GameSummaryResponse());
        when(gameApplicationService.getEvents("game-1")).thenReturn(List.of(event(1L)));
        when(gameApplicationService.getAudit("game-1")).thenReturn(List.of(audit(1L)));
        stubPlayerViews(gameApplicationService, "game-1");

        ReflectionTestUtils.invokeMethod(runner, "runActiveGame");

        verify(gameApplicationService).stepGame("game-1");
        assertThat(delays).containsExactly(5L, 7L);
    }

    @Test
    void probeModelCommandShouldWorkWithoutActiveGame() {
        GameApplicationService gameApplicationService = mock(GameApplicationService.class);
        ModelProfileCatalogService modelProfileCatalogService = mock(ModelProfileCatalogService.class);
        ModelProfileProbeService modelProfileProbeService = mock(ModelProfileProbeService.class);
        AvalonConfigRegistry configRegistry = mock(AvalonConfigRegistry.class);
        ConsoleTranscriptPrinter printer = mock(ConsoleTranscriptPrinter.class);
        ConsoleDecisionReportBuilder decisionReportBuilder = mock(ConsoleDecisionReportBuilder.class);
        ConsolePlaybackSettings playbackSettings = new ConsolePlaybackSettings(true, 1L, 1L);
        ConsolePlaybackDelayer playbackDelayer = millis -> { };
        SeedGenerator seedGenerator = () -> GENERATED_SEED;
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        AvalonConsoleRunner runner = new AvalonConsoleRunner(
                gameApplicationService,
                modelProfileCatalogService,
                modelProfileProbeService,
                configRegistry,
                seedGenerator,
                printer,
                decisionReportBuilder,
                playbackSettings,
                playbackDelayer,
                "target/test-reports",
                applicationContext
        );

        ModelProfileProbeResponse response = new ModelProfileProbeResponse();
        response.setModelId("minimax-m2.7");
        when(modelProfileProbeService.probe(org.mockito.ArgumentMatchers.eq("minimax-m2.7"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);
        when(printer.formatModelProbe(response)).thenReturn("probe output");

        ReflectionTestUtils.invokeMethod(runner, "dispatch", "probe-model minimax-m2.7 structured", reader(""));

        verify(modelProfileProbeService).probe(org.mockito.ArgumentMatchers.eq("minimax-m2.7"), org.mockito.ArgumentMatchers.any());
        verify(printer).formatModelProbe(response);
    }

    @Test
    void stepActiveGameShouldWriteDecisionReportWhenGameEnds(@TempDir Path tempDir) throws Exception {
        GameApplicationService gameApplicationService = mock(GameApplicationService.class);
        ModelProfileCatalogService modelProfileCatalogService = mock(ModelProfileCatalogService.class);
        ModelProfileProbeService modelProfileProbeService = mock(ModelProfileProbeService.class);
        AvalonConfigRegistry configRegistry = mock(AvalonConfigRegistry.class);
        ConsoleTranscriptPrinter printer = new ConsoleTranscriptPrinter();
        ConsoleDecisionReportBuilder decisionReportBuilder = new ConsoleDecisionReportBuilder();
        ConsolePlaybackSettings playbackSettings = new ConsolePlaybackSettings(false, 1L, 1L);
        ConsolePlaybackDelayer playbackDelayer = millis -> { };
        SeedGenerator seedGenerator = () -> GENERATED_SEED;
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        AvalonConsoleRunner runner = new AvalonConsoleRunner(
                gameApplicationService,
                modelProfileCatalogService,
                modelProfileProbeService,
                configRegistry,
                seedGenerator,
                printer,
                decisionReportBuilder,
                playbackSettings,
                playbackDelayer,
                tempDir.toString(),
                applicationContext
        );

        ConsoleGameSession session = (ConsoleGameSession) ReflectionTestUtils.getField(runner, "session");
        CreateGameRequest request = new CreateGameRequest();
        request.setPlayers(List.of(
                player(1, "Alice"),
                player(2, "Bob"),
                player(3, "Cara"),
                player(4, "Dylan"),
                player(5, "Eva")
        ));
        session.activateNewGame("game-1", request);

        List<GameEventEntryResponse> events = List.of(
                event(1L, "GAME_STARTED", "DISCUSSION", "SYSTEM", Map.of("leaderSeat", 1)),
                event(2L, "PLAYER_ACTION", "DISCUSSION", "P1", Map.of(
                        "seatNo", 1,
                        "actionType", "PUBLIC_SPEECH",
                        "speech", "我先发一段公开信息。"
                )),
                event(3L, "GAME_ENDED", "GAME_END", "SYSTEM", Map.of("winner", "GOOD"))
        );
        List<GameAuditEntryResponse> audits = List.of(audit(2L));

        when(gameApplicationService.getState("game-1"))
                .thenReturn(state("game-1", "RUNNING", "DISCUSSION", 1, "P1", "等待玩家公开发言"))
                .thenReturn(endedState("game-1", 1));
        when(gameApplicationService.stepGame("game-1")).thenReturn(new GameSummaryResponse());
        when(gameApplicationService.getEvents("game-1")).thenReturn(events, events);
        when(gameApplicationService.getAudit("game-1")).thenReturn(audits, audits);
        stubPlayerViews(gameApplicationService, "game-1");

        ReflectionTestUtils.invokeMethod(runner, "stepActiveGame");

        Path reportPath = tempDir.resolve("game-1-decision-report.md");
        assertThat(reportPath).exists();
        assertThat(Files.readString(reportPath))
                .contains("# 决策报告：game-1")
                .contains("## 角色总表")
                .contains("| 序号 | 阶段 | 玩家 | 角色 | 动作 | 公开发言 | 私有思考 | 备注 |")
                .contains("## 第1轮")
                .contains("我先发一段公开信息。");
    }

    private AvalonConsoleRunner runnerWithProfiles(List<ModelProfileResponse> profiles) {
        GameApplicationService gameApplicationService = mock(GameApplicationService.class);
        ModelProfileCatalogService modelProfileCatalogService = mock(ModelProfileCatalogService.class);
        ModelProfileProbeService modelProfileProbeService = mock(ModelProfileProbeService.class);
        AvalonConfigRegistry configRegistry = mock(AvalonConfigRegistry.class);
        ConsoleTranscriptPrinter printer = mock(ConsoleTranscriptPrinter.class);
        ConsoleDecisionReportBuilder decisionReportBuilder = mock(ConsoleDecisionReportBuilder.class);
        ConsolePlaybackSettings playbackSettings = new ConsolePlaybackSettings(true, 1L, 1L);
        ConsolePlaybackDelayer playbackDelayer = millis -> { };
        SeedGenerator seedGenerator = () -> GENERATED_SEED;
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        when(modelProfileCatalogService.listAll()).thenReturn(profiles);
        when(configRegistry.requireSetupTemplate("classic-5p-v1")).thenReturn(CLASSIC_TEMPLATE);

        return new AvalonConsoleRunner(
                gameApplicationService,
                modelProfileCatalogService,
                modelProfileProbeService,
                configRegistry,
                seedGenerator,
                printer,
                decisionReportBuilder,
                playbackSettings,
                playbackDelayer,
                "target/test-reports",
                applicationContext
        );
    }

    private BufferedReader reader(String input) {
        return new BufferedReader(new StringReader(input));
    }

    private String blankLines(int count) {
        return System.lineSeparator().repeat(count);
    }

    private ModelProfileResponse profile(String modelId) {
        ModelProfileResponse response = new ModelProfileResponse();
        response.setModelId(modelId);
        response.setDisplayName(modelId);
        response.setSource("STATIC");
        response.setEnabled(true);
        response.setProvider("openai");
        response.setModelName(modelId);
        return response;
    }

    private GameStateResponse state(String gameId,
                                    String status,
                                    String phase,
                                    int roundNo,
                                    String nextRequiredActor,
                                    String waitingReason) {
        GameStateResponse state = new GameStateResponse();
        state.setGameId(gameId);
        state.setStatus(status);
        state.setPhase(phase);
        state.setRoundNo(roundNo);
        state.setNextRequiredActor(nextRequiredActor);
        state.setWaitingReason(waitingReason);
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

    private GameStateResponse endedState(String gameId, int roundNo) {
        GameStateResponse state = state(gameId, "ENDED", "GAME_END", roundNo, null, "游戏已结束");
        Map<String, Object> publicState = new LinkedHashMap<>(state.getPublicState());
        publicState.put("winnerCamp", "GOOD");
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

    private GameEventEntryResponse event(long seqNo) {
        GameEventEntryResponse event = new GameEventEntryResponse();
        event.setSeqNo(seqNo);
        event.setType("PLAYER_ACTION");
        event.setPhase("DISCUSSION");
        event.setActorId("P1");
        event.setPayload(Map.of(
                "seatNo", 1,
                "actionType", "PUBLIC_SPEECH",
                "speech", "我先发言。"
        ));
        return event;
    }

    private GameAuditEntryResponse audit(long eventSeqNo) {
        GameAuditEntryResponse entry = new GameAuditEntryResponse();
        entry.setPlayerId("P1");
        entry.setEventSeqNo(eventSeqNo);
        entry.setRawModelResponseJson("{\"privateThought\":\"先说一段试探性发言。\"}");
        entry.setAuditReasonJson("{\"reasonSummary\":[\"先做低风险试探\"]}");
        entry.setParsedActionJson("{\"actionType\":\"PUBLIC_SPEECH\"}");
        return entry;
    }

    private void stubPlayerViews(GameApplicationService gameApplicationService, String gameId) {
        when(gameApplicationService.getPlayerView(gameId, "P1"))
                .thenReturn(playerView(1, "PERCIVAL", "GOOD", "P2/Bob∈[梅林, 莫甘娜]"));
        when(gameApplicationService.getPlayerView(gameId, "P2"))
                .thenReturn(playerView(2, "MERLIN", "GOOD", "P4/Dylan=莫甘娜；P5/Eva=刺客"));
        when(gameApplicationService.getPlayerView(gameId, "P3"))
                .thenReturn(playerView(3, "LOYAL_SERVANT", "GOOD", "无"));
        when(gameApplicationService.getPlayerView(gameId, "P4"))
                .thenReturn(playerView(4, "MORGANA", "EVIL", "P5/Eva=刺客"));
        when(gameApplicationService.getPlayerView(gameId, "P5"))
                .thenReturn(playerView(5, "ASSASSIN", "EVIL", "P4/Dylan=莫甘娜"));
    }

    private PlayerPrivateViewResponse playerView(int seatNo, String roleId, String camp, String knowledgeSummary) {
        PlayerPrivateViewResponse response = new PlayerPrivateViewResponse();
        response.setSeatNo(seatNo);
        response.setRoleSummary(roleId);
        response.setPrivateKnowledge(Map.of(
                "camp", camp,
                "notes", List.of(knowledgeSummary),
                "visiblePlayers", List.of()
        ));
        return response;
    }

    private CreateGameRequest.PlayerSlotRequest player(int seatNo, String name) {
        CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
        player.setSeatNo(seatNo);
        player.setDisplayName(name);
        player.setControllerType("LLM");
        player.setAgentConfig(new com.example.avalon.agent.model.PlayerAgentConfig());
        return player;
    }

    private Map<String, String> orderedBindings(String... values) {
        Map<String, String> bindings = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            bindings.put(values[index], values[index + 1]);
        }
        return bindings;
    }
}
