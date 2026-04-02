package com.example.avalon.agent.service;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultTurnAgent implements TurnAgent {
    private final DeliberationPolicyRegistry policyRegistry;
    private final String configuredDefaultPolicyId;

    public DefaultTurnAgent(DeliberationPolicyRegistry policyRegistry) {
        this(policyRegistry, null);
    }

    @Autowired
    public DefaultTurnAgent(DeliberationPolicyRegistry policyRegistry,
                            @Value("${avalon.agent.default-policy-id:}") String configuredDefaultPolicyId) {
        this.policyRegistry = policyRegistry;
        this.configuredDefaultPolicyId = blankToNull(configuredDefaultPolicyId);
        if (this.configuredDefaultPolicyId != null) {
            policyRegistry.require(this.configuredDefaultPolicyId);
        }
    }

    @Override
    public TurnAgentResult execute(PlayerTurnContext context, PlayerAgentConfig config) {
        PlayerAgentConfig effectiveConfig = config == null ? new PlayerAgentConfig() : config;
        String policyId = resolvePolicyId(effectiveConfig);
        effectiveConfig.setAgentPolicyId(policyId);
        return policyRegistry.require(policyId).execute(context, effectiveConfig);
    }

    private String resolvePolicyId(PlayerAgentConfig config) {
        String explicitPolicyId = config == null ? null : blankToNull(config.getAgentPolicyId());
        if (explicitPolicyId != null) {
            return explicitPolicyId;
        }
        if (configuredDefaultPolicyId != null) {
            return configuredDefaultPolicyId;
        }
        return AgentPolicyIds.LEGACY_SINGLE_SHOT;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
