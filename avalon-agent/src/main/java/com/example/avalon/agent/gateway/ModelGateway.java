package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;

public interface ModelGateway {
    AgentTurnResult playTurn(AgentTurnRequest request);
}
