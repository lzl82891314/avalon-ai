package com.example.avalon.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class StructuredInferenceRequest {
    private String modelSlotId;
    private String modelId;
    private String provider;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> providerOptions = new LinkedHashMap<>();
    private String developerPrompt;
    private String userPrompt;

    public String getModelSlotId() {
        return modelSlotId;
    }

    public void setModelSlotId(String modelSlotId) {
        this.modelSlotId = modelSlotId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Map<String, Object> getProviderOptions() {
        return providerOptions;
    }

    public void setProviderOptions(Map<String, Object> providerOptions) {
        this.providerOptions = providerOptions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providerOptions);
    }

    public String getDeveloperPrompt() {
        return developerPrompt;
    }

    public void setDeveloperPrompt(String developerPrompt) {
        this.developerPrompt = developerPrompt;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }
}
