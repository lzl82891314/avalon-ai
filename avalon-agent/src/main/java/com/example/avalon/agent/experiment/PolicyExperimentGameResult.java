package com.example.avalon.agent.experiment;

public record PolicyExperimentGameResult(
        String gameId,
        String policyId,
        String roleId,
        boolean win,
        boolean paused,
        boolean legalOutput,
        int modelCalls,
        long inputTokens,
        long outputTokens,
        double firstRoundRiskScore,
        double postFailureRecoveryScore,
        double assassinHitScore
) {
}
