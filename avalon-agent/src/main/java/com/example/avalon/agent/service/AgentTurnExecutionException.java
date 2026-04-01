package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentTurnExecutionException extends RuntimeException {
    private final AgentTurnRequest request;
    private final AgentTurnResult lastTurnResult;
    private final int attempts;
    private final List<Map<String, Object>> executionTrace;
    private final Map<String, Object> policySummary;

    public AgentTurnExecutionException(String message,
                                       AgentTurnRequest request,
                                       AgentTurnResult lastTurnResult,
                                       int attempts,
                                       Throwable cause) {
        this(message, request, lastTurnResult, attempts, List.of(), Map.of(), cause);
    }

    public AgentTurnExecutionException(String message,
                                       AgentTurnRequest request,
                                       AgentTurnResult lastTurnResult,
                                       int attempts,
                                       List<Map<String, Object>> executionTrace,
                                       Map<String, Object> policySummary,
                                       Throwable cause) {
        super(message, cause);
        this.request = request;
        this.lastTurnResult = lastTurnResult;
        this.attempts = attempts;
        this.executionTrace = executionTrace == null
                ? List.of()
                : executionTrace.stream().map(AgentTurnExecutionException::copy).toList();
        this.policySummary = copy(policySummary);
    }

    public AgentTurnRequest request() {
        return request;
    }

    public AgentTurnResult lastTurnResult() {
        return lastTurnResult;
    }

    public int attempts() {
        return attempts;
    }

    public List<Map<String, Object>> executionTrace() {
        return executionTrace;
    }

    public Map<String, Object> policySummary() {
        return policySummary;
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
