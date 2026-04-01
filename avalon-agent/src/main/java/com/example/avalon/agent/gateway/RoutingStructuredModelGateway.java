package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Primary
@Component
public class RoutingStructuredModelGateway implements StructuredModelGateway {
    private static final List<String> OPENAI_COMPATIBLE_PROVIDER_IDS = List.of(
            "openai",
            "minimax",
            "glm",
            "claude",
            "qwen"
    );
    private static final Set<String> OPENAI_COMPATIBLE_PROVIDERS = Set.copyOf(OPENAI_COMPATIBLE_PROVIDER_IDS);

    private final StructuredModelGateway noopGateway;
    private final StructuredModelGateway openAiGateway;

    @Autowired
    public RoutingStructuredModelGateway(NoopAgentGateway noopGateway, OpenAiChatCompletionsGateway openAiGateway) {
        this.noopGateway = noopGateway;
        this.openAiGateway = openAiGateway;
    }

    RoutingStructuredModelGateway(StructuredModelGateway noopGateway, StructuredModelGateway openAiGateway) {
        this.noopGateway = noopGateway;
        this.openAiGateway = openAiGateway;
    }

    @Override
    public StructuredInferenceResult infer(StructuredInferenceRequest request) {
        String provider = normalizedProvider(request.getProvider());
        if (provider == null || "noop".equals(provider)) {
            return noopGateway.infer(request);
        }
        if (OPENAI_COMPATIBLE_PROVIDERS.contains(provider)) {
            return openAiGateway.infer(request);
        }
        throw new IllegalStateException(
                "Unsupported LLM provider '" + request.getProvider() + "'. Supported providers: "
                        + String.join(", ", OPENAI_COMPATIBLE_PROVIDER_IDS)
                        + ", noop"
        );
    }

    private String normalizedProvider(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
