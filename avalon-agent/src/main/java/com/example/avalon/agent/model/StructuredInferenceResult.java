package com.example.avalon.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

public class StructuredInferenceResult {
    private JsonNode payload;
    private String rawJson;
    private RawCompletionMetadata modelMetadata = new RawCompletionMetadata();

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public RawCompletionMetadata getModelMetadata() {
        return modelMetadata;
    }

    public void setModelMetadata(RawCompletionMetadata modelMetadata) {
        this.modelMetadata = modelMetadata == null ? new RawCompletionMetadata() : modelMetadata;
    }
}
