package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {
    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void shouldRenderCandidateKnowledgeAsUncertainInPrompt() {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setGameId("game-1");
        request.setRoundNo(1);
        request.setPhase("TEAM_VOTE");
        request.setPlayerId("P1");
        request.setSeatNo(1);
        request.setRoleId("PERCIVAL");
        request.setAllowedActions(List.of("TEAM_VOTE"));
        request.setRulesSummary("Classic five-player Avalon");
        request.setPrivateKnowledge(Map.of(
                "camp", "GOOD",
                "notes", List.of("You see Merlin and Morgana as candidates."),
                "visiblePlayers", List.of(
                        Map.of(
                                "playerId", "P3",
                                "displayName", "Cara",
                                "candidateRoleIds", List.of("MERLIN", "MORGANA")
                        ),
                        Map.of(
                                "playerId", "P5",
                                "displayName", "Eva",
                                "candidateRoleIds", List.of("MERLIN", "MORGANA")
                        )
                )
        ));
        request.setMemory(Map.of());
        request.setPublicState(Map.of("phase", "TEAM_VOTE"));
        request.setOutputSchemaVersion("v1");

        String prompt = promptBuilder.build(request);

        assertTrue(prompt.contains("候选身份 [MERLIN, MORGANA]"));
        assertTrue(prompt.contains("这只代表候选集合，不代表你已知真实身份"));
        assertTrue(prompt.contains("绝不能在 privateThought 或 auditReason.reasonSummary 里写出“P5是梅林”"));
    }
}
