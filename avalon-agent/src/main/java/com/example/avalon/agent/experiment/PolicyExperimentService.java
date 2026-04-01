package com.example.avalon.agent.experiment;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PolicyExperimentService {
    public PolicyExperimentSummary summarize(PolicyExperimentRun run, List<PolicyExperimentGameResult> gameResults) {
        Map<String, List<PolicyExperimentGameResult>> grouped = new LinkedHashMap<>();
        if (gameResults != null) {
            for (PolicyExperimentGameResult gameResult : gameResults) {
                grouped.computeIfAbsent(gameResult.policyId(), ignored -> new ArrayList<>()).add(gameResult);
            }
        }
        List<PolicyExperimentPolicySummary> summaries = grouped.entrySet().stream()
                .map(entry -> summary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PolicyExperimentPolicySummary::policyId))
                .toList();
        return new PolicyExperimentSummary(
                run == null ? null : run.runId(),
                run == null ? null : run.baselinePolicyId(),
                run == null ? null : run.candidatePolicyId(),
                summaries
        );
    }

    private PolicyExperimentPolicySummary summary(String policyId, List<PolicyExperimentGameResult> gameResults) {
        int games = gameResults.size();
        if (games == 0) {
            return new PolicyExperimentPolicySummary(policyId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        double winRate = ratio(gameResults.stream().filter(PolicyExperimentGameResult::win).count(), games);
        double pauseRate = ratio(gameResults.stream().filter(PolicyExperimentGameResult::paused).count(), games);
        double illegalRate = ratio(gameResults.stream().filter(result -> !result.legalOutput()).count(), games);
        double avgModelCalls = average(gameResults.stream().mapToInt(PolicyExperimentGameResult::modelCalls).sum(), games);
        double avgInputTokens = average(gameResults.stream().mapToLong(PolicyExperimentGameResult::inputTokens).sum(), games);
        double avgOutputTokens = average(gameResults.stream().mapToLong(PolicyExperimentGameResult::outputTokens).sum(), games);
        double avgFirstRoundRisk = gameResults.stream().mapToDouble(PolicyExperimentGameResult::firstRoundRiskScore).average().orElse(0);
        double avgRecovery = gameResults.stream().mapToDouble(PolicyExperimentGameResult::postFailureRecoveryScore).average().orElse(0);
        double avgAssassinHit = gameResults.stream().mapToDouble(PolicyExperimentGameResult::assassinHitScore).average().orElse(0);
        return new PolicyExperimentPolicySummary(
                policyId,
                games,
                winRate,
                pauseRate,
                illegalRate,
                avgModelCalls,
                avgInputTokens,
                avgOutputTokens,
                avgFirstRoundRisk,
                avgRecovery,
                avgAssassinHit
        );
    }

    private double ratio(long count, int total) {
        return total == 0 ? 0 : (double) count / total;
    }

    private double average(long sum, int total) {
        return total == 0 ? 0 : (double) sum / total;
    }
}
