package com.example.avalon.core.game.rule;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

