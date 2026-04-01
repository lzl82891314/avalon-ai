package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;

public interface StructuredModelGateway {
    StructuredInferenceResult infer(StructuredInferenceRequest request);
}
