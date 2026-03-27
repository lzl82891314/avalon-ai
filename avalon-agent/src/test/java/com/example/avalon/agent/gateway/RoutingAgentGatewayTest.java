package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingAgentGatewayTest {
    @Test
    void shouldRouteSupportedCompatibleProvidersToOpenAiGateway() {
        RecordingGateway noopGateway = new RecordingGateway("noop");
        RecordingGateway openAiGateway = new RecordingGateway("openai");
        RoutingAgentGateway routingAgentGateway = new RoutingAgentGateway(noopGateway, openAiGateway);

        for (String provider : List.of("openai", "OpenAI", "minimax", "glm", "claude", "qwen")) {
            AgentTurnRequest request = new AgentTurnRequest();
            request.setProvider(provider);

            AgentTurnResult result = routingAgentGateway.playTurn(request);

            assertEquals("openai", result.getPublicSpeech());
        }
        assertEquals(0, noopGateway.calls.get());
        assertEquals(6, openAiGateway.calls.get());
    }

    @Test
    void shouldFallbackToNoopForBlankOrExplicitNoopProvider() {
        RecordingGateway noopGateway = new RecordingGateway("noop");
        RecordingGateway openAiGateway = new RecordingGateway("openai");
        RoutingAgentGateway routingAgentGateway = new RoutingAgentGateway(noopGateway, openAiGateway);
        AgentTurnRequest blankProvider = new AgentTurnRequest();
        AgentTurnRequest explicitNoop = new AgentTurnRequest();
        explicitNoop.setProvider("noop");
        AgentTurnRequest upperCaseNoop = new AgentTurnRequest();
        upperCaseNoop.setProvider(" NOOP ");

        assertEquals("noop", routingAgentGateway.playTurn(blankProvider).getPublicSpeech());
        assertEquals("noop", routingAgentGateway.playTurn(explicitNoop).getPublicSpeech());
        assertEquals("noop", routingAgentGateway.playTurn(upperCaseNoop).getPublicSpeech());
        assertEquals(3, noopGateway.calls.get());
        assertEquals(0, openAiGateway.calls.get());
    }

    @Test
    void shouldRejectUnsupportedProviderInsteadOfFallingBackToNoop() {
        RecordingGateway noopGateway = new RecordingGateway("noop");
        RecordingGateway openAiGateway = new RecordingGateway("openai");
        RoutingAgentGateway routingAgentGateway = new RoutingAgentGateway(noopGateway, openAiGateway);
        AgentTurnRequest unsupportedProvider = new AgentTurnRequest();
        unsupportedProvider.setProvider("anthropic");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> routingAgentGateway.playTurn(unsupportedProvider)
        );

        assertEquals(
                "Unsupported LLM provider 'anthropic'. Supported providers: openai, minimax, glm, claude, qwen, noop",
                error.getMessage()
        );
        assertEquals(0, noopGateway.calls.get());
        assertEquals(0, openAiGateway.calls.get());
    }

    private static final class RecordingGateway implements AgentGateway {
        private final String marker;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingGateway(String marker) {
            this.marker = marker;
        }

        @Override
        public AgentTurnResult playTurn(AgentTurnRequest request) {
            calls.incrementAndGet();
            AgentTurnResult result = new AgentTurnResult();
            result.setPublicSpeech(marker);
            return result;
        }
    }
}
