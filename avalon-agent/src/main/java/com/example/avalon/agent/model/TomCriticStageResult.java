package com.example.avalon.agent.model;

import java.util.ArrayList;
import java.util.List;

public class TomCriticStageResult {
    private String status;
    private List<String> riskFindings = new ArrayList<>();
    private List<String> counterSignals = new ArrayList<>();
    private List<String> recommendedAdjustments = new ArrayList<>();
    private String summary;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getRiskFindings() {
        return riskFindings;
    }

    public void setRiskFindings(List<String> riskFindings) {
        this.riskFindings = riskFindings == null ? new ArrayList<>() : new ArrayList<>(riskFindings);
    }

    public List<String> getCounterSignals() {
        return counterSignals;
    }

    public void setCounterSignals(List<String> counterSignals) {
        this.counterSignals = counterSignals == null ? new ArrayList<>() : new ArrayList<>(counterSignals);
    }

    public List<String> getRecommendedAdjustments() {
        return recommendedAdjustments;
    }

    public void setRecommendedAdjustments(List<String> recommendedAdjustments) {
        this.recommendedAdjustments = recommendedAdjustments == null ? new ArrayList<>() : new ArrayList<>(recommendedAdjustments);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
