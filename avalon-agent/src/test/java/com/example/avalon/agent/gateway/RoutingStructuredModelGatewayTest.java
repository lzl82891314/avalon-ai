package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingStructuredModelGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldRouteSupportedCompatibleProvidersToOpenAiGateway() {
        RecordingStructuredGateway noopGateway = new RecordingStructuredGateway("noop");
        RecordingStructuredGateway openAiGateway = new RecordingStructuredGateway("openai");
        RoutingStructuredModelGateway routingGateway = new RoutingStructuredModelGateway(noopGateway, openAiGateway);

        for (String provider : List.of("openai", "OpenAI", "minimax", "glm", "claude", "qwen")) {
            StructuredInferenceRequest request = new StructuredInferenceRequest();
            request.setProvider(provider);

            StructuredInferenceResult result = routingGateway.infer(request);

            assertEquals("openai", result.getPayload().path("gateway").asText());
        }
        assertEquals(0, noopGateway.calls.get());
        assertEquals(6, openAiGateway.calls.get());
    }

    @Test
    void shouldFallbackToNoopForBlankOrExplicitNoopProvider() {
        RecordingStructuredGateway noopGateway = new RecordingStructuredGateway("noop");
        RecordingStructuredGateway openAiGateway = new RecordingStructuredGateway("openai");
        RoutingStructuredModelGateway routingGateway = new RoutingStructuredModelGateway(noopGateway, openAiGateway);
        StructuredInferenceRequest blankProvider = new StructuredInferenceRequest();
        StructuredInferenceRequest explicitNoop = new StructuredInferenceRequest();
        explicitNoop.setProvider("noop");
        StructuredInferenceRequest upperCaseNoop = new StructuredInferenceRequest();
        upperCaseNoop.setProvider(" NOOP ");

        assertEquals("noop", routingGateway.infer(blankProvider).getPayload().path("gateway").asText());
        assertEquals("noop", routingGateway.infer(explicitNoop).getPayload().path("gateway").asText());
        assertEquals("noop", routingGateway.infer(upperCaseNoop).getPayload().path("gateway").asText());
        assertEquals(3, noopGateway.calls.get());
        assertEquals(0, openAiGateway.calls.get());
    }

    @Test
    void shouldRejectUnsupportedProviderInsteadOfFallingBackToNoop() {
        RecordingStructuredGateway noopGateway = new RecordingStructuredGateway("noop");
        RecordingStructuredGateway openAiGateway = new RecordingStructuredGateway("openai");
        RoutingStructuredModelGateway routingGateway = new RoutingStructuredModelGateway(noopGateway, openAiGateway);
        StructuredInferenceRequest unsupportedProvider = new StructuredInferenceRequest();
        unsupportedProvider.setProvider("anthropic");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> routingGateway.infer(unsupportedProvider)
        );

        assertEquals(
                "Unsupported LLM provider 'anthropic'. Supported providers: openai, minimax, glm, claude, qwen, noop",
                error.getMessage()
        );
        assertEquals(0, noopGateway.calls.get());
        assertEquals(0, openAiGateway.calls.get());
    }

    private final class RecordingStructuredGateway implements StructuredModelGateway {
        private final String marker;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingStructuredGateway(String marker) {
            this.marker = marker;
        }

        @Override
        public StructuredInferenceResult infer(StructuredInferenceRequest request) {
            calls.incrementAndGet();
            StructuredInferenceResult result = new StructuredInferenceResult();
            result.setPayload(objectMapper.valueToTree(Map.of("gateway", marker)));
            result.setRawJson("{\"gateway\":\"" + marker + "\"}");
            return result;
        }
    }
}
