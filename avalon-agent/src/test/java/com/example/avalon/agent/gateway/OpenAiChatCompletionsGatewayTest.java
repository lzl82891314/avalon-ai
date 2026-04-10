package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatCompletionsGatewayTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldBuildChatCompletionRequestAndParseStructuredResponse() throws Exception {
        AtomicReference<URI> uriRef = new AtomicReference<>();
        AtomicReference<Map<String, String>> headersRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<Duration> timeoutRef = new AtomicReference<>();
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            uriRef.set(uri);
            headersRef.set(new LinkedHashMap<>(headers));
            bodyRef.set(requestBody);
            timeoutRef.set(timeout);
            return json("""
                    {
                      "id":"chatcmpl-test",
                      "model":"gpt-5.2",
                      "service_tier":"default",
                      "usage":{"prompt_tokens":123,"completion_tokens":45},
                      "choices":[
                        {
                          "finish_reason":"stop",
                          "message":{
                            "content":"{\\"publicSpeech\\":\\"我赞成这支队伍。\\",\\"privateThought\\":\\"当前队伍风险可控，可以先通过观察后续任务结果。\\",\\"action\\":{\\"actionType\\":\\"TEAM_VOTE\\",\\"vote\\":\\"APPROVE\\"},\\"auditReason\\":{\\"goal\\":\\"生成一个合法的 TEAM_VOTE 动作\\",\\"reasonSummary\\":[\\"当前队伍看起来可接受\\"],\\"confidence\\":0.72,\\"beliefs\\":{\\"trust\\":\\"moderate\\"}},\\"memoryUpdate\\":{\\"observationsToAdd\\":[\\"队长提出了一支相对稳定的队伍\\"],\\"strategyMode\\":\\"BALANCED\\",\\"lastSummary\\":\\"我在第一轮通过了当前提案\\"}}"
                          }
                        }
                      ]
                    }
                    """);
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);
        AgentTurnRequest request = request("minimax");

        AgentTurnResult result = gateway.playTurn(request);
        JsonNode requestBody = objectMapper.readTree(bodyRef.get());

        assertEquals(URI.create("https://api.openai.test/v1/chat/completions"), uriRef.get());
        assertEquals("Bearer test-key", headersRef.get().get("Authorization"));
        assertEquals("org-1", headersRef.get().get("OpenAI-Organization"));
        assertEquals("proj-1", headersRef.get().get("OpenAI-Project"));
        assertEquals(Duration.ofMillis(1500), timeoutRef.get());
        assertEquals("gpt-5.2", requestBody.path("model").asText());
        assertEquals(0.2, requestBody.path("temperature").asDouble());
        assertEquals(640, requestBody.path("max_completion_tokens").asInt());
        assertEquals("json_object", requestBody.path("response_format").path("type").asText());
        assertEquals(7, requestBody.path("seed").asInt());
        assertEquals(true, requestBody.path("reasoning_split").asBoolean());
        assertEquals("system", requestBody.path("messages").get(0).path("role").asText());
        assertEquals("user", requestBody.path("messages").get(1).path("role").asText());
        assertTrue(requestBody.path("messages").get(1).path("content").asText().contains("可执行动作"));

        assertEquals("我赞成这支队伍。", result.getPublicSpeech());
        assertEquals("当前队伍风险可控，可以先通过观察后续任务结果。", result.getPrivateThought());
        assertEquals("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}", result.getActionJson());
        assertEquals("生成一个合法的 TEAM_VOTE 动作", result.getAuditReason().getGoal());
        assertEquals("BALANCED", result.getMemoryUpdate().getStrategyMode());
        assertEquals("minimax", result.getModelMetadata().getProvider());
        assertEquals("gpt-5.2", result.getModelMetadata().getModelName());
        assertEquals(123L, result.getModelMetadata().getInputTokens());
        assertEquals(45L, result.getModelMetadata().getOutputTokens());
        assertEquals("openai-compatible", result.getModelMetadata().getAttributes().get("gatewayType"));
        assertEquals("stop", result.getModelMetadata().getAttributes().get("finishReason"));
        assertEquals("json_object", result.getModelMetadata().getAttributes().get("assistantContentShape"));
    }

    @Test
    void shouldParseStructuredInferencePayload() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "id":"chatcmpl-structured",
                          "model":"gpt-5.2",
                          "usage":{"prompt_tokens":21,"completion_tokens":12},
                          "choices":[
                            {
                              "finish_reason":"stop",
                              "message":{
                                "content":"{\\"beliefsByPlayerId\\":{\\"P2\\":{\\"firstOrderEvilScore\\":0.81,\\"secondOrderAwarenessScore\\":0.45,\\"thirdOrderManipulationRisk\\":0.62,\\"confidence\\":0.77}},\\"strategyMode\\":\\"PRESSURE_TEST\\",\\"lastSummary\\":\\"P2 需要持续施压\\",\\"observationsToAdd\\":[\\"P2 投票偏激进\\"],\\"inferredFactsToAdd\\":[\\"P2 可能在试探我\\"]}"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        StructuredInferenceResult result = gateway.infer(structuredRequest("openai"));

        assertEquals(0.81, result.getPayload().path("beliefsByPlayerId").path("P2").path("firstOrderEvilScore").asDouble());
        assertEquals("PRESSURE_TEST", result.getPayload().path("strategyMode").asText());
        assertTrue(result.getRawJson().contains("beliefsByPlayerId"));
        assertEquals(21L, result.getModelMetadata().getInputTokens());
        assertEquals(12L, result.getModelMetadata().getOutputTokens());
        assertEquals("json_object", result.getModelMetadata().getAttributes().get("assistantContentShape"));
    }

    @Test
    void shouldRejectMissingApiKeyConfiguration() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> objectMapper.createObjectNode(),
                name -> null
        );
        AgentTurnRequest request = request("openai");
        request.setProviderOptions(Map.of());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> gateway.playTurn(request));

        assertTrue(error.getMessage().contains("OpenAI-compatible provider 'openai' requires an API key"));
    }

    @Test
    void shouldUseLongerDefaultTimeoutForHighLatencyProviders() {
        AtomicReference<Duration> timeoutRef = new AtomicReference<>();
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            timeoutRef.set(timeout);
            return json("""
                    {
                      "choices":[
                        {
                          "message":{
                            "content":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                          }
                        }
                      ]
                    }
                    """);
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);
        AgentTurnRequest request = request("glm");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1",
                "response_format", Map.of("type", "json_object")
        ));

        gateway.playTurn(request);

        assertEquals(Duration.ofSeconds(60), timeoutRef.get());
    }

    @Test
    void shouldPreferExplicitTimeoutOverProviderDefault() {
        AtomicReference<Duration> timeoutRef = new AtomicReference<>();
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            timeoutRef.set(timeout);
            return json("""
                    {
                      "choices":[
                        {
                          "message":{
                            "content":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                          }
                        }
                      ]
                    }
                    """);
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);
        AgentTurnRequest request = request("glm");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1",
                "timeoutMillis", 1234,
                "response_format", Map.of("type", "json_object")
        ));

        gateway.playTurn(request);

        assertEquals(Duration.ofMillis(1234), timeoutRef.get());
    }

    @Test
    void shouldDeriveCompatibilityOptionsFromAvalonRuntimeWithoutForwardingIt() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<Duration> timeoutRef = new AtomicReference<>();
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            bodyRef.set(requestBody);
            timeoutRef.set(timeout);
            return json("""
                    {
                      "choices":[
                        {
                          "message":{
                            "content":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                          }
                        }
                      ]
                    }
                    """);
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);
        AgentTurnRequest request = new AgentTurnRequest();
        request.setProvider("glm");
        request.setModelName("glm-5");
        request.setTemperature(0.2);
        request.setMaxTokens(180);
        request.setPromptText("Return JSON.");
        request.setOutputSchemaVersion("v1");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1",
                "avalonRuntime", Map.of(
                        "admissionEligible", true,
                        "instructionRole", "system",
                        "enableReasoningSplit", true,
                        "responseFormatJsonObject", true,
                        "defaultTimeoutMillis", 4321,
                        "minimumCompletionTokens", 720
                )
        ));

        gateway.playTurn(request);

        JsonNode requestBody = objectMapper.readTree(bodyRef.get());
        assertFalse(requestBody.has("avalonRuntime"));
        assertEquals("system", requestBody.path("messages").get(0).path("role").asText());
        assertEquals("json_object", requestBody.path("response_format").path("type").asText());
        assertEquals(true, requestBody.path("reasoning_split").asBoolean());
        assertEquals(720, requestBody.path("max_completion_tokens").asInt());
        assertEquals(Duration.ofMillis(4321), timeoutRef.get());
    }

    @Test
    void shouldDefaultToDeveloperInstructionForNonMinimaxProviders() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            bodyRef.set(requestBody);
            return json("""
                    {
                      "choices":[
                        {
                          "message":{
                            "content":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                          }
                        }
                      ]
                    }
                    """);
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);

        gateway.playTurn(request("openai"));

        JsonNode requestBody = objectMapper.readTree(bodyRef.get());
        assertEquals("developer", requestBody.path("messages").get(0).path("role").asText());
        assertFalse(requestBody.has("reasoning_split"));
    }

    @Test
    void shouldAcceptBaseUrlThatAlreadyIncludesChatCompletionsEndpoint() throws Exception {
        AtomicReference<URI> uriRef = new AtomicReference<>();
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            uriRef.set(uri);
            return json("""
                    {
                      "choices":[
                        {
                          "message":{
                            "content":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                          }
                        }
                      ]
                    }
                    """);
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);
        AgentTurnRequest request = request("openai");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1/chat/completions",
                "response_format", Map.of("type", "json_object")
        ));

        gateway.playTurn(request);

        assertEquals(URI.create("https://api.openai.test/v1/chat/completions"), uriRef.get());
    }

    @Test
    void shouldWrapTransportDiagnosticsFromHttpTransport() {
        OpenAiHttpTransport transport = (uri, headers, requestBody, timeout) -> {
            throw new OpenAiCompatibleTransportException(
                    "OpenAI-compatible HTTP transport failed after 3/3 attempts (java.net.http.HttpTimeoutException: request timed out)",
                    new HttpTimeoutException("request timed out"),
                    Map.of(
                            "failureDomain", "transport",
                            "failureKind", "timeout",
                            "transportAttempts", 3,
                            "timeoutMs", 60000L,
                            "rootExceptionClass", "java.net.http.HttpTimeoutException",
                            "rootExceptionMessage", "request timed out"
                    )
            );
        };
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(transport, name -> null);
        AgentTurnRequest request = request("glm");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1",
                "response_format", Map.of("type", "json_object")
        ));

        OpenAiCompatibleResponseException error = assertInstanceOf(
                OpenAiCompatibleResponseException.class,
                assertThrows(RuntimeException.class, () -> gateway.playTurn(request))
        );

        assertEquals("transport", error.diagnostics().get("failureDomain"));
        assertEquals("timeout", error.diagnostics().get("failureKind"));
        assertEquals(3, error.diagnostics().get("transportAttempts"));
        assertEquals(60000L, error.diagnostics().get("timeoutMs"));
        assertEquals("java.net.http.HttpTimeoutException", error.diagnostics().get("rootExceptionClass"));
        assertTrue(error.getMessage().contains("HttpTimeoutException"));
    }

    @Test
    void shouldStripLeadingThinkBlockBeforeParsingJson() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "message":{
                                "content":"<think>internal reasoning</think>\\n{\\"privateThought\\":\\"已完成推理\\",\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        AgentTurnResult result = gateway.playTurn(request("minimax"));

        assertEquals("已完成推理", result.getPrivateThought());
        assertEquals("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"hi\"}", result.getActionJson());
        assertEquals("think_prefixed_json", result.getModelMetadata().getAttributes().get("assistantContentShape"));
    }

    @Test
    void shouldParseJsonInsideMarkdownCodeBlock() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "message":{
                                "content":"```json\\n{\\"privateThought\\":\\"保持简短\\",\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}\\n```"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        AgentTurnResult result = gateway.playTurn(request("minimax"));

        assertEquals("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"hi\"}", result.getActionJson());
        assertEquals("markdown_code_block", result.getModelMetadata().getAttributes().get("assistantContentShape"));
    }

    @Test
    void shouldParseEmbeddedJsonObjectInsideExplanation() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "message":{
                                "content":"下面是最终答案： {\\"privateThought\\":\\"先观察一轮\\",\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        AgentTurnResult result = gateway.playTurn(request("minimax"));

        assertEquals("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"hi\"}", result.getActionJson());
        assertEquals("embedded_json_object", result.getModelMetadata().getAttributes().get("assistantContentShape"));
    }

    @Test
    void shouldUseReasoningDetailsAsPrivateThoughtFallback() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "finish_reason":"stop",
                              "message":{
                                "content":"{\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}",
                                "reasoning_details":[{"type":"reasoning.text","text":"先保持简短，再进入公开发言。"}]
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        AgentTurnResult result = gateway.playTurn(request("minimax"));

        assertEquals("先保持简短，再进入公开发言。", result.getPrivateThought());
        assertEquals("先保持简短，再进入公开发言。", result.getModelMetadata().getAttributes().get("reasoningDetailsPreview"));
    }

    @Test
    void shouldExposeDiagnosticsWhenContentIsReasoningOnly() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "finish_reason":"stop",
                              "message":{
                                "content":null,
                                "reasoning_details":[{"type":"reasoning.text","text":"这里只返回 reasoning。"}]
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        RuntimeException error = assertThrows(RuntimeException.class, () -> gateway.playTurn(request("minimax")));
        OpenAiCompatibleResponseException responseException = assertInstanceOf(OpenAiCompatibleResponseException.class, error);

        assertTrue(responseException.getMessage().contains("assistant content was empty"));
        assertEquals("reasoning_only", responseException.diagnostics().get("assistantContentShape"));
        assertEquals("这里只返回 reasoning。", responseException.diagnostics().get("reasoningDetailsPreview"));
    }

    @Test
    void shouldIncludeShapeAndBodyPreviewWhenAssistantContentIsNotJson() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "message":{
                                "content":"<think>internal reasoning</think>\\nHi there!"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> gateway.playTurn(request("minimax")));

        assertTrue(error.getMessage().contains("shape=plain_text"));
        assertTrue(error.getMessage().contains("bodyPreview="));
        assertTrue(error.getMessage().contains("Hi there!"));
    }

    @Test
    void shouldClassifyTruncatedJsonCandidateAndSurfaceFinishReason() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "finish_reason":"length",
                              "message":{
                                "content":"{\\"publicSpeech\\":\\"我是1号，先给个保守思路。\\",\\"privateThought\\":\\"先做低风险验证\\",\\"action\\":"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        RuntimeException error = assertThrows(RuntimeException.class, () -> gateway.playTurn(request("openai")));
        OpenAiCompatibleResponseException responseException = assertInstanceOf(OpenAiCompatibleResponseException.class, error);

        assertTrue(responseException.getMessage().contains("truncated JSON"));
        assertTrue(responseException.getMessage().contains("finishReason=length"));
        assertEquals("truncated_json_candidate", responseException.diagnostics().get("assistantContentShape"));
        assertEquals("length", responseException.diagnostics().get("finishReason"));
    }

    @Test
    void shouldParseTextFromOutputTextContentArray() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "message":{
                                "content":[
                                  {
                                    "type":"output_text",
                                    "text":"{\\"privateThought\\":\\"保持简短\\",\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"}}"
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        AgentTurnResult result = gateway.playTurn(request("openai"));

        assertEquals("保持简短", result.getPrivateThought());
        assertEquals("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"hi\"}", result.getActionJson());
    }

    @Test
    void shouldIgnoreMalformedOptionalSectionsAndKeepValidAction() {
        OpenAiChatCompletionsGateway gateway = new OpenAiChatCompletionsGateway(
                (uri, headers, requestBody, timeout) -> json("""
                        {
                          "choices":[
                            {
                              "finish_reason":"stop",
                              "message":{
                                "content":"{\\"privateThought\\":\\"先保持队形\\",\\"action\\":{\\"actionType\\":\\"PUBLIC_SPEECH\\",\\"speechText\\":\\"hi\\"},\\"auditReason\\":\\"too much\\",\\"memoryUpdate\\":{\\"trustDelta\\":{\\"P1\\":{\\"score\\":1}}}}"
                              }
                            }
                          ]
                        }
                        """),
                name -> null
        );

        AgentTurnResult result = gateway.playTurn(request("openai"));
        List<Map<String, Object>> warnings = rawWarnings(result.getModelMetadata().getAttributes().get("optionalSectionWarnings"));

        assertEquals("先保持队形", result.getPrivateThought());
        assertEquals("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"hi\"}", result.getActionJson());
        assertNull(result.getAuditReason());
        assertNull(result.getMemoryUpdate());
        assertEquals(2, warnings.size());
        assertEquals("auditReason", warnings.get(0).get("field"));
        assertEquals("expected_json_object", warnings.get(0).get("reason"));
        assertTrue(String.valueOf(warnings.get(0).get("contentPreview")).contains("too much"));
        assertEquals("memoryUpdate", warnings.get(1).get("field"));
        assertEquals("dto_conversion_failed", warnings.get(1).get("reason"));
        assertTrue(String.valueOf(warnings.get(1).get("contentPreview")).contains("trustDelta"));
        assertEquals("stop", result.getModelMetadata().getAttributes().get("finishReason"));
    }

    private AgentTurnRequest request(String provider) {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setProvider(provider);
        request.setModelName("gpt-5.2");
        request.setTemperature(0.2);
        request.setMaxTokens(180);
        request.setPromptText("""
                你正在扮演一名阿瓦隆玩家。
                可执行动作：[TEAM_VOTE]
                """.strip());
        request.setOutputSchemaVersion("v1");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1",
                "organization", "org-1",
                "project", "proj-1",
                "timeoutMillis", 1500,
                "response_format", Map.of("type", "json_object"),
                "seed", 7
        ));
        return request;
    }

    private StructuredInferenceRequest structuredRequest(String provider) {
        StructuredInferenceRequest request = new StructuredInferenceRequest();
        request.setProvider(provider);
        request.setModelName("gpt-5.2");
        request.setTemperature(0.1);
        request.setMaxTokens(220);
        request.setDeveloperPrompt("Return JSON only.");
        request.setUserPrompt("Build tom beliefs for this turn.");
        request.setProviderOptions(Map.of(
                "apiKey", "test-key",
                "baseUrl", "https://api.openai.test/v1",
                "response_format", Map.of("type", "json_object")
        ));
        return request;
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build test JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rawWarnings(Object value) {
        return assertInstanceOf(List.class, value);
    }
}
