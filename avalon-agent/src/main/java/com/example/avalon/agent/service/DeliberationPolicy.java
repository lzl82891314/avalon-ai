package com.example.avalon.agent.service;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.model.TurnAgentResult;
import com.example.avalon.core.game.model.PlayerTurnContext;

public interface DeliberationPolicy {
    String policyId();

    TurnAgentResult execute(PlayerTurnContext context, PlayerAgentConfig config);
}
