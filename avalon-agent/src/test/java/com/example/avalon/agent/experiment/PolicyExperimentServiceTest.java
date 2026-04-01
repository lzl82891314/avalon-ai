package com.example.avalon.agent.experiment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyExperimentServiceTest {
    @Test
    void shouldSummarizePerPolicyMetrics() {
        PolicyExperimentService service = new PolicyExperimentService();
        PolicyExperimentRun run = new PolicyExperimentRun(
                "run-1",
                "legacy-single-shot",
                "tom-tot-critic-v1",
                List.of(11L, 12L),
                Instant.parse("2026-04-01T00:00:00Z")
        );

        PolicyExperimentSummary summary = service.summarize(run, List.of(
                new PolicyExperimentGameResult("g1", "legacy-single-shot", "MERLIN", true, false, true, 1, 120, 40, 0.6, 0.4, 0.0),
                new PolicyExperimentGameResult("g2", "legacy-single-shot", "ASSASSIN", false, false, true, 1, 110, 35, 0.7, 0.3, 1.0),
                new PolicyExperimentGameResult("g3", "tom-tot-critic-v1", "MERLIN", true, false, true, 3, 260, 90, 0.2, 0.8, 0.0),
                new PolicyExperimentGameResult("g4", "tom-tot-critic-v1", "ASSASSIN", true, false, true, 4, 320, 120, 0.3, 0.9, 1.0)
        ));

        assertEquals("run-1", summary.runId());
        assertEquals(2, summary.policySummaries().size());

        PolicyExperimentPolicySummary legacy = summary.policySummaries().get(0);
        PolicyExperimentPolicySummary candidate = summary.policySummaries().get(1);

        assertEquals("legacy-single-shot", legacy.policyId());
        assertEquals(2, legacy.games());
        assertEquals(0.5, legacy.winRate());
        assertEquals(1.0, legacy.avgModelCalls());

        assertEquals("tom-tot-critic-v1", candidate.policyId());
        assertEquals(2, candidate.games());
        assertEquals(1.0, candidate.winRate());
        assertEquals(3.5, candidate.avgModelCalls());
        assertEquals(0.25, candidate.avgFirstRoundRiskScore());
    }
}
