package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.ModelProfile;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.setup.model.AssassinationRuleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.setup.model.VisibilityPolicyDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentTurnRequestFactoryTest {
    private final AgentTurnRequestFactory factory = new AgentTurnRequestFactory();

    @Test
    void shouldPopulateProviderModelAndOptionsFromAgentConfig() {
        PlayerAgentConfig agentConfig = new PlayerAgentConfig();
        ModelProfile modelProfile = new ModelProfile();
        modelProfile.setModelId("openai-gpt-5.2");
        modelProfile.setProvider("openai");
        modelProfile.setModelName("gpt-5.2");
        modelProfile.setTemperature(0.3);
        modelProfile.setMaxTokens(240);
        modelProfile.setProviderOptions(Map.of(
                "apiKeyEnv", "OPENAI_API_KEY",
                "response_format", Map.of("type", "json_object")
        ));
        agentConfig.setModelProfile(modelProfile);
        agentConfig.setOutputSchemaVersion("v2");

        AgentTurnRequest request = factory.create(teamVoteContext(), agentConfig);

        assertEquals("openai-gpt-5.2", request.getModelId());
        assertEquals("openai", request.getProvider());
        assertEquals("gpt-5.2", request.getModelName());
        assertEquals(0.3, request.getTemperature());
        assertEquals(240, request.getMaxTokens());
        assertEquals("OPENAI_API_KEY", request.getProviderOptions().get("apiKeyEnv"));
        assertEquals("v2", request.getOutputSchemaVersion());
    }

    @Test
    void shouldDefaultToNoopProviderWhenModelProfileIsBlank() {
        AgentTurnRequest request = factory.create(teamVoteContext(), new PlayerAgentConfig());

        assertNull(request.getModelId());
        assertEquals("noop", request.getProvider());
        assertNull(request.getModelName());
        assertNull(request.getTemperature());
        assertNull(request.getMaxTokens());
        assertEquals(Map.of(), request.getProviderOptions());
    }

    private PlayerTurnContext teamVoteContext() {
        RuleSetDefinition ruleSetDefinition = new RuleSetDefinition(
                "avalon-classic-5p-v1",
                "Avalon Classic 5 Players",
                "1.0.0",
                5,
                5,
                List.of(
                        new RoundTeamSizeRule(1, 2),
                        new RoundTeamSizeRule(2, 3),
                        new RoundTeamSizeRule(3, 2),
                        new RoundTeamSizeRule(4, 3),
                        new RoundTeamSizeRule(5, 3)
                ),
                Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1),
                List.of("classic-5p-v1"),
                new AssassinationRuleDefinition(true, "ASSASSIN", "MERLIN"),
                new VisibilityPolicyDefinition(true),
                true
        );
        return new PlayerTurnContext(
                "game-1",
                1,
                GamePhase.TEAM_VOTE.name(),
                "P1",
                1,
                "MERLIN",
                new PublicGameSnapshot(
                        "game-1",
                        GameStatus.RUNNING,
                        GamePhase.TEAM_VOTE,
                        1,
                        0,
                        0,
                        0,
                        1,
                        List.of("P1", "P2"),
                        null,
                        null,
                        List.of(
                                new PublicPlayerSummary("game-1", "P1", 1, "P1", PlayerControllerType.LLM, PlayerConnectionState.DISCONNECTED),
                                new PublicPlayerSummary("game-1", "P2", 2, "P2", PlayerControllerType.SCRIPTED, PlayerConnectionState.DISCONNECTED)
                        ),
                        Instant.parse("2026-03-24T00:00:00Z")
                ),
                new PlayerPrivateView(
                        "game-1",
                        "P1",
                        1,
                        "MERLIN",
                        Camp.GOOD,
                        new PlayerPrivateKnowledge(List.of(), List.of()),
                        List.of()
                ),
                PlayerMemoryState.empty("game-1", "P1", "MERLIN", Camp.GOOD, Instant.parse("2026-03-24T00:00:00Z")),
                new AllowedActionSet("game-1", "P1", 1, EnumSet.of(PlayerActionType.TEAM_VOTE)),
                ruleSetDefinition,
                new SetupTemplate("classic-5p-v1", 5, true, List.of("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN")),
                "Classic five-player Avalon"
        );
    }
}
