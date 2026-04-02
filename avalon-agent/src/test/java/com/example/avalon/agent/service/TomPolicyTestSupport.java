package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.ModelProfile;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.RawCompletionMetadata;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TomPolicyTestSupport {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    PlayerAgentConfig config(String policyId, String strategyProfileId) {
        PlayerAgentConfig config = new PlayerAgentConfig();
        config.setAgentPolicyId(policyId);
        config.setStrategyProfileId(strategyProfileId);
        return config;
    }

    PlayerAgentConfig configWithCriticSlot(String policyId, String strategyProfileId) {
        PlayerAgentConfig config = config(policyId, strategyProfileId);

        ModelProfile actor = new ModelProfile();
        actor.setModelId("actor-model");
        actor.setProvider("openai");
        actor.setModelName("gpt-5.2");

        ModelProfile critic = new ModelProfile();
        critic.setModelId("critic-model");
        critic.setProvider("openai");
        critic.setModelName("gpt-5.2-mini");

        config.setModelProfile(actor);
        config.setModelSlots(Map.of(
                "actor", actor,
                "critic", critic
        ));
        return config;
    }

    RawCompletionMetadata metadata(String provider, String modelName, long inputTokens, long outputTokens) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider(provider);
        metadata.setModelName(modelName);
        metadata.setInputTokens(inputTokens);
        metadata.setOutputTokens(outputTokens);
        metadata.setAttributes(new LinkedHashMap<>(Map.of("gatewayType", "test")));
        return metadata;
    }

    JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build test JSON", exception);
        }
    }

    RuntimeException truncatedJsonFailure(String provider, String modelName) {
        return new OpenAiCompatibleResponseException(
                "OpenAI-compatible assistant content looked like truncated JSON (shape=truncated_json_candidate, bodyPreview={) [finishReason=length]",
                null,
                provider,
                modelName,
                "length",
                new OpenAiCompatibleMessageAnalysis(
                        true,
                        false,
                        "truncated_json_candidate",
                        "{",
                        null,
                        null
                )
        );
    }

    PlayerTurnContext teamVoteContext() {
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
                                new PublicPlayerSummary("game-1", "P2", 2, "P2", PlayerControllerType.SCRIPTED, PlayerConnectionState.DISCONNECTED),
                                new PublicPlayerSummary("game-1", "P3", 3, "P3", PlayerControllerType.SCRIPTED, PlayerConnectionState.DISCONNECTED)
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
