package com.example.avalon.core.role.service;

import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeterministicRoleAssignmentServiceTest {
    private final RoleAssignmentService service = new DeterministicRoleAssignmentService();

    @Test
    void assignsRolesDeterministicallyForSameSeed() {
        GameSession session = TestFixtures.waitingSession();
        List<String> first = service.assignRoles(session, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles(), 7L)
                .stream()
                .map(roleAssignment -> roleAssignment.roleId())
                .toList();
        List<String> second = service.assignRoles(session, TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), TestFixtures.classicRoles(), 7L)
                .stream()
                .map(roleAssignment -> roleAssignment.roleId())
                .toList();

        assertEquals(first, second);
        assertEquals(5, first.stream().distinct().count());
    }

    @Test
    void rejectsMissingRoleDefinition() {
        var roles = TestFixtures.classicRoles().subList(0, 4);
        assertThrows(RuntimeException.class,
                () -> service.assignRoles(TestFixtures.waitingSession(), TestFixtures.classicRuleSet(), TestFixtures.classicSetupTemplate(), TestFixtures.classicPlayers(), roles, 7L));
    }
}

