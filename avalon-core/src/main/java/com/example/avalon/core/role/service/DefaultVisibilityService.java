package com.example.avalon.core.role.service;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultVisibilityService implements VisibilityService {
    @Override
    public PlayerPrivateView buildPrivateView(GameRuleContext context, String playerId) {
        RoleAssignment assignment = context.roleAssignmentByPlayerId(playerId);
        RoleDefinition roleDefinition = context.roleDefinitionByRoleId(assignment.roleId());

        List<VisiblePlayerInfo> visiblePlayers = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (KnowledgeRuleDefinition knowledgeRule : roleDefinition.knowledgeRules()) {
            switch (knowledgeRule.type()) {
                case SEE_PLAYERS_BY_CAMP -> context.roleAssignments().stream()
                        .filter(candidate -> !Objects.equals(candidate.playerId(), playerId))
                        .filter(candidate -> candidate.camp() == knowledgeRule.targetCamp())
                        .filter(candidate -> !knowledgeRule.exclusions().contains(candidate.roleId()))
                        .forEach(candidate -> visiblePlayers.add(new VisiblePlayerInfo(
                                candidate.playerId(),
                                candidate.seatNo(),
                                context.playerById(candidate.playerId()).displayName(),
                                candidate.roleId(),
                                candidate.camp(),
                                List.of(candidate.roleId())
                        )));
                case SEE_PLAYERS_BY_ROLE -> context.roleAssignments().stream()
                        .filter(candidate -> !Objects.equals(candidate.playerId(), playerId))
                        .filter(candidate -> knowledgeRule.targetRoleIds().contains(candidate.roleId()))
                        .filter(candidate -> !knowledgeRule.exclusions().contains(candidate.roleId()))
                        .forEach(candidate -> visiblePlayers.add(new VisiblePlayerInfo(
                                candidate.playerId(),
                                candidate.seatNo(),
                                context.playerById(candidate.playerId()).displayName(),
                                null,
                                candidate.camp(),
                                knowledgeRule.targetRoleIds()
                        )));
                case SEE_ALLIED_EVIL_PLAYERS -> context.roleAssignments().stream()
                        .filter(candidate -> !Objects.equals(candidate.playerId(), playerId))
                        .filter(candidate -> candidate.camp() == Camp.EVIL)
                        .filter(candidate -> !knowledgeRule.exclusions().contains(candidate.roleId()))
                        .forEach(candidate -> visiblePlayers.add(new VisiblePlayerInfo(
                                candidate.playerId(),
                                candidate.seatNo(),
                                context.playerById(candidate.playerId()).displayName(),
                                candidate.roleId(),
                                candidate.camp(),
                                List.of(candidate.roleId())
                        )));
            }
        }

        notes.add(defaultKnowledgeNote(roleDefinition.roleId()));
        return new PlayerPrivateView(
                context.session().gameId(),
                playerId,
                assignment.seatNo(),
                roleDefinition.roleId(),
                roleDefinition.camp(),
                new PlayerPrivateKnowledge(visiblePlayers, notes),
                roleDefinition.actionCapabilities()
        );
    }

    private String defaultKnowledgeNote(String roleId) {
        return switch (roleId) {
            case "MERLIN" -> "You know evil players except excluded roles.";
            case "PERCIVAL" -> "You see Merlin and Morgana as candidates.";
            case "ASSASSIN" -> "You know allied evil players except excluded roles.";
            default -> "No special private knowledge.";
        };
    }
}

