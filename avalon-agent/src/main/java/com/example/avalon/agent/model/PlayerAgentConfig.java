package com.example.avalon.agent.model;

import com.example.avalon.agent.service.AgentPolicyIds;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerAgentConfig {
    private String playerId;
    private String promptProfileId;
    private String strategyProfileId;
    private String agentPolicyId;
    private String outputSchemaVersion;
    private String auditLevel;
    private ModelProfile modelProfile = new ModelProfile();
    private Map<String, ModelProfile> modelSlots = new LinkedHashMap<>();

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPromptProfileId() {
        return promptProfileId;
    }

    public void setPromptProfileId(String promptProfileId) {
        this.promptProfileId = promptProfileId;
    }

    public String getStrategyProfileId() {
        return strategyProfileId;
    }

    public void setStrategyProfileId(String strategyProfileId) {
        this.strategyProfileId = strategyProfileId;
    }

    public String getAgentPolicyId() {
        return agentPolicyId;
    }

    public void setAgentPolicyId(String agentPolicyId) {
        this.agentPolicyId = agentPolicyId;
    }

    public String getOutputSchemaVersion() {
        return outputSchemaVersion;
    }

    public void setOutputSchemaVersion(String outputSchemaVersion) {
        this.outputSchemaVersion = outputSchemaVersion;
    }

    public String getAuditLevel() {
        return auditLevel;
    }

    public void setAuditLevel(String auditLevel) {
        this.auditLevel = auditLevel;
    }

    public ModelProfile getModelProfile() {
        return modelProfile;
    }

    public void setModelProfile(ModelProfile modelProfile) {
        this.modelProfile = modelProfile == null ? new ModelProfile() : modelProfile;
    }

    public Map<String, ModelProfile> getModelSlots() {
        return modelSlots;
    }

    public void setModelSlots(Map<String, ModelProfile> modelSlots) {
        this.modelSlots = modelSlots == null ? new LinkedHashMap<>() : new LinkedHashMap<>(modelSlots);
    }

    public String effectiveStrategyProfileId() {
        if (strategyProfileId != null && !strategyProfileId.isBlank()) {
            return strategyProfileId;
        }
        if (promptProfileId != null && !promptProfileId.isBlank()) {
            return promptProfileId;
        }
        return null;
    }

    public String effectiveAgentPolicyId() {
        if (agentPolicyId == null || agentPolicyId.isBlank()) {
            return AgentPolicyIds.LEGACY_SINGLE_SHOT;
        }
        return agentPolicyId;
    }

    public ModelProfile modelForSlot(String slotId) {
        if (slotId != null) {
            ModelProfile slotProfile = modelSlots.get(slotId);
            if (slotProfile != null) {
                return slotProfile;
            }
        }
        return modelProfile == null ? new ModelProfile() : modelProfile;
    }
}
