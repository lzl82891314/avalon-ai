package com.example.avalon.agent.model;

import com.example.avalon.core.player.memory.PlayerBeliefState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TomBeliefStageResult {
    private Map<String, PlayerBeliefState> beliefsByPlayerId = new LinkedHashMap<>();
    private String strategyMode;
    private String lastSummary;
    private List<String> observationsToAdd = new ArrayList<>();
    private List<String> inferredFactsToAdd = new ArrayList<>();

    public Map<String, PlayerBeliefState> getBeliefsByPlayerId() {
        return beliefsByPlayerId;
    }

    public void setBeliefsByPlayerId(Map<String, PlayerBeliefState> beliefsByPlayerId) {
        this.beliefsByPlayerId = beliefsByPlayerId == null ? new LinkedHashMap<>() : new LinkedHashMap<>(beliefsByPlayerId);
    }

    public String getStrategyMode() {
        return strategyMode;
    }

    public void setStrategyMode(String strategyMode) {
        this.strategyMode = strategyMode;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }

    public List<String> getObservationsToAdd() {
        return observationsToAdd;
    }

    public void setObservationsToAdd(List<String> observationsToAdd) {
        this.observationsToAdd = observationsToAdd == null ? new ArrayList<>() : new ArrayList<>(observationsToAdd);
    }

    public List<String> getInferredFactsToAdd() {
        return inferredFactsToAdd;
    }

    public void setInferredFactsToAdd(List<String> inferredFactsToAdd) {
        this.inferredFactsToAdd = inferredFactsToAdd == null ? new ArrayList<>() : new ArrayList<>(inferredFactsToAdd);
    }
}
