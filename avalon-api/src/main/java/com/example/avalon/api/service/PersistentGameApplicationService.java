package com.example.avalon.api.service;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.dto.GameActionSubmissionRequest;
import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.GameSummaryResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.LlmSelectionConfig;
import com.example.avalon.runtime.model.LlmSelectionMode;
import com.example.avalon.runtime.model.PlayerRegistration;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.persistence.RuntimePersistenceService;
import com.example.avalon.runtime.recovery.RecoveryResult;
import com.example.avalon.runtime.recovery.RecoveryService;
import com.example.avalon.runtime.recovery.ReplayQueryService;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.TurnContextBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PersistentGameApplicationService implements GameApplicationService {
    private final AvalonConfigRegistry configRegistry;
    private final GameOrchestrator gameOrchestrator;
    private final GameSessionService gameSessionService;
    private final RuntimePersistenceService runtimePersistenceService;
    private final RecoveryService recoveryService;
    private final ReplayQueryService replayQueryService;
    private final TurnContextBuilder turnContextBuilder;
    private final ModelProfileCatalogService modelProfileCatalogService;
    private final SeedGenerator seedGenerator;
    private final ObjectMapper objectMapper;

    public PersistentGameApplicationService(
            AvalonConfigRegistry configRegistry,
            GameOrchestrator gameOrchestrator,
            GameSessionService gameSessionService,
            RuntimePersistenceService runtimePersistenceService,
            RecoveryService recoveryService,
            ReplayQueryService replayQueryService,
            TurnContextBuilder turnContextBuilder,
            ModelProfileCatalogService modelProfileCatalogService,
            SeedGenerator seedGenerator
    ) {
        this.configRegistry = configRegistry;
        this.gameOrchestrator = gameOrchestrator;
        this.gameSessionService = gameSessionService;
        this.runtimePersistenceService = runtimePersistenceService;
        this.recoveryService = recoveryService;
        this.replayQueryService = replayQueryService;
        this.turnContextBuilder = turnContextBuilder;
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.seedGenerator = seedGenerator;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public GameSummaryResponse createGame(CreateGameRequest request) {
        String gameId = "game-" + UUID.randomUUID();
        GameRuntimeState state = gameOrchestrator.createGame(toGameSetup(gameId, request));
        runtimePersistenceService.persist(state);
        return summary(state, "game created");
    }

    @Override
    public GameSummaryResponse startGame(String gameId) {
        GameRuntimeState state = gameOrchestrator.start(gameId);
        runtimePersistenceService.persist(state);
        return summary(state, "game started");
    }

    @Override
    public GameSummaryResponse stepGame(String gameId) {
        GameRuntimeState state = gameOrchestrator.step(gameId);
        runtimePersistenceService.persist(state);
        return summary(state, "game stepped");
    }

    @Override
    public GameSummaryResponse runGame(String gameId) {
        GameRuntimeState state = gameOrchestrator.runToEnd(gameId).state();
        runtimePersistenceService.persist(state);
        return summary(state, "game run-to-end completed");
    }

    @Override
    public GameStateResponse getState(String gameId) {
        GameRuntimeState state = requireState(gameId);
        GameStateResponse response = new GameStateResponse();
        response.setGameId(state.generatedGameId());
        response.setStatus(state.status().name());
        response.setPhase(state.phase().name());
        response.setRoundNo(state.roundNo());

        Map<String, Object> publicState = new LinkedHashMap<>();
        publicState.put("leaderSeat", state.currentLeaderSeat());
        publicState.put("playerCount", state.playerCount());
        publicState.put("failedTeamVoteCount", state.failedTeamVoteCount());
        publicState.put("approvedMissionRounds", state.approvedMissionRounds());
        publicState.put("failedMissionRounds", state.failedMissionRounds());
        publicState.put("currentProposalTeam", state.currentProposalTeam());
        publicState.put("winnerCamp", state.winnerCamp() == null ? null : state.winnerCamp().name());
        response.setPublicState(publicState);
        response.setNextRequiredActor(nextRequiredActor(state));
        response.setWaitingReason(waitingReason(state));
        return response;
    }

    @Override
    public List<GameEventEntryResponse> getEvents(String gameId) {
        return replayQueryService.events(gameId).stream().map(this::toEventResponse).toList();
    }

    @Override
    public List<GameEventEntryResponse> getReplay(String gameId) {
        return replayQueryService.replay(gameId).stream().map(this::toReplayResponse).toList();
    }

    @Override
    public List<GameAuditEntryResponse> getAudit(String gameId) {
        return replayQueryService.audit(gameId).stream().map(this::toAuditResponse).toList();
    }

    @Override
    public PlayerPrivateViewResponse getPlayerView(String gameId, String playerId) {
        GameRuntimeState state = requireState(gameId);
        PlayerRegistration player = state.playerById(playerId);
        PlayerTurnContext context = turnContextBuilder.build(state, player);
        PlayerPrivateView privateView = context.privateView();

        PlayerPrivateViewResponse response = new PlayerPrivateViewResponse();
        response.setSeatNo(player.seatNo());
        response.setRoleSummary(privateView.roleId());
        Map<String, Object> privateKnowledge = new LinkedHashMap<>();
        privateKnowledge.put("camp", privateView.camp().name());
        privateKnowledge.put("notes", privateView.knowledge().notes());
        privateKnowledge.put("visiblePlayers", privateView.knowledge().visiblePlayers());
        response.setPrivateKnowledge(privateKnowledge);
        response.setMemorySnapshot(state.memoryOf(playerId));
        response.setAllowedActions(context.allowedActions().allowedActionTypes().stream().map(Enum::name).toList());
        return response;
    }

    @Override
    public GameSummaryResponse submitPlayerAction(String gameId, String playerId, GameActionSubmissionRequest request) {
        throw new UnsupportedOperationException("human action submission is reserved for V2");
    }

    private GameSetup toGameSetup(String gameId, CreateGameRequest request) {
        SetupTemplate setupTemplate = configRegistry.requireSetupTemplate(request.getSetupTemplateId());
        long effectiveSeed = Optional.ofNullable(request.getSeed()).orElseGet(seedGenerator::nextSeed);
        List<PlayerRegistration> players = new ArrayList<>();
        int playerIndex = 1;
        for (CreateGameRequest.PlayerSlotRequest playerRequest : request.getPlayers()) {
            PlayerControllerType controllerType = PlayerControllerType.valueOf(Optional.ofNullable(playerRequest.getControllerType()).orElse("SCRIPTED"));
            players.add(new PlayerRegistration(
                    "P" + playerIndex,
                    Optional.ofNullable(playerRequest.getSeatNo()).orElse(playerIndex),
                    Optional.ofNullable(playerRequest.getDisplayName()).orElse("P" + playerIndex),
                    controllerType,
                    playerRequest.getAgentConfig() == null ? Map.of() : objectMapper.convertValue(playerRequest.getAgentConfig(), new TypeReference<Map<String, Object>>() { })));
            playerIndex++;
        }
        LlmSelectionConfig llmSelectionConfig = toLlmSelectionConfig(request.getLlmSelection(), setupTemplate, players);
        return new GameSetup(
                gameId,
                request.getRuleSetId(),
                configRegistry.requireRuleSet(request.getRuleSetId()),
                request.getSetupTemplateId(),
                setupTemplate,
                effectiveSeed,
                roleDefinitionsFor(setupTemplate),
                players,
                llmSelectionConfig
        );
    }

    private LlmSelectionConfig toLlmSelectionConfig(CreateGameRequest.LlmSelectionRequest request,
                                                    SetupTemplate setupTemplate,
                                                    List<PlayerRegistration> players) {
        if (request == null || request.getMode() == null || request.getMode().isBlank()) {
            return LlmSelectionConfig.none();
        }
        List<PlayerRegistration> llmPlayers = players.stream()
                .filter(player -> player.controllerType() == PlayerControllerType.LLM)
                .toList();
        int llmPlayerCount = llmPlayers.size();
        if (llmPlayerCount == 0) {
            throw new IllegalArgumentException("llmSelection requires at least one LLM player");
        }
        LlmSelectionMode mode = LlmSelectionMode.valueOf(request.getMode().trim().toUpperCase());
        return switch (mode) {
            case NONE -> LlmSelectionConfig.none();
            case SEAT_BINDING -> seatBindingSelection(request, llmPlayers);
            case ROLE_BINDING -> roleBindingSelection(request, setupTemplate);
            case RANDOM_POOL -> randomPoolSelection(request, llmPlayerCount);
        };
    }

    private LlmSelectionConfig seatBindingSelection(CreateGameRequest.LlmSelectionRequest request,
                                                    List<PlayerRegistration> llmPlayers) {
        Map<Integer, String> seatBindings = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : request.getSeatBindings().entrySet()) {
            Integer seatNo = entry.getKey();
            if (seatNo == null || llmPlayers.stream().noneMatch(player -> player.seatNo() == seatNo)) {
                throw new IllegalArgumentException("SEAT_BINDING may only reference active LLM seats");
            }
        }
        for (PlayerRegistration player : llmPlayers.stream().sorted((left, right) -> Integer.compare(left.seatNo(), right.seatNo())).toList()) {
            String modelId = request.getSeatBindings().get(player.seatNo());
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("SEAT_BINDING requires modelId for seat " + player.seatNo());
            }
            modelProfileCatalogService.requireEnabledProfile(modelId.trim());
            seatBindings.put(player.seatNo(), modelId.trim());
        }
        if (request.getSeatBindings().size() != seatBindings.size()) {
            throw new IllegalArgumentException("SEAT_BINDING may only reference active LLM seats");
        }
        return new LlmSelectionConfig(LlmSelectionMode.SEAT_BINDING, seatBindings, Map.of(), List.of());
    }

    private LlmSelectionConfig roleBindingSelection(CreateGameRequest.LlmSelectionRequest request, SetupTemplate setupTemplate) {
        Map<String, String> roleBindings = new LinkedHashMap<>();
        for (String roleId : distinctRoleIds(setupTemplate)) {
            String modelId = request.getRoleBindings().get(roleId);
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("ROLE_BINDING requires modelId for role " + roleId);
            }
            modelProfileCatalogService.requireEnabledProfile(modelId.trim());
            roleBindings.put(roleId, modelId.trim());
        }
        if (request.getRoleBindings().size() != roleBindings.size()) {
            throw new IllegalArgumentException("ROLE_BINDING may only reference active roles in the setup template");
        }
        return new LlmSelectionConfig(LlmSelectionMode.ROLE_BINDING, Map.of(), roleBindings, List.of());
    }

    private LlmSelectionConfig randomPoolSelection(CreateGameRequest.LlmSelectionRequest request, int llmPlayerCount) {
        List<String> candidateModelIds = request.getCandidateModelIds().stream()
                .map(String::trim)
                .filter(modelId -> !modelId.isBlank())
                .distinct()
                .toList();
        if (candidateModelIds.size() < llmPlayerCount) {
            throw new IllegalArgumentException("RANDOM_POOL requires at least " + llmPlayerCount + " distinct candidateModelIds");
        }
        candidateModelIds.forEach(modelProfileCatalogService::requireEnabledProfile);
        return new LlmSelectionConfig(LlmSelectionMode.RANDOM_POOL, Map.of(), Map.of(), candidateModelIds);
    }

    private Map<String, RoleDefinition> roleDefinitionsFor(SetupTemplate setupTemplate) {
        Map<String, RoleDefinition> roleDefinitions = new LinkedHashMap<>();
        for (String roleId : setupTemplate.roleIds()) {
            roleDefinitions.put(roleId, configRegistry.requireRole(roleId));
        }
        return roleDefinitions;
    }

    private GameRuntimeState requireState(String gameId) {
        return gameSessionService.find(gameId).orElseGet(() -> {
            RecoveryResult recovered = recoveryService.recover(gameId);
            gameSessionService.save(recovered.state());
            return recovered.state();
        });
    }

    private GameSummaryResponse summary(GameRuntimeState state, String message) {
        GameSummaryResponse response = new GameSummaryResponse();
        response.setGameId(state.generatedGameId());
        response.setStatus(state.status().name());
        response.setMessage(message);
        return response;
    }

    private GameEventEntryResponse toEventResponse(GameEventRecord record) {
        GameEventEntryResponse response = new GameEventEntryResponse();
        response.setSeqNo(record.seqNo());
        response.setType(record.type());
        response.setPhase(record.phase());
        response.setActorId(record.actorPlayerId());
        response.setVisibility(record.visibility());
        response.setPayload(readPayload(record.payloadJson()));
        response.setCreatedAt(record.createdAt());
        return response;
    }

    private GameEventEntryResponse toReplayResponse(com.example.avalon.runtime.recovery.ReplayProjectionStep step) {
        GameEventEntryResponse response = new GameEventEntryResponse();
        response.setSeqNo(step.seqNo());
        response.setType(step.eventType());
        response.setPhase(step.phase());
        response.setActorId(step.actorId());
        response.setReplayKind(step.replayKind());
        response.setSummary(step.summary());
        response.setPayload(step.payload());
        response.setCreatedAt(step.createdAt());
        return response;
    }

    private GameAuditEntryResponse toAuditResponse(AuditRecord record) {
        GameAuditEntryResponse response = new GameAuditEntryResponse();
        response.setAuditId(record.auditId());
        response.setEventSeqNo(record.eventSeqNo());
        response.setPlayerId(record.playerId());
        response.setVisibility(record.visibility());
        response.setInputContextJson(record.inputContextJson());
        response.setRawModelResponseJson(record.rawModelResponseJson());
        response.setParsedActionJson(record.parsedActionJson());
        response.setAuditReasonJson(record.auditReasonJson());
        response.setValidationResultJson(record.validationResultJson());
        response.setErrorMessage(record.errorMessage());
        response.setCreatedAt(record.createdAt());
        return response;
    }

    private String nextRequiredActor(GameRuntimeState state) {
        return switch (state.status()) {
            case WAITING -> "SYSTEM";
            case RECOVERING -> "SYSTEM";
            case PAUSED, ENDED -> null;
            case RUNNING -> switch (state.phase()) {
                case DISCUSSION -> state.playerByIndex(state.discussionSpeakerIndex()).playerId();
                case TEAM_PROPOSAL -> state.playerBySeat(state.currentLeaderSeat()).playerId();
                case TEAM_VOTE -> state.playerByIndex(state.voteIndex()).playerId();
                case MISSION_ACTION -> {
                    if (state.currentProposalTeam().isEmpty() || state.missionIndex() >= state.currentProposalTeam().size()) {
                        yield null;
                    }
                    yield state.playerBySeat(state.currentProposalTeam().get(state.missionIndex())).playerId();
                }
                case ASSASSINATION -> state.roleAssignments().values().stream()
                        .filter(assignment -> assignment.roleId().equals(state.setup().ruleSetDefinition().assassinationRule().assassinRoleId()))
                        .map(assignment -> assignment.playerId())
                        .findFirst()
                        .orElse(null);
                default -> "SYSTEM";
            };
        };
    }

    private String waitingReason(GameRuntimeState state) {
        return switch (state.status()) {
            case WAITING -> "等待开始游戏";
            case RECOVERING -> "游戏恢复中";
            case PAUSED -> "游戏已暂停";
            case ENDED -> "游戏已结束";
            case RUNNING -> switch (state.phase()) {
                case DISCUSSION -> "等待玩家公开发言";
                case TEAM_PROPOSAL -> "等待队长提出任务队伍";
                case TEAM_VOTE -> "等待玩家对队伍投票";
                case MISSION_ACTION -> "等待任务队员执行任务";
                case MISSION_RESOLUTION -> "等待任务结果结算";
                case ASSASSINATION -> "等待刺客选择刺杀目标";
                case ROLE_REVEAL -> "等待身份分配完成";
                case GAME_END -> "等待游戏结束结算";
                case ROUND_START -> "等待新一轮开始";
                case WAITING_FOR_HUMAN_INPUT -> "等待人工输入";
            };
        };
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize payload JSON", e);
        }
    }

    private List<String> distinctRoleIds(SetupTemplate setupTemplate) {
        return new ArrayList<>(new LinkedHashSet<>(setupTemplate.roleIds()));
    }
}
