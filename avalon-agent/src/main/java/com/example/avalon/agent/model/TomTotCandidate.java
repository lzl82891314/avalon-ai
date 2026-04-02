package com.example.avalon.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TomTotCandidate {
    private String candidateId;
    private Map<String, Object> actionDraft = new LinkedHashMap<>();
    private String actionPlanSummary;
    private String projectedPublicReaction;
    private String projectedVoteOutcome;
    private String projectedMissionRisk;
    private Double expectedUtility;
    private List<String> keyRisks = new ArrayList<>();

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public Map<String, Object> getActionDraft() {
        return actionDraft;
    }

    public void setActionDraft(Map<String, Object> actionDraft) {
        this.actionDraft = actionDraft == null ? new LinkedHashMap<>() : new LinkedHashMap<>(actionDraft);
    }

    public String getActionPlanSummary() {
        return actionPlanSummary;
    }

    public void setActionPlanSummary(String actionPlanSummary) {
        this.actionPlanSummary = actionPlanSummary;
    }

    public String getProjectedPublicReaction() {
        return projectedPublicReaction;
    }

    public void setProjectedPublicReaction(String projectedPublicReaction) {
        this.projectedPublicReaction = projectedPublicReaction;
    }

    public String getProjectedVoteOutcome() {
        return projectedVoteOutcome;
    }

    public void setProjectedVoteOutcome(String projectedVoteOutcome) {
        this.projectedVoteOutcome = projectedVoteOutcome;
    }

    public String getProjectedMissionRisk() {
        return projectedMissionRisk;
    }

    public void setProjectedMissionRisk(String projectedMissionRisk) {
        this.projectedMissionRisk = projectedMissionRisk;
    }

    public Double getExpectedUtility() {
        return expectedUtility;
    }

    public void setExpectedUtility(Double expectedUtility) {
        this.expectedUtility = expectedUtility;
    }

    public List<String> getKeyRisks() {
        return keyRisks;
    }

    public void setKeyRisks(List<String> keyRisks) {
        this.keyRisks = keyRisks == null ? new ArrayList<>() : new ArrayList<>(keyRisks);
    }
}
