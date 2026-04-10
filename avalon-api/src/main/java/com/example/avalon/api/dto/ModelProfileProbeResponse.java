package com.example.avalon.api.dto;

import java.util.ArrayList;
import java.util.List;

public class ModelProfileProbeResponse {
    private String modelId;
    private String provider;
    private String modelName;
    private String baseUrl;
    private Boolean reachable;
    private Boolean structuredCompatible;
    private Boolean avalonCompatible;
    private Boolean admissionEligible;
    private String diagnosis;
    private List<String> failedStages = new ArrayList<>();
    private List<ModelProfileProbeCheckResponse> checks = new ArrayList<>();

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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Boolean getReachable() {
        return reachable;
    }

    public void setReachable(Boolean reachable) {
        this.reachable = reachable;
    }

    public Boolean getStructuredCompatible() {
        return structuredCompatible;
    }

    public void setStructuredCompatible(Boolean structuredCompatible) {
        this.structuredCompatible = structuredCompatible;
    }

    public Boolean getAvalonCompatible() {
        return avalonCompatible;
    }

    public void setAvalonCompatible(Boolean avalonCompatible) {
        this.avalonCompatible = avalonCompatible;
    }

    public Boolean getAdmissionEligible() {
        return admissionEligible;
    }

    public void setAdmissionEligible(Boolean admissionEligible) {
        this.admissionEligible = admissionEligible;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public List<String> getFailedStages() {
        return failedStages;
    }

    public void setFailedStages(List<String> failedStages) {
        this.failedStages = failedStages == null ? new ArrayList<>() : new ArrayList<>(failedStages);
    }

    public List<ModelProfileProbeCheckResponse> getChecks() {
        return checks;
    }

    public void setChecks(List<ModelProfileProbeCheckResponse> checks) {
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
    }
}
