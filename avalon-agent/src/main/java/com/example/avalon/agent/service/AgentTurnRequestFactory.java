package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.ModelProfile;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentTurnRequestFactory {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AgentTurnRequest create(PlayerTurnContext context, PlayerAgentConfig agentConfig) {
        return create(context, agentConfig, "actor");
    }

    public AgentTurnRequest create(PlayerTurnContext context, PlayerAgentConfig agentConfig, String modelSlotId) {
        PlayerAgentConfig effectiveConfig = agentConfig == null ? new PlayerAgentConfig() : agentConfig;
        String slotId = modelSlotId == null || modelSlotId.isBlank() ? "actor" : modelSlotId;
        ModelProfile modelProfile = modelProfile(effectiveConfig, slotId);
        AgentTurnRequest request = new AgentTurnRequest();
        request.setGameId(context.gameId());
        request.setRoundNo(context.roundNo());
        request.setPhase(context.phase());
        request.setPlayerId(context.playerId());
        request.setSeatNo(context.seatNo());
        request.setRoleId(context.roleId());
        request.setAgentPolicyId(effectiveConfig.effectiveAgentPolicyId());
        request.setStrategyProfileId(effectiveConfig.effectiveStrategyProfileId());
        request.setModelSlotId(slotId);
        request.setModelId(modelProfile.getModelId());
        request.setProvider(provider(modelProfile));
        request.setModelName(modelName(modelProfile));
        request.setTemperature(modelProfile.getTemperature());
        request.setMaxTokens(modelProfile.getMaxTokens());
        request.setProviderOptions(modelProfile.getProviderOptions());
        request.setPrivateKnowledge(privateKnowledge(context));
        request.setPublicState(publicState(context));
        request.setMemory(memory(context));
        request.setAllowedActions(context.allowedActions().allowedActionTypes().stream().map(Enum::name).toList());
        request.setRulesSummary(context.rulesSummary());
        request.setOutputSchemaVersion(defaultString(effectiveConfig.getOutputSchemaVersion(), "v1"));
        return request;
    }

    private Map<String, Object> privateKnowledge(PlayerTurnContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("camp", context.privateView().camp().name());
        payload.put("notes", context.privateView().knowledge().notes());
        payload.put("visiblePlayers", context.privateView().knowledge().visiblePlayers().stream()
                .map(this::visiblePlayerPayload)
                .toList());
        return payload;
    }

    private Map<String, Object> visiblePlayerPayload(VisiblePlayerInfo visiblePlayer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", visiblePlayer.playerId());
        payload.put("seatNo", visiblePlayer.seatNo());
        payload.put("displayName", visiblePlayer.displayName());
        payload.put("exactRoleId", visiblePlayer.exactRoleId());
        payload.put("camp", visiblePlayer.camp() == null ? null : visiblePlayer.camp().name());
        payload.put("candidateRoleIds", List.copyOf(visiblePlayer.candidateRoleIds()));
        return payload;
    }

    private Map<String, Object> publicState(PlayerTurnContext context) {
        Map<String, Object> payload = objectMapper.convertValue(context.publicState(), new TypeReference<Map<String, Object>>() { });
        payload.put("teamSize", context.ruleSetDefinition().teamSizeForRound(context.roundNo()));
        payload.put("playerCount", context.setupTemplate().playerCount());
        return payload;
    }

    private Map<String, Object> memory(PlayerTurnContext context) {
        return objectMapper.convertValue(context.memoryState(), new TypeReference<Map<String, Object>>() { });
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String provider(ModelProfile modelProfile) {
        return defaultString(modelProfile.getProvider(), "noop");
    }

    private String modelName(ModelProfile modelProfile) {
        String modelName = modelProfile.getModelName();
        return modelName == null || modelName.isBlank() ? null : modelName;
    }

    private ModelProfile modelProfile(PlayerAgentConfig agentConfig, String modelSlotId) {
        ModelProfile modelProfile = agentConfig.modelForSlot(modelSlotId);
        return modelProfile == null ? new ModelProfile() : modelProfile;
    }
}
