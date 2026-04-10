package com.example.avalon.agent.gateway;

import java.time.Duration;

public record AvalonRuntimeStageBudget(
        String stageId,
        Integer maxTokens,
        Duration timeout,
        Integer maxAttempts
) {
}
