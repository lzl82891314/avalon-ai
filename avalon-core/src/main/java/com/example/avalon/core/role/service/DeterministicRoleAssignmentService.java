package com.example.avalon.core.role.service;

import com.example.avalon.core.common.exception.RoleAssignmentException;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class DeterministicRoleAssignmentService implements RoleAssignmentService {
    @Override
    public List<RoleAssignment> assignRoles(
            GameSession session,
            RuleSetDefinition ruleSet,
            SetupTemplate setupTemplate,
            List<GamePlayer> players,
            Collection<RoleDefinition> roleDefinitions,
            long seed
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(ruleSet, "ruleSet");
        Objects.requireNonNull(setupTemplate, "setupTemplate");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(roleDefinitions, "roleDefinitions");

        if (players.size() != setupTemplate.playerCount()) {
            throw new RoleAssignmentException("Player count does not match setup template");
        }
        if (setupTemplate.roleIds().size() != players.size()) {
            throw new RoleAssignmentException("Role count does not match player count");
        }

        Map<String, RoleDefinition> roleDefinitionMap = new LinkedHashMap<>();
        for (RoleDefinition roleDefinition : roleDefinitions) {
            roleDefinitionMap.put(roleDefinition.roleId(), roleDefinition);
        }

        List<String> shuffledRoleIds = new ArrayList<>(setupTemplate.roleIds());
        java.util.Collections.shuffle(shuffledRoleIds, new Random(seed));

        List<GamePlayer> orderedPlayers = players.stream()
                .sorted(Comparator.comparingInt(GamePlayer::seatNo))
                .toList();

        List<RoleAssignment> assignments = new ArrayList<>();
        for (int index = 0; index < orderedPlayers.size(); index++) {
            GamePlayer player = orderedPlayers.get(index);
            String roleId = shuffledRoleIds.get(index);
            RoleDefinition roleDefinition = roleDefinitionMap.get(roleId);
            if (roleDefinition == null) {
                throw new RoleAssignmentException("Missing role definition for roleId: " + roleId);
            }
            assignments.add(new RoleAssignment(
                    session.gameId(),
                    player.playerId(),
                    player.seatNo(),
                    roleId,
                    roleDefinition.camp(),
                    null,
                    Instant.now()
            ));
        }

        List<RoleAssignment> finalAssignments = new ArrayList<>();
        for (RoleAssignment assignment : assignments) {
            RoleDefinition roleDefinition = roleDefinitionMap.get(assignment.roleId());
            PlayerPrivateKnowledge privateKnowledge = buildPrivateKnowledge(
                    assignment.playerId(),
                    orderedPlayers,
                    assignments,
                    roleDefinitionMap,
                    roleDefinition
            );
            finalAssignments.add(new RoleAssignment(
                    assignment.gameId(),
                    assignment.playerId(),
                    assignment.seatNo(),
                    assignment.roleId(),
                    assignment.camp(),
                    privateKnowledge,
                    assignment.assignedAt()
            ));
        }

        return List.copyOf(finalAssignments);
    }

    private PlayerPrivateKnowledge buildPrivateKnowledge(
            String playerId,
            List<GamePlayer> allPlayers,
            List<RoleAssignment> allAssignments,
            Map<String, RoleDefinition> roleDefinitionMap,
            RoleDefinition roleDefinition
    ) {
        List<VisiblePlayerInfo> visiblePlayers = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (KnowledgeRuleDefinition knowledgeRule : roleDefinition.knowledgeRules()) {
            switch (knowledgeRule.type()) {
                case SEE_PLAYERS_BY_CAMP -> addCampVisibility(visiblePlayers, allPlayers, allAssignments, roleDefinitionMap, playerId, knowledgeRule);
                case SEE_PLAYERS_BY_ROLE -> addRoleVisibility(visiblePlayers, allPlayers, allAssignments, roleDefinitionMap, playerId, knowledgeRule);
                case SEE_ALLIED_EVIL_PLAYERS -> addAlliedEvilVisibility(visiblePlayers, allPlayers, allAssignments, roleDefinitionMap, playerId, knowledgeRule);
            }
        }

        notes.add(defaultKnowledgeNote(roleDefinition.roleId()));
        return new PlayerPrivateKnowledge(visiblePlayers, notes);
    }

    private void addCampVisibility(
            List<VisiblePlayerInfo> visiblePlayers,
            List<GamePlayer> allPlayers,
            List<RoleAssignment> assignments,
            Map<String, RoleDefinition> roleDefinitionMap,
            String observerPlayerId,
            KnowledgeRuleDefinition rule
    ) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (candidateRole.camp() != rule.targetCamp() || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    candidateRole.roleId(),
                    candidateRole.camp(),
                    List.of(candidateRole.roleId())
            ));
        }
    }

    private void addRoleVisibility(
            List<VisiblePlayerInfo> visiblePlayers,
            List<GamePlayer> allPlayers,
            List<RoleAssignment> assignments,
            Map<String, RoleDefinition> roleDefinitionMap,
            String observerPlayerId,
            KnowledgeRuleDefinition rule
    ) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (!rule.targetRoleIds().contains(candidateRole.roleId()) || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    null,
                    candidateRole.camp(),
                    rule.targetRoleIds()
            ));
        }
    }

    private void addAlliedEvilVisibility(
            List<VisiblePlayerInfo> visiblePlayers,
            List<GamePlayer> allPlayers,
            List<RoleAssignment> assignments,
            Map<String, RoleDefinition> roleDefinitionMap,
            String observerPlayerId,
            KnowledgeRuleDefinition rule
    ) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (candidateRole.camp() != Camp.EVIL || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    candidateRole.roleId(),
                    candidateRole.camp(),
                    List.of(candidateRole.roleId())
            ));
        }
    }

    private RoleDefinition roleDefinitionByPlayer(
            List<RoleAssignment> assignments,
            Map<String, RoleDefinition> roleDefinitionMap,
            String playerId
    ) {
        for (RoleAssignment assignment : assignments) {
            if (Objects.equals(assignment.playerId(), playerId)) {
                return roleDefinitionMap.get(assignment.roleId());
            }
        }
        throw new RoleAssignmentException("Role assignment missing for player: " + playerId);
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
