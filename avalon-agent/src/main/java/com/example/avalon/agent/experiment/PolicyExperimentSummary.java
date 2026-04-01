package com.example.avalon.agent.experiment;

import java.util.List;

public record PolicyExperimentSummary(
        String runId,
        String baselinePolicyId,
        String candidatePolicyId,
        List<PolicyExperimentPolicySummary> policySummaries
) {
    public PolicyExperimentSummary {
        policySummaries = policySummaries == null ? List.of() : List.copyOf(policySummaries);
    }
}
