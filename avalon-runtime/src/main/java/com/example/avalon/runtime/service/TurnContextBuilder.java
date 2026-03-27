package com.example.avalon.runtime.service;

import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.PlayerRegistration;

import java.util.List;
import java.util.stream.Collectors;

public class TurnContextBuilder {
    private final VisibilityService visibilityService;
    private final RuntimeCoreContextFactory contextFactory;
    private final com.example.avalon.core.game.rule.GameRuleEngine coreRuleEngine;

    public TurnContextBuilder(VisibilityService visibilityService) {
        this(visibilityService, new RuntimeCoreContextFactory(), new com.example.avalon.core.game.rule.ClassicAvalonRuleEngine());
    }

    TurnContextBuilder(VisibilityService visibilityService,
                       RuntimeCoreContextFactory contextFactory,
                       com.example.avalon.core.game.rule.GameRuleEngine coreRuleEngine) {
        this.visibilityService = visibilityService;
        this.contextFactory = contextFactory;
        this.coreRuleEngine = coreRuleEngine;
    }

    public PlayerTurnContext build(GameRuntimeState state, PlayerRegistration player) {
        RoleAssignment assignment = state.requireRoleAssignmentBySeat(player.seatNo());
        PlayerPrivateView privateView = visibilityService.buildPrivateView(state, assignment);
        AllowedActionSet allowedActions = coreRuleEngine.allowedActions(contextFactory.toRuleContext(state), player.playerId());
        return new PlayerTurnContext(
                state.generatedGameId(),
                state.roundNo(),
                state.phase().name(),
                player.playerId(),
                player.seatNo(),
                assignment.roleId(),
                publicSnapshot(state),
                privateView,
                PlayerMemoryState.empty(state.generatedGameId(), player.playerId(), assignment.roleId(), assignment.camp(), state.updatedAt()),
                allowedActions,
                state.runtimeRuleSetDefinition(),
                state.runtimeSetupTemplate(),
                "经典五人阿瓦隆");
    }

    private PublicGameSnapshot publicSnapshot(GameRuntimeState state) {
        List<String> currentTeamPlayerIds = state.currentProposalTeam().stream()
                .map(seat -> state.playerBySeat(seat).playerId())
                .toList();
        List<PublicPlayerSummary> players = state.players().stream()
                .map(player -> new PublicPlayerSummary(
                        state.generatedGameId(),
                        player.playerId(),
                        player.seatNo(),
                        player.displayName(),
                        player.controllerType(),
                        com.example.avalon.core.game.enums.PlayerConnectionState.DISCONNECTED))
                .collect(Collectors.toList());
        return new PublicGameSnapshot(
                state.generatedGameId(),
                state.status(),
                state.phase(),
                state.roundNo(),
                state.failedTeamVoteCount(),
                state.approvedMissionRounds().size(),
                state.failedMissionRounds().size(),
                state.currentLeaderSeat(),
                currentTeamPlayerIds,
                state.winnerCamp(),
                state.winnerCamp() == null ? null : state.winnerCamp().name(),
                players,
                state.updatedAt());
    }
}
