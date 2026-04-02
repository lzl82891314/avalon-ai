package com.example.avalon.agent.model;

import java.util.ArrayList;
import java.util.List;

public class TomTotStageResult {
    private List<TomTotCandidate> candidates = new ArrayList<>();
    private String selectedCandidateId;
    private String summary;

    public List<TomTotCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<TomTotCandidate> candidates) {
        this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
    }

    public String getSelectedCandidateId() {
        return selectedCandidateId;
    }

    public void setSelectedCandidateId(String selectedCandidateId) {
        this.selectedCandidateId = selectedCandidateId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
