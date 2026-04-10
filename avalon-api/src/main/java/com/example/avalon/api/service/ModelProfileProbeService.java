package com.example.avalon.api.service;

import com.example.avalon.agent.gateway.AvalonRuntimeCompatibilityProfile;
import com.example.avalon.agent.gateway.AvalonRuntimeStageBudget;
import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import com.example.avalon.agent.gateway.OpenAiHttpTransport;
import com.example.avalon.agent.gateway.ModelProfileApiKeyResolver;
import com.example.avalon.agent.gateway.ModelProfileSecretSupport;
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
    private static final List<ProbeCheckType> DEFAULT_CHECKS = List.of(
            ProbeCheckType.CONNECTIVITY,
            ProbeCheckType.STRUCTURED_JSON
    );
    private static final Pattern STATUS_PATTERN = Pattern.compile("status\\s+(\\d{3})");

    private final ModelProfileCatalogService modelProfileCatalogService;
    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ModelProfileProbeService(ModelProfileCatalogService modelProfileCatalogService,
                                    OpenAiHttpTransport transport,
                                    ModelProfileApiKeyResolver apiKeyResolver) {
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.transport = transport;
        this.apiKeyResolver = apiKeyResolver;
    }

    public ModelProfileProbeResponse probe(String modelId, ModelProfileProbeRequest request) {
        CatalogModelProfile profile = modelProfileCatalogService.requireCatalogProfile(modelId);
        AvalonRuntimeCompatibilityProfile compatibility = OpenAiCompatibleSupport.compatibilityProfile(
                profile.provider(),
                profile.providerOptions()
        );
        List<ProbeCheckType> checksToRun = normalizeChecks(request == null ? List.of() : request.getChecks());

        List<ModelProfileProbeCheckResponse> checks = new ArrayList<>();
        for (ProbeCheckType checkType : checksToRun) {
            checks.add(runCheck(profile, compatibility, checkType));
        }

        Boolean reachable = checkResult(checks, ProbeCheckType.CONNECTIVITY);
        Boolean structuredCompatible = structuredCompatibility(checks);
        Boolean avalonCompatible = avalonCompatibility(checks, structuredCompatible);
        List<String> failedStages = failedStages(checks);
        boolean configuredAdmission = OpenAiCompatibleSupport.admissionEligible(profile.provider(), profile.providerOptions());
        Boolean admissionEligible = configuredAdmission
                && !Boolean.FALSE.equals(structuredCompatible)
                && !Boolean.FALSE.equals(avalonCompatible);

        ModelProfileProbeResponse response = new ModelProfileProbeResponse();
        response.setModelId(profile.modelId());
        response.setProvider(profile.provider());
        response.setModelName(profile.modelName());
        response.setBaseUrl(defaultBaseUrl(OpenAiCompatibleSupport.stringOption(profile.providerOptions(), "baseUrl")));
        response.setChecks(checks);
        response.setReachable(reachable);
        response.setStructuredCompatible(structuredCompatible);
        response.setAvalonCompatible(avalonCompatible);
        response.setAdmissionEligible(admissionEligible);
        response.setFailedStages(failedStages);
        response.setDiagnosis(diagnosis(reachable, structuredCompatible, avalonCompatible, admissionEligible));
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
            String normalized = requestedCheck.trim().toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "ALL" -> {
                    checks.add(ProbeCheckType.CONNECTIVITY);
                    checks.add(ProbeCheckType.AVALON_BELIEF);
                    checks.add(ProbeCheckType.AVALON_TOT);
                    checks.add(ProbeCheckType.AVALON_CRITIC);
                    checks.add(ProbeCheckType.AVALON_DECISION);
                }
                case "AVALON" -> {
                    checks.add(ProbeCheckType.AVALON_BELIEF);
                    checks.add(ProbeCheckType.AVALON_TOT);
                    checks.add(ProbeCheckType.AVALON_CRITIC);
                    checks.add(ProbeCheckType.AVALON_DECISION);
                }
                default -> checks.add(ProbeCheckType.valueOf(normalized));
            }
        }
        return checks.isEmpty() ? DEFAULT_CHECKS : List.copyOf(checks);
    }

    private ModelProfileProbeCheckResponse runCheck(CatalogModelProfile profile,
                                                    AvalonRuntimeCompatibilityProfile compatibility,
                                                    ProbeCheckType checkType) {
        return switch (checkType) {
            case CONNECTIVITY -> runConnectivityCheck(profile, compatibility);
            case STRUCTURED_JSON -> runStageCheck(
                    profile,
                    compatibility,
                    checkType,
                    "decision-stage",
                    "你正在执行阿瓦隆 structured-json 探测。只返回一个最小合法 JSON 对象。",
                    "当前阶段只允许 PUBLIC_SPEECH。请输出 action.actionType=PUBLIC_SPEECH 且包含 speechText。",
                    payload -> requireAction(payload)
            );
            case AVALON_BELIEF -> runStageCheck(
                    profile,
                    compatibility,
                    checkType,
                    "belief-stage",
                    "你正在执行阿瓦隆 belief-stage 探测。只返回 beliefsByPlayerId、strategyMode、lastSummary、observationsToAdd、inferredFactsToAdd。",
                    """
                            当前是 5 人局第 1 轮公开讨论阶段。
                            你是 P1，身份是 LOYAL_SERVANT。
                            请只对 P2、P3、P4、P5 建立 beliefsByPlayerId，并给出简短 strategyMode 与 summary。
                            """.strip(),
                    payload -> requireObjectField(payload, "beliefsByPlayerId")
            );
            case AVALON_TOT -> runStageCheck(
                    profile,
                    compatibility,
                    checkType,
                    "tot-stage",
                    "你正在执行阿瓦隆 tot-stage 探测。只返回 candidates、selectedCandidateId、summary。",
                    "请生成候选行动并预测桌面反应，最后选出一个 selectedCandidateId。",
                    payload -> {
                        requireField(payload, "candidates");
                        requireField(payload, "selectedCandidateId");
                    }
            );
            case AVALON_CRITIC -> runStageCheck(
                    profile,
                    compatibility,
                    checkType,
                    "critic-stage",
                    "你正在执行阿瓦隆 critic-stage 探测。只返回 status、riskFindings、counterSignals、recommendedAdjustments、summary。",
                    "请站在怀疑者视角审视一个候选行动，并给出 status。",
                    payload -> requireField(payload, "status")
            );
            case AVALON_DECISION -> runStageCheck(
                    profile,
                    compatibility,
                    checkType,
                    "decision-stage",
                    "你正在执行阿瓦隆 decision-stage 探测。只返回 action/publicSpeech/privateThought 的最小合法 JSON。",
                    "当前阶段只允许 TEAM_VOTE。请输出 action.actionType=TEAM_VOTE 且 vote=APPROVE 或 REJECT。",
                    this::requireAction
            );
        };
    }

    private ModelProfileProbeCheckResponse runConnectivityCheck(CatalogModelProfile profile,
                                                                AvalonRuntimeCompatibilityProfile compatibility) {
        long startedAt = System.nanoTime();
        ModelProfileProbeCheckResponse response = baseCheckResponse(ProbeCheckType.CONNECTIVITY, null, compatibility.profileId());
        try {
            RequestSettings settings = requestSettings(profile, compatibility, null);
            JsonNode payload = transport.postChatCompletion(
                    OpenAiCompatibleSupport.endpointUri(settings.baseUrl()),
                    headers(settings),
                    connectivityBody(profile),
                    settings.timeout()
            );
            response.setSuccess(true);
            response.setDiagnosisCode("OK");
            response.setHttpStatus(200);
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setFinishReason(textOrNull(payload.path("choices").path(0).path("finish_reason")));
            response.setAssistantPreview(previewAssistant(payload));
            return response;
        } catch (RuntimeException exception) {
            response.setSuccess(false);
            response.setDiagnosisCode(diagnosisCode(exception, null));
            response.setHttpStatus(extractStatus(exception.getMessage()));
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setErrorMessage(exception.getMessage());
            return response;
        }
    }

    private ModelProfileProbeCheckResponse runStageCheck(CatalogModelProfile profile,
                                                         AvalonRuntimeCompatibilityProfile compatibility,
                                                         ProbeCheckType checkType,
                                                         String stageId,
                                                         String developerPrompt,
                                                         String userPrompt,
                                                         StageValidator validator) {
        long startedAt = System.nanoTime();
        ModelProfileProbeCheckResponse response = baseCheckResponse(checkType, stageId, compatibility.profileId());
        try {
            RequestSettings settings = requestSettings(profile, compatibility, stageId);
            JsonNode payload = transport.postChatCompletion(
                    OpenAiCompatibleSupport.endpointUri(settings.baseUrl()),
                    headers(settings),
                    structuredBody(profile, compatibility, stageId, developerPrompt, userPrompt),
                    settings.timeout()
            );
            response.setHttpStatus(200);
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setFinishReason(textOrNull(payload.path("choices").path(0).path("finish_reason")));

            JsonNode message = payload.path("choices").path(0).path("message");
            OpenAiCompatibleMessageAnalysis analysis = OpenAiCompatibleSupport.analyzeAssistantMessage(message);
            applyAnalysis(response, analysis);

            JsonNode structuredPayload = OpenAiCompatibleSupport.readJson(objectMapper, analysis);
            validator.validate(structuredPayload);
            response.setSuccess(true);
            response.setDiagnosisCode("OK");
            return response;
        } catch (RuntimeException exception) {
            response.setSuccess(false);
            if (response.getHttpStatus() == null) {
                response.setHttpStatus(extractStatus(exception.getMessage()));
            }
            response.setLatencyMs(elapsedMillis(startedAt));
            response.setDiagnosisCode(diagnosisCode(exception, response.getContentShape()));
            response.setErrorMessage(exception.getMessage());
            return response;
        }
    }

    private ModelProfileProbeCheckResponse baseCheckResponse(ProbeCheckType checkType,
                                                             String stageId,
                                                             String requestProfileId) {
        ModelProfileProbeCheckResponse response = new ModelProfileProbeCheckResponse();
        response.setCheckType(checkType.name());
        response.setStageId(stageId);
        response.setRequestProfileId(requestProfileId);
        return response;
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

    private String structuredBody(CatalogModelProfile profile,
                                  AvalonRuntimeCompatibilityProfile compatibility,
                                  String stageId,
                                  String developerPrompt,
                                  String userPrompt) {
        Map<String, Object> providerOptions = OpenAiCompatibleSupport.effectiveProviderOptions(
                profile.provider(),
                profile.providerOptions()
        );
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", defaultModel(profile.modelName()));
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", compatibility.instructionRole())
                .put("content", developerPrompt);
        messages.addObject()
                .put("role", "user")
                .put("content", userPrompt);
        if (profile.temperature() != null) {
            root.put("temperature", profile.temperature());
        }
        int effectiveMaxTokens = stageMaxTokens(profile, stageId, providerOptions);
        if (effectiveMaxTokens > 0) {
            root.put("max_completion_tokens", effectiveMaxTokens);
        }
        for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
            if (!OpenAiCompatibleSupport.shouldForwardProviderOption(entry.getKey())) {
                continue;
            }
            root.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
        }
        return writeJson(root);
    }

    private int stageMaxTokens(CatalogModelProfile profile,
                               String stageId,
                               Map<String, Object> providerOptions) {
        AvalonRuntimeStageBudget stageBudget = OpenAiCompatibleSupport.stageBudget(profile.provider(), providerOptions, stageId);
        Integer requestedMaxTokens = stageBudget != null && stageBudget.maxTokens() != null
                ? stageBudget.maxTokens()
                : profile.maxTokens();
        return OpenAiCompatibleSupport.effectiveMaxTokens(profile.provider(), providerOptions, requestedMaxTokens);
    }

    private RequestSettings requestSettings(CatalogModelProfile profile,
                                            AvalonRuntimeCompatibilityProfile compatibility,
                                            String stageId) {
        Map<String, Object> providerOptions = profile.providerOptions();
        String apiKey = apiKeyResolver.resolveApiKey(profile.modelId(), providerOptions);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    ModelProfileSecretSupport.missingApiKeyMessage(profile.provider(), profile.modelId())
            );
        }
        String baseUrl = defaultBaseUrl(OpenAiCompatibleSupport.stringOption(providerOptions, "baseUrl"));
        String organization = OpenAiCompatibleSupport.stringOption(providerOptions, "organization");
        String project = OpenAiCompatibleSupport.stringOption(providerOptions, "project");
        AvalonRuntimeStageBudget stageBudget = stageId == null ? null : compatibility.stageBudget(stageId);
        Duration timeout = stageBudget != null && stageBudget.timeout() != null
                ? stageBudget.timeout()
                : compatibility.defaultTimeout();
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

    private void requireAction(JsonNode payload) {
        JsonNode actionNode = payload.get("action");
        if (actionNode == null || actionNode.isNull() || actionNode.isMissingNode() || !actionNode.isObject()) {
            throw new IllegalStateException("action object missing");
        }
    }

    private void requireObjectField(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject() || node.isEmpty()) {
            throw new IllegalStateException(fieldName + " missing");
        }
    }

    private void requireField(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new IllegalStateException(fieldName + " missing");
        }
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

    private String diagnosis(Boolean reachable,
                             Boolean structuredCompatible,
                             Boolean avalonCompatible,
                             Boolean admissionEligible) {
        if (Boolean.FALSE.equals(reachable)) {
            return "NETWORK_OR_AUTH_FAILED";
        }
        if (Boolean.FALSE.equals(avalonCompatible)) {
            return "AVALON_STAGE_FAILED";
        }
        if (Boolean.TRUE.equals(reachable) && Boolean.FALSE.equals(structuredCompatible)) {
            return "NETWORK_OK_BUT_STRUCTURED_JSON_FAILED";
        }
        if (Boolean.FALSE.equals(structuredCompatible)) {
            return "STRUCTURED_JSON_FAILED";
        }
        if (Boolean.FALSE.equals(admissionEligible)) {
            return "ADMISSION_DISABLED";
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

    private Boolean structuredCompatibility(List<ModelProfileProbeCheckResponse> checks) {
        Boolean structuredJson = checkResult(checks, ProbeCheckType.STRUCTURED_JSON);
        if (structuredJson != null) {
            return structuredJson;
        }
        return checkResult(checks, ProbeCheckType.AVALON_DECISION);
    }

    private Boolean avalonCompatibility(List<ModelProfileProbeCheckResponse> checks, Boolean structuredCompatible) {
        List<ModelProfileProbeCheckResponse> avalonChecks = checks.stream()
                .filter(check -> check.getCheckType() != null && check.getCheckType().startsWith("AVALON_"))
                .toList();
        if (avalonChecks.isEmpty()) {
            return null;
        }
        return avalonChecks.stream().allMatch(ModelProfileProbeCheckResponse::isSuccess);
    }

    private List<String> failedStages(List<ModelProfileProbeCheckResponse> checks) {
        return checks.stream()
                .filter(check -> check.getStageId() != null && !check.isSuccess())
                .map(ModelProfileProbeCheckResponse::getStageId)
                .distinct()
                .toList();
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

    private String diagnosisCode(RuntimeException exception, String contentShape) {
        if (exception instanceof OpenAiCompatibleResponseException responseException
                && "transport".equalsIgnoreCase(String.valueOf(responseException.diagnostics().get("failureDomain")))) {
            return "TRANSPORT_FAILURE";
        }
        if ("truncated_json_candidate".equals(contentShape)) {
            return "TRUNCATED_JSON";
        }
        if ("reasoning_only".equals(contentShape)) {
            return "REASONING_ONLY";
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("action object")) {
            return "MISSING_ACTION";
        }
        if (message.contains("beliefsByPlayerId")) {
            return "MISSING_BELIEFS";
        }
        if (message.contains("selectedCandidateId")) {
            return "MISSING_SELECTED_CANDIDATE";
        }
        if (message.contains("status")) {
            return "MISSING_STATUS";
        }
        return "VALIDATION_FAILED";
    }

    private enum ProbeCheckType {
        CONNECTIVITY,
        STRUCTURED_JSON,
        AVALON_BELIEF,
        AVALON_TOT,
        AVALON_CRITIC,
        AVALON_DECISION
    }

    @FunctionalInterface
    private interface StageValidator {
        void validate(JsonNode payload);
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
