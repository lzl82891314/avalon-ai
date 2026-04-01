package com.example.avalon.core.game.rule;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.role.enums.KnowledgeRuleType;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import com.example.avalon.core.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassicAvalonRuleEngineTest {
    private final GameRuleEngine engine = new ClassicAvalonRuleEngine();

    @Test
    void progressesFromProposalToVoteAndMissionResolution() {
        var assignments = new com.example.avalon.core.role.service.DeterministicRoleAssignmentService()
                .assignRoles(TestFixtures.waitingSession(), TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles(), 7L);
        var context = new GameRuleContext(
                TestFixtures.runningSession().toBuilder()
                        .phase(GamePhase.TEAM_PROPOSAL)
                        .currentLeaderSeat(1)
                        .build(),
                TestFixtures.classicPlayers(),
                assignments,
                TestFixtures.classicRuleSet(),
                TestFixtures.classicSetupTemplate(),
                TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role))
        );

        var proposalResult = engine.applyAction(context, "P1", new TeamProposalAction(List.of("P1", "P2")));
        assertEquals(GamePhase.TEAM_VOTE, proposalResult.phase());

        var voteSession = proposalResult;
        for (int index = 1; index <= 5; index++) {
            voteSession = engine.applyAction(new GameRuleContext(voteSession, TestFixtures.classicPlayers(), assignments, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role))), "P" + index, new TeamVoteAction(VoteChoice.APPROVE));
        }
        assertEquals(GamePhase.MISSION_ACTION, voteSession.phase());

        var missionContext = new GameRuleContext(voteSession, TestFixtures.classicPlayers(), assignments, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role)));
        var afterFirstMission = engine.applyAction(missionContext, "P1", new MissionAction(MissionChoice.SUCCESS));
        var afterMissionResolution = engine.applyAction(new GameRuleContext(afterFirstMission, TestFixtures.classicPlayers(), assignments, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role))), "P2", new MissionAction(MissionChoice.SUCCESS));

        assertEquals(GamePhase.DISCUSSION, afterMissionResolution.phase());
        assertEquals(2, afterMissionResolution.roundNo());
        assertEquals(1, afterMissionResolution.successfulMissionCount());
    }

    @Test
    void entersAssassinationAfterThreeSuccessfulMissions() {
        var assignments = new com.example.avalon.core.role.service.DeterministicRoleAssignmentService()
                .assignRoles(TestFixtures.waitingSession(), TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles(), 7L);
        var session = TestFixtures.runningSession().toBuilder()
                .phase(GamePhase.MISSION_ACTION)
                .roundNo(3)
                .successfulMissionCount(2)
                .currentTeamPlayerIds(List.of("P1", "P2"))
                .build();
        var context = new GameRuleContext(session, TestFixtures.classicPlayers(), assignments, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role)));

        var afterFirst = engine.applyAction(context, "P1", new MissionAction(MissionChoice.SUCCESS));
        var afterSecond = engine.applyAction(new GameRuleContext(afterFirst, TestFixtures.classicPlayers(), assignments, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role))), "P2", new MissionAction(MissionChoice.SUCCESS));

        assertEquals(GamePhase.ASSASSINATION, afterSecond.phase());
        assertEquals(3, afterSecond.successfulMissionCount());
    }

    @Test
    void evilWinsWhenMerlinIsAssassinated() {
        var assignments = new com.example.avalon.core.role.service.DeterministicRoleAssignmentService()
                .assignRoles(TestFixtures.waitingSession(), TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles(), 7L);
        var assassinPlayerId = assignments.stream().filter(assignment -> "ASSASSIN".equals(assignment.roleId())).findFirst().orElseThrow().playerId();
        var merlinPlayerId = assignments.stream().filter(assignment -> "MERLIN".equals(assignment.roleId())).findFirst().orElseThrow().playerId();
        var session = TestFixtures.runningSession().toBuilder()
                .phase(GamePhase.ASSASSINATION)
                .successfulMissionCount(3)
                .build();
        var context = new GameRuleContext(session, TestFixtures.classicPlayers(), assignments, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(role -> role.roleId(), role -> role)));

        var result = engine.applyAction(context, assassinPlayerId, new com.example.avalon.core.game.model.AssassinationAction(merlinPlayerId));

        assertEquals(GameStatus.ENDED, result.status());
        assertEquals(com.example.avalon.core.game.enums.Camp.EVIL, result.winnerCamp());
    }

    @Test
    void roundFourNeedsTwoFailsForSevenPlayerGames() {
        RuleSetDefinition ruleSet = sevenPlayerRuleSet();
        SetupTemplate setupTemplate = sevenPlayerSetupTemplate();
        List<RoleDefinition> roles = sevenPlayerRoles();
        List<GamePlayer> players = sevenPlayerPlayers();
        var assignments = new com.example.avalon.core.role.service.DeterministicRoleAssignmentService()
                .assignRoles(
                        GameRuleContextFixtures.waitingSession(ruleSet, setupTemplate),
                        ruleSet,
                        setupTemplate,
                        players,
                        roles,
                        11L
                );
        String evilPlayerId = assignments.stream()
                .filter(assignment -> assignment.camp() == com.example.avalon.core.game.enums.Camp.EVIL)
                .findFirst()
                .orElseThrow()
                .playerId();
        List<String> teamPlayerIds = new java.util.ArrayList<>();
        teamPlayerIds.add(evilPlayerId);
        assignments.stream()
                .map(com.example.avalon.core.role.model.RoleAssignment::playerId)
                .filter(playerId -> !playerId.equals(evilPlayerId))
                .limit(3)
                .forEach(teamPlayerIds::add);
        Map<String, RoleDefinition> roleMap = roles.stream().collect(java.util.stream.Collectors.toMap(RoleDefinition::roleId, role -> role));
        var context = new GameRuleContext(
                GameRuleContextFixtures.runningSession(ruleSet, setupTemplate).toBuilder()
                        .phase(GamePhase.MISSION_ACTION)
                        .roundNo(4)
                        .currentTeamPlayerIds(teamPlayerIds)
                        .build(),
                players,
                assignments,
                ruleSet,
                setupTemplate,
                roleMap
        );

        var afterFirst = engine.applyAction(context, teamPlayerIds.get(0), new MissionAction(MissionChoice.FAIL));
        var afterSecond = engine.applyAction(new GameRuleContext(afterFirst, players, assignments, ruleSet, setupTemplate, roleMap), teamPlayerIds.get(1), new MissionAction(MissionChoice.SUCCESS));
        var afterThird = engine.applyAction(new GameRuleContext(afterSecond, players, assignments, ruleSet, setupTemplate, roleMap), teamPlayerIds.get(2), new MissionAction(MissionChoice.SUCCESS));
        var afterFourth = engine.applyAction(new GameRuleContext(afterThird, players, assignments, ruleSet, setupTemplate, roleMap), teamPlayerIds.get(3), new MissionAction(MissionChoice.SUCCESS));

        assertEquals(GamePhase.DISCUSSION, afterFourth.phase());
        assertEquals(1, afterFourth.successfulMissionCount());
    }

    @Test
    void goodPlayersCannotSubmitFailMissionActions() {
        var assignments = new com.example.avalon.core.role.service.DeterministicRoleAssignmentService()
                .assignRoles(TestFixtures.waitingSession(), TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles(), 7L);
        String goodPlayerId = assignments.stream()
                .filter(assignment -> assignment.camp() == com.example.avalon.core.game.enums.Camp.GOOD)
                .findFirst()
                .orElseThrow()
                .playerId();
        var session = TestFixtures.runningSession().toBuilder()
                .phase(GamePhase.MISSION_ACTION)
                .currentTeamPlayerIds(List.of(goodPlayerId))
                .build();
        var context = new GameRuleContext(
                session,
                TestFixtures.classicPlayers(),
                assignments,
                TestFixtures.classicRuleSet(),
                TestFixtures.classicSetupTemplate(),
                TestFixtures.classicRoles().stream().collect(java.util.stream.Collectors.toMap(RoleDefinition::roleId, role -> role))
        );

        assertThrows(
                com.example.avalon.core.common.exception.GameRuleViolationException.class,
                () -> engine.applyAction(context, goodPlayerId, new MissionAction(MissionChoice.FAIL))
        );
    }

    private RuleSetDefinition sevenPlayerRuleSet() {
        return new RuleSetDefinition(
                "avalon-classic-7p-v2",
                "Avalon Classic 7 Players V2",
                "2.0.0",
                7,
                7,
                List.of(
                        new RoundTeamSizeRule(1, 2),
                        new RoundTeamSizeRule(2, 3),
                        new RoundTeamSizeRule(3, 3),
                        new RoundTeamSizeRule(4, 4),
                        new RoundTeamSizeRule(5, 4)
                ),
                Map.of(1, 1, 2, 1, 3, 1, 4, 2, 5, 1),
                List.of("classic-7p-v2"),
                new AssassinationRuleDefinition(true, "ASSASSIN", "MERLIN"),
                new VisibilityPolicyDefinition(true),
                true
        );
    }

    private SetupTemplate sevenPlayerSetupTemplate() {
        return new SetupTemplate(
                "classic-7p-v2",
                7,
                true,
                List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "LOYAL_SERVANT", "MORGANA", "ASSASSIN", "MORDRED")
        );
    }

    private List<RoleDefinition> sevenPlayerRoles() {
        return List.of(
                new RoleDefinition("MERLIN", "Merlin", com.example.avalon.core.game.enums.Camp.GOOD, "desc",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_PLAYERS_BY_CAMP, com.example.avalon.core.game.enums.Camp.EVIL, List.of(), List.of("MORDRED"))),
                        List.of(), true, true, true, false, List.of()),
                new RoleDefinition("PERCIVAL", "Percival", com.example.avalon.core.game.enums.Camp.GOOD, "desc",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ROLE_AMBIGUITY, null, List.of("MERLIN", "MORGANA"), List.of())),
                        List.of(), true, true, true, false, List.of()),
                new RoleDefinition("LOYAL_SERVANT", "Loyal Servant", com.example.avalon.core.game.enums.Camp.GOOD, "desc",
                        List.of(), List.of(), true, true, true, false, List.of()),
                new RoleDefinition("MORGANA", "Morgana", com.example.avalon.core.game.enums.Camp.EVIL, "desc",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of(), true, true, true, false, List.of()),
                new RoleDefinition("ASSASSIN", "Assassin", com.example.avalon.core.game.enums.Camp.EVIL, "desc",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of("ASSASSINATE"), true, true, true, true, List.of()),
                new RoleDefinition("MORDRED", "Mordred", com.example.avalon.core.game.enums.Camp.EVIL, "desc",
                        List.of(new KnowledgeRuleDefinition(KnowledgeRuleType.SEE_ALLIED_EVIL_PLAYERS, null, List.of(), List.of("OBERON"))),
                        List.of(), true, true, true, false, List.of())
        );
    }

    private List<GamePlayer> sevenPlayerPlayers() {
        return List.of(
                new GamePlayer("game-7", "P1", 1, "P1", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED),
                new GamePlayer("game-7", "P2", 2, "P2", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED),
                new GamePlayer("game-7", "P3", 3, "P3", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED),
                new GamePlayer("game-7", "P4", 4, "P4", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED),
                new GamePlayer("game-7", "P5", 5, "P5", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED),
                new GamePlayer("game-7", "P6", 6, "P6", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED),
                new GamePlayer("game-7", "P7", 7, "P7", PlayerControllerType.SCRIPTED, "{}", com.example.avalon.core.game.enums.PlayerConnectionState.CONNECTED)
        );
    }

    private static final class GameRuleContextFixtures {
        private static com.example.avalon.core.game.model.GameSession waitingSession(RuleSetDefinition ruleSet, SetupTemplate setupTemplate) {
            Instant now = Instant.parse("2026-03-23T00:00:00Z");
            return com.example.avalon.core.game.model.GameSession.createWaiting("game-7", ruleSet.ruleSetId(), ruleSet.version(), setupTemplate.templateId(), 42L, now, now);
        }

        private static com.example.avalon.core.game.model.GameSession runningSession(RuleSetDefinition ruleSet, SetupTemplate setupTemplate) {
            return waitingSession(ruleSet, setupTemplate).startRunning(Instant.parse("2026-03-23T00:00:00Z"));
        }
    }
}
