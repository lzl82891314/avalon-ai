package com.example.avalon.agent.service;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.TurnAgentResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultTurnAgentTest {
    @Test
    void shouldPreferExplicitPolicyOverConfiguredDefault() {
        AtomicReference<String> executedPolicyId = new AtomicReference<>();
        AtomicReference<String> requestPolicyId = new AtomicReference<>();
        DeliberationPolicyRegistry registry = registry(executedPolicyId, requestPolicyId);
        DefaultTurnAgent turnAgent = new DefaultTurnAgent(registry, AgentPolicyIds.TOM_V1);

        PlayerAgentConfig config = new PlayerAgentConfig();
        config.setAgentPolicyId(AgentPolicyIds.TOM_TOT_V1);

        TurnAgentResult result = turnAgent.execute(null, config);

        assertEquals(AgentPolicyIds.TOM_TOT_V1, executedPolicyId.get());
        assertEquals(AgentPolicyIds.TOM_TOT_V1, requestPolicyId.get());
        assertEquals(AgentPolicyIds.TOM_TOT_V1, result.policyId());
    }

    @Test
    void shouldUseConfiguredDefaultPolicyWhenRequestDoesNotSpecifyOne() {
        AtomicReference<String> executedPolicyId = new AtomicReference<>();
        AtomicReference<String> requestPolicyId = new AtomicReference<>();
        DeliberationPolicyRegistry registry = registry(executedPolicyId, requestPolicyId);
        DefaultTurnAgent turnAgent = new DefaultTurnAgent(registry, AgentPolicyIds.TOM_V1);

        PlayerAgentConfig config = new PlayerAgentConfig();

        TurnAgentResult result = turnAgent.execute(null, config);

        assertEquals(AgentPolicyIds.TOM_V1, executedPolicyId.get());
        assertEquals(AgentPolicyIds.TOM_V1, requestPolicyId.get());
        assertEquals(AgentPolicyIds.TOM_V1, result.policyId());
        assertEquals(AgentPolicyIds.TOM_V1, config.getAgentPolicyId());
    }

    @Test
    void shouldFallbackToLegacySingleShotWhenNeitherRequestNorConfigProvidesPolicy() {
        AtomicReference<String> executedPolicyId = new AtomicReference<>();
        AtomicReference<String> requestPolicyId = new AtomicReference<>();
        DeliberationPolicyRegistry registry = registry(executedPolicyId, requestPolicyId);
        DefaultTurnAgent turnAgent = new DefaultTurnAgent(registry, null);

        PlayerAgentConfig config = new PlayerAgentConfig();

        TurnAgentResult result = turnAgent.execute(null, config);

        assertEquals(AgentPolicyIds.LEGACY_SINGLE_SHOT, executedPolicyId.get());
        assertEquals(AgentPolicyIds.LEGACY_SINGLE_SHOT, requestPolicyId.get());
        assertEquals(AgentPolicyIds.LEGACY_SINGLE_SHOT, result.policyId());
    }

    @Test
    void shouldRejectUnknownConfiguredDefaultPolicyAtConstructionTime() {
        DeliberationPolicyRegistry registry = registry(new AtomicReference<>(), new AtomicReference<>());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultTurnAgent(registry, "unknown-policy")
        );

        assertEquals("Unknown agent policy: unknown-policy", error.getMessage());
    }

    private DeliberationPolicyRegistry registry(AtomicReference<String> executedPolicyId,
                                                AtomicReference<String> requestPolicyId) {
        return new DeliberationPolicyRegistry(List.of(
                policy(AgentPolicyIds.LEGACY_SINGLE_SHOT, executedPolicyId, requestPolicyId),
                policy(AgentPolicyIds.TOM_V1, executedPolicyId, requestPolicyId),
                policy(AgentPolicyIds.TOM_TOT_V1, executedPolicyId, requestPolicyId),
                policy(AgentPolicyIds.TOM_TOT_CRITIC_V1, executedPolicyId, requestPolicyId)
        ));
    }

    private DeliberationPolicy policy(String policyId,
                                      AtomicReference<String> executedPolicyId,
                                      AtomicReference<String> requestPolicyId) {
        return new DeliberationPolicy() {
            @Override
            public String policyId() {
                return policyId;
            }

            @Override
            public TurnAgentResult execute(com.example.avalon.core.game.model.PlayerTurnContext context,
                                           PlayerAgentConfig config) {
                executedPolicyId.set(policyId);
                requestPolicyId.set(config.getAgentPolicyId());
                return new TurnAgentResult(
                        null,
                        null,
                        null,
                        1,
                        policyId,
                        null,
                        List.of(),
                        Map.of()
                );
            }
        };
    }
}
