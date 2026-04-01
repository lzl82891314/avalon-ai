package com.example.avalon.agent.model;

import com.example.avalon.core.game.model.PlayerAction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TurnAgentResult(
        AgentTurnRequest request,
        AgentTurnResult turnResult,
        PlayerAction action,
        int attempts,
        String policyId,
        String strategyProfileId,
        List<Map<String, Object>> executionTrace,
        Map<String, Object> policySummary
) {
    public TurnAgentResult {
        executionTrace = executionTrace == null
                ? List.of()
                : executionTrace.stream().map(TurnAgentResult::copy).toList();
        policySummary = copy(policySummary);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
