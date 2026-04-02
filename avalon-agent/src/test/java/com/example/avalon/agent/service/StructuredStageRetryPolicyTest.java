package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.gateway.StructuredModelGateway;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredStageRetryPolicyTest {
    @Test
    void shouldNotRetryTransportFailures() {
        StructuredStageRetryPolicy policy = new StructuredStageRetryPolicy();
        RecordingGateway gateway = new RecordingGateway(List.of(transportFailure()));

        StructuredStageRetryPolicy.StructuredStageExecutionException error = assertThrows(
                StructuredStageRetryPolicy.StructuredStageExecutionException.class,
                () -> policy.execute("belief-stage", request(), gateway)
        );

        assertEquals(1, error.attempts());
        assertEquals(1, gateway.requests().size());
    }

    @Test
    void shouldRetryBeliefStageCompressionFailures() {
        StructuredStageRetryPolicy policy = new StructuredStageRetryPolicy();
        RecordingGateway gateway = new RecordingGateway(List.of(truncatedFailure(), successfulResult()));

        StructuredStageRetryPolicy.StructuredStageExecution execution = policy.execute("belief-stage", request(), gateway);

        assertEquals(2, execution.attempts());
        assertEquals(2, gateway.requests().size());
        StructuredInferenceRequest secondAttempt = gateway.requests().get(1);
        assertEquals(1280, secondAttempt.getMaxTokens());
        assertTrue(secondAttempt.getDeveloperPrompt().contains("只保留 beliefsByPlayerId、strategyMode、lastSummary、observationsToAdd、inferredFactsToAdd"));
    }

    @Test
    void shouldRetryTotStageCompressionFailuresWithStageSpecificPrompt() {
        StructuredStageRetryPolicy policy = new StructuredStageRetryPolicy();
        RecordingGateway gateway = new RecordingGateway(List.of(truncatedFailure(), successfulResult()));

        StructuredStageRetryPolicy.StructuredStageExecution execution = policy.execute("tot-stage", request(), gateway);

        assertEquals(2, execution.attempts());
        StructuredInferenceRequest secondAttempt = gateway.requests().get(1);
        assertTrue(secondAttempt.getDeveloperPrompt().contains("必须固定输出 3 个 candidates"));
        assertTrue(secondAttempt.getDeveloperPrompt().contains("keyRisks 最多 1 条"));
    }

    @Test
    void shouldRetryCriticStageCompressionFailuresWithStageSpecificPrompt() {
        StructuredStageRetryPolicy policy = new StructuredStageRetryPolicy();
        RecordingGateway gateway = new RecordingGateway(List.of(truncatedFailure(), successfulResult()));

        StructuredStageRetryPolicy.StructuredStageExecution execution = policy.execute("critic-stage", request(), gateway);

        assertEquals(2, execution.attempts());
        StructuredInferenceRequest secondAttempt = gateway.requests().get(1);
        assertTrue(secondAttempt.getDeveloperPrompt().contains("status 必填"));
        assertTrue(secondAttempt.getDeveloperPrompt().contains("各最多 2 条短句"));
    }

    private StructuredInferenceRequest request() {
        StructuredInferenceRequest request = new StructuredInferenceRequest();
        request.setProvider("openai");
        request.setModelName("gpt-5.4");
        request.setMaxTokens(320);
        request.setDeveloperPrompt("stage developer prompt");
        request.setUserPrompt("stage user prompt");
        return request;
    }

    private RuntimeException transportFailure() {
        return new OpenAiCompatibleResponseException(
                "OpenAI-compatible HTTP transport failed after 3/3 attempts (java.net.http.HttpTimeoutException: request timed out)",
                null,
                "openai",
                "gpt-5.4",
                null,
                null,
                Map.of(
                        "failureDomain", "transport",
                        "failureKind", "timeout",
                        "transportAttempts", 3,
                        "timeoutMs", 120000L,
                        "rootExceptionClass", "java.net.http.HttpTimeoutException",
                        "rootExceptionMessage", "request timed out"
                )
        );
    }

    private RuntimeException truncatedFailure() {
        return new OpenAiCompatibleResponseException(
                "OpenAI-compatible assistant content looked like truncated JSON (shape=truncated_json_candidate, bodyPreview={) [finishReason=length]",
                null,
                "openai",
                "gpt-5.4",
                "length",
                new OpenAiCompatibleMessageAnalysis(
                        true,
                        false,
                        "truncated_json_candidate",
                        "{",
                        null,
                        null
                )
        );
    }

    private StructuredInferenceResult successfulResult() {
        return new StructuredInferenceResult();
    }

    private static final class RecordingGateway implements StructuredModelGateway {
        private final List<Object> scriptedResults;
        private final List<StructuredInferenceRequest> requests = new ArrayList<>();

        private RecordingGateway(List<Object> scriptedResults) {
            this.scriptedResults = new ArrayList<>(scriptedResults);
        }

        @Override
        public StructuredInferenceResult infer(StructuredInferenceRequest request) {
            requests.add(request.copy());
            Object scripted = scriptedResults.get(Math.min(requests.size() - 1, scriptedResults.size() - 1));
            if (scripted instanceof RuntimeException exception) {
                throw exception;
            }
            return (StructuredInferenceResult) scripted;
        }

        private List<StructuredInferenceRequest> requests() {
            return requests;
        }
    }
}
