package com.example.avalon.agent.experiment;

import java.time.Instant;
import java.util.List;

public record PolicyExperimentRun(
        String runId,
        String baselinePolicyId,
        String candidatePolicyId,
        List<Long> seedSuite,
        Instant createdAt
) {
    public PolicyExperimentRun {
        seedSuite = seedSuite == null ? List.of() : List.copyOf(seedSuite);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
