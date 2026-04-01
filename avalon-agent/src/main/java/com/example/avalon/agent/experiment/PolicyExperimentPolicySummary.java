package com.example.avalon.agent.experiment;

public record PolicyExperimentPolicySummary(
        String policyId,
        int games,
        double winRate,
        double pauseRate,
        double illegalOutputRate,
        double avgModelCalls,
        double avgInputTokens,
        double avgOutputTokens,
        double avgFirstRoundRiskScore,
        double avgPostFailureRecoveryScore,
        double avgAssassinHitScore
) {
}
