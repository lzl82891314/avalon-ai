package com.example.avalon.api.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import com.example.avalon.agent.gateway.OpenAiHttpTransport;
import com.example.avalon.api.dto.ModelProfileProbeCheckResponse;
import com.example.avalon.api.dto.ModelProfileProbeRequest;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import com.example.avalon.api.model.CatalogModelProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ModelProfileProbeService {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final List<ProbeCheckType> DEFAULT_CHECKS = List.of(
            ProbeCheckType.CONNECTIVITY,
            ProbeCheckType.STRUCTURED_JSON
    );
    private static final Pattern STATUS_PATTERN = Pattern.compile("status\\s+(\\d{3})");

    private final ModelProfileCatalogService modelProfileCatalogService;
    private final OpenAiHttpTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ModelProfileProbeService(ModelProfileCatalogService modelProfileCatalogService,
                                    OpenAiHttpTransport transport) {
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.transport = transport;
    }

    public ModelProfileProbeResponse probe(String modelId, ModelProfileProbeRequest request) {
        CatalogModelProfile profile = modelProfileCatalogService.requireCatalogProfile(modelId);
        List<ProbeCheckType> checksToRun = normalizeChecks(request == null ? List.of() : request.getChecks());

        List<ModelProfileProbeCheckResponse> checks = new ArrayList<>();
        if (checksToRun.contains(ProbeCheckType.CONNECTIVITY)) {
            checks.add(runConnectivityCheck(profile));
        }
        if (checksToRun.contains(ProbeCheckType.STRUCTURED_JSON)) {
            checks.add(runStructuredJsonCheck(profile));
        }

        ModelProfileProbeResponse response = new ModelProfileProbeResponse();
        response.setModelId(profile.modelId());
        response.setProvider(profile.provider());
        response.setModelName(profile.modelName());
        response.setBaseUrl(defaultBaseUrl(OpenAiCompatibleSupport.stringOption(profile.providerOptions(), "baseUrl")));
        response.setChecks(checks);
        response.setReachable(checkResult(checks, ProbeCheckType.CONNECTIVITY));
        response.setStructuredCompatible(checkResult(checks, ProbeCheckType.STRUCTURED_JSON));
        response.setDiagnosis(diagnosis(response.getReachable(), response.getStructuredCompatible()));
        return response;
    }

    private List<ProbeCheckType> normalizeChecks(List<String> requestedChecks) {
        if (requestedChecks == null || requestedChecks.isEmpty()) {
            return DEFAULT_CHECKS;
        }
        Set<ProbeCheckType> checks = new LinkedHashSet<>();
        for (String requestedCheck : requestedChecks) {
            if (requestedCheck == null || requestedCheck.isBlank()) {
                continue;
            }
            checks.add(ProbeCheckType.valueOf(requestedCheck.trim().toUpperCase(Locale.ROOT)));
        }
        return checks.isEmpty() ? DEFAULT_CHECKS : List.copyOf(checks);
    }

    private ModelProfileProbeCheckResponse runConnectivityCheck(CatalogModelProfile profile) {
        long startedAt = System.nanoTime();
        ModelProfileProbeCheckResponse response = new ModelProfileProbeCheckResponse();
        response.setCheckType(ProbeCheckType.CONNECTIVITY.name());
        try {
            RequestSettings settings = requestSettings(profile);
            JsonNode payload = transport.postChatCompletion(
                    OpenAiCompatibleSupport.endpointUri(settings.baseUrl()),
                    headers(settings),
                    connectivityBody(profile),
                    settings.timeout()
            );
            response.setSuccess(true);
            response.setHttpStatus(200);
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setFinishReason(textOrNull(payload.path("choices").path(0).path("finish_reason")));
            response.setAssistantPreview(previewAssistant(payload));
            return response;
        } catch (RuntimeException exception) {
            response.setSuccess(false);
            if (response.getHttpStatus() == null) {
                response.setHttpStatus(extractStatus(exception.getMessage()));
            }
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setErrorMessage(exception.getMessage());
            return response;
        }
    }

    private ModelProfileProbeCheckResponse runStructuredJsonCheck(CatalogModelProfile profile) {
        long startedAt = System.nanoTime();
        ModelProfileProbeCheckResponse response = new ModelProfileProbeCheckResponse();
        response.setCheckType(ProbeCheckType.STRUCTURED_JSON.name());
        try {
            RequestSettings settings = requestSettings(profile);
            JsonNode payload = transport.postChatCompletion(
                    OpenAiCompatibleSupport.endpointUri(settings.baseUrl()),
                    headers(settings),
                    structuredJsonBody(profile),
                    settings.timeout()
            );
            response.setHttpStatus(200);
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setFinishReason(textOrNull(payload.path("choices").path(0).path("finish_reason")));

            JsonNode message = payload.path("choices").path(0).path("message");
            OpenAiCompatibleMessageAnalysis analysis = OpenAiCompatibleSupport.analyzeAssistantMessage(message);
            applyAnalysis(response, analysis);

            JsonNode structuredPayload = OpenAiCompatibleSupport.readJson(objectMapper, analysis);
            JsonNode actionNode = structuredPayload.get("action");
            if (actionNode == null || actionNode.isNull() || actionNode.isMissingNode()) {
                throw new IllegalStateException("OpenAI-compatible response did not include an action object");
            }
            response.setSuccess(true);
            return response;
        } catch (RuntimeException exception) {
            response.setSuccess(false);
            if (response.getHttpStatus() == null) {
                response.setHttpStatus(extractStatus(exception.getMessage()));
            }
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setErrorMessage(exception.getMessage());
            return response;
        }
    }

    private Map<String, String> headers(RequestSettings settings) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + settings.apiKey());
        if (settings.organization() != null && !settings.organization().isBlank()) {
            headers.put("OpenAI-Organization", settings.organization());
        }
        if (settings.project() != null && !settings.project().isBlank()) {
            headers.put("OpenAI-Project", settings.project());
        }
        return headers;
    }

    private String connectivityBody(CatalogModelProfile profile) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", defaultModel(profile.modelName()));
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", "Reply briefly.");
        messages.addObject()
                .put("role", "user")
                .put("content", "hi");
        root.put("max_completion_tokens", 32);
        if (profile.temperature() != null) {
            root.put("temperature", profile.temperature());
        }
        return writeJson(root);
    }

    private String structuredJsonBody(CatalogModelProfile profile) {
        Map<String, Object> providerOptions = OpenAiCompatibleSupport.effectiveProviderOptions(
                profile.provider(),
                profile.providerOptions()
        );
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", defaultModel(profile.modelName()));
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", OpenAiCompatibleSupport.instructionRole(profile.provider(), providerOptions))
                .put("content", structuredInstruction(profile.provider()));
        messages.addObject()
                .put("role", "user")
                .put("content", structuredUserPrompt(profile.provider()));
        if (profile.temperature() != null) {
            root.put("temperature", profile.temperature());
        }
        if (profile.maxTokens() != null) {
            root.put("max_completion_tokens", profile.maxTokens());
        }
        for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
            if (!OpenAiCompatibleSupport.shouldForwardProviderOption(entry.getKey())) {
                continue;
            }
            root.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
        }
        return writeJson(root);
    }

    private String structuredInstruction(String provider) {
        StringBuilder builder = new StringBuilder("""
                你正在控制一名阿瓦隆玩家。
                只返回一个 JSON 对象，不要输出 Markdown，不要补充解释文字。
                JSON 键固定为 publicSpeech、privateThought、action、auditReason、memoryUpdate。
                publicSpeech 和 privateThought 都尽量使用中文。
                action 必须是合法的结构化动作 JSON。
                """.strip());
        if ("minimax".equals(OpenAiCompatibleSupport.providerId(provider))) {
            builder.append(System.lineSeparator())
                    .append("""
                            兼容要求：
                            - 最终输出的第一个字符必须是 {，最后一个字符必须是 }
                            - 不要输出 <think>、不要输出代码块、不要输出项目符号、不要解释规则
                            - privateThought 只写一句简短中文
                            """.strip());
        }
        return builder.toString();
    }

    private String structuredUserPrompt(String provider) {
        StringBuilder builder = new StringBuilder("""
                当前阶段只允许 PUBLIC_SPEECH。
                请返回一个合法动作，其中 action.actionType 必须为 PUBLIC_SPEECH，
                action.speechText 可以是一句简短中文发言。
                """.strip());
        if ("minimax".equals(OpenAiCompatibleSupport.providerId(provider))) {
            builder.append(System.lineSeparator())
                    .append("""
                            你的最终回复只能是一个 JSON 对象。
                            合法示例：
                            {"publicSpeech":"我先说一句公开信息。","privateThought":"先做一轮观察。","action":{"actionType":"PUBLIC_SPEECH","speechText":"我先说一句公开信息。"},"auditReason":{"goal":"生成合法公开发言","reasonSummary":["当前阶段只允许公开发言"],"confidence":0.72},"memoryUpdate":null}
                            """.strip());
        }
        return builder.toString();
    }

    private RequestSettings requestSettings(CatalogModelProfile profile) {
        Map<String, Object> providerOptions = profile.providerOptions();
        String apiKey = OpenAiCompatibleSupport.stringOption(providerOptions, "apiKey");
        String apiKeyEnv = OpenAiCompatibleSupport.stringOption(providerOptions, "apiKeyEnv");
        if ((apiKey == null || apiKey.isBlank()) && apiKeyEnv != null && !apiKeyEnv.isBlank()) {
            apiKey = System.getenv(apiKeyEnv);
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI-compatible provider '" + OpenAiCompatibleSupport.providerId(profile.provider())
                            + "' requires apiKey, apiKeyEnv, or OPENAI_API_KEY"
            );
        }
        String baseUrl = defaultBaseUrl(OpenAiCompatibleSupport.stringOption(providerOptions, "baseUrl"));
        String organization = OpenAiCompatibleSupport.stringOption(providerOptions, "organization");
        String project = OpenAiCompatibleSupport.stringOption(providerOptions, "project");
        Duration timeout = timeout(providerOptions.get("timeoutMillis"));
        return new RequestSettings(baseUrl, apiKey, organization, project, timeout);
    }

    private String defaultModel(String modelName) {
        return modelName == null || modelName.isBlank() ? "gpt-5.2" : modelName;
    }

    private String defaultBaseUrl(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank() ? OpenAiCompatibleSupport.DEFAULT_BASE_URL : baseUrl;
    }

    private String previewAssistant(JsonNode response) {
        try {
            OpenAiCompatibleMessageAnalysis analysis = OpenAiCompatibleSupport.analyzeAssistantMessage(
                    response.path("choices").path(0).path("message")
            );
            return analysis.assistantContentPreview();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void applyAnalysis(ModelProfileProbeCheckResponse response, OpenAiCompatibleMessageAnalysis analysis) {
        response.setAssistantPreview(analysis.assistantContentPreview());
        response.setContentPresent(analysis.contentPresent());
        response.setReasoningDetailsPresent(analysis.reasoningDetailsPresent());
        response.setContentShape(analysis.assistantContentShape());
        response.setReasoningDetailsPreview(analysis.reasoningDetailsPreview());
    }

    private Integer extractStatus(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        Matcher matcher = STATUS_PATTERN.matcher(errorMessage);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String diagnosis(Boolean reachable, Boolean structuredCompatible) {
        if (Boolean.FALSE.equals(reachable)) {
            return "NETWORK_OR_AUTH_FAILED";
        }
        if (Boolean.TRUE.equals(reachable) && Boolean.FALSE.equals(structuredCompatible)) {
            return "NETWORK_OK_BUT_STRUCTURED_JSON_FAILED";
        }
        if (Boolean.FALSE.equals(structuredCompatible)) {
            return "STRUCTURED_JSON_FAILED";
        }
        return "OK";
    }

    private Boolean checkResult(List<ModelProfileProbeCheckResponse> checks, ProbeCheckType type) {
        return checks.stream()
                .filter(check -> type.name().equals(check.getCheckType()))
                .findFirst()
                .map(ModelProfileProbeCheckResponse::isSuccess)
                .orElse(null);
    }

    private Duration timeout(Object rawTimeoutMillis) {
        if (rawTimeoutMillis instanceof Number number) {
            return Duration.ofMillis(number.longValue());
        }
        if (rawTimeoutMillis instanceof String text && !text.isBlank()) {
            return Duration.ofMillis(Long.parseLong(text));
        }
        return DEFAULT_TIMEOUT;
    }

    private Long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String writeJson(ObjectNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize model probe request", exception);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private enum ProbeCheckType {
        CONNECTIVITY,
        STRUCTURED_JSON
    }

    private record RequestSettings(
            String baseUrl,
            String apiKey,
            String organization,
            String project,
            Duration timeout
    ) {
    }
}
