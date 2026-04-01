package com.example.avalon.agent.service;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultTurnAgent implements TurnAgent {
    private final DeliberationPolicyRegistry policyRegistry;

    public DefaultTurnAgent(DeliberationPolicyRegistry policyRegistry) {
        this.policyRegistry = policyRegistry;
    }

    @Override
    public TurnAgentResult execute(PlayerTurnContext context, PlayerAgentConfig config) {
        PlayerAgentConfig effectiveConfig = config == null ? new PlayerAgentConfig() : config;
        String policyId = effectiveConfig.effectiveAgentPolicyId();
        return policyRegistry.require(policyId).execute(context, effectiveConfig);
    }
}
