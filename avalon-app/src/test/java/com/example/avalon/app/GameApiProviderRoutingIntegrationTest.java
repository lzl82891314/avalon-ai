package com.example.avalon.app;

import com.example.avalon.agent.gateway.OpenAiHttpTransport;
import com.example.avalon.api.dto.CreateGameRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AvalonApplication.class)
@AutoConfigureMockMvc
class GameApiProviderRoutingIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpenAiHttpTransport transport;

    @Test
    void supportedCompatibleProviderShouldRouteThroughSharedGatewayAndPersistProviderMetadata() throws Exception {
        when(transport.postChatCompletion(any(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(publicSpeechResponse("我先给出一段测试发言。"));

        String modelId = createManagedCompatibleProfile(
                "managed-minimax-routing",
                "minimax",
                "minimax-m2.7",
                """
                        {
                          "apiKey": "test-key",
                          "baseUrl": "https://mock.minimax.test/v1",
                          "response_format": {"type": "json_object"}
                        }
                        """
        );
        String gameId = createGame(roleBindingSelection(modelId));

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/games/{gameId}/step", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/games/{gameId}/state", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.phase").value("DISCUSSION"))
                .andExpect(jsonPath("$.nextRequiredActor").value("P2"));

        org.mockito.ArgumentCaptor<String> bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(transport, times(1)).postChatCompletion(any(), anyMap(), bodyCaptor.capture(), any(Duration.class));
        JsonNode requestBody = objectMapper.readTree(bodyCaptor.getValue());
        assertThat(requestBody.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(requestBody.path("reasoning_split").asBoolean()).isTrue();
        assertThat(requestBody.path("max_completion_tokens").asInt()).isEqualTo(640);

        String auditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode auditEntries = objectMapper.readTree(auditResponseBody);
        assertThat(auditEntries.get(0).path("rawModelResponseJson").asText()).contains("\"provider\":\"minimax\"");
        assertThat(auditEntries.get(0).path("rawModelResponseJson").asText()).contains("\"gatewayType\":\"openai-compatible\"");
    }

    @Test
    void compatibleProviderShouldRetryWithRaisedTokenBudgetAfterTruncatedJsonFailure() throws Exception {
        reset(transport);
        when(transport.postChatCompletion(any(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(truncatedJsonResponse())
                .thenReturn(publicSpeechResponse("我先给出一段压缩后的测试发言。"));

        String modelId = createManagedCompatibleProfile(
                "managed-minimax-retry",
                "minimax",
                "minimax-m2.7",
                320,
                """
                        {
                          "apiKey": "test-key",
                          "baseUrl": "https://mock.minimax.test/v1",
                          "response_format": {"type": "json_object"}
                        }
                        """
        );
        String gameId = createGame(roleBindingSelection(modelId));

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/games/{gameId}/step", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        org.mockito.ArgumentCaptor<String> bodyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(transport, times(2)).postChatCompletion(any(), anyMap(), bodyCaptor.capture(), any(Duration.class));
        List<String> bodies = bodyCaptor.getAllValues();
        JsonNode firstRequestBody = objectMapper.readTree(bodies.get(0));
        JsonNode secondRequestBody = objectMapper.readTree(bodies.get(1));

        assertThat(firstRequestBody.path("max_completion_tokens").asInt()).isEqualTo(640);
        assertThat(secondRequestBody.path("max_completion_tokens").asInt()).isEqualTo(640);
        assertThat(secondRequestBody.path("messages").get(1).path("content").asText()).contains("优先先写 action");
    }

    @Test
    void unsupportedProviderShouldPauseAndExposeProviderErrorWithoutCallingTransport() throws Exception {
        String modelId = createManagedCompatibleProfile(
                "managed-anthropic-routing",
                "anthropic",
                "claude-sonnet",
                "{}"
        );
        String gameId = createGame(roleBindingSelection(modelId));

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/games/{gameId}/step", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        verifyNoInteractions(transport);

        String auditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode auditEntries = objectMapper.readTree(auditResponseBody);
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("Unsupported LLM provider 'anthropic'");
        assertThat(auditEntries.get(0).path("errorMessage").asText()).isEqualTo("Agent turn validation failed after 2 attempts");
    }

    @Test
    void probeEndpointShouldDistinguishConnectivityFromStructuredCompatibility() throws Exception {
        reset(transport);
        when(transport.postChatCompletion(any(), anyMap(), anyString(), any(Duration.class)))
                .thenReturn(simpleTextResponse("Hi there!"))
                .thenReturn(reasoningOnlyResponse("这里只返回了 reasoning。"));

        String modelId = createManagedCompatibleProfile(
                "managed-minimax-probe",
                "minimax",
                "minimax-m2.7",
                """
                        {
                          "apiKey": "test-key",
                          "baseUrl": "https://mock.minimax.test/v1",
                          "instructionRole": "system",
                          "response_format": {"type": "json_object"}
                        }
                        """
        );

        mockMvc.perform(post("/model-profiles/{modelId}/probe", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value(modelId))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.structuredCompatible").value(false))
                .andExpect(jsonPath("$.diagnosis").value("NETWORK_OK_BUT_STRUCTURED_JSON_FAILED"))
                .andExpect(jsonPath("$.checks", hasSize(2)))
                .andExpect(jsonPath("$.checks[0].checkType").value("CONNECTIVITY"))
                .andExpect(jsonPath("$.checks[0].success").value(true))
                .andExpect(jsonPath("$.checks[0].httpStatus").value(200))
                .andExpect(jsonPath("$.checks[1].checkType").value("STRUCTURED_JSON"))
                .andExpect(jsonPath("$.checks[1].success").value(false))
                .andExpect(jsonPath("$.checks[1].httpStatus").value(200))
                .andExpect(jsonPath("$.checks[1].contentPresent").value(false))
                .andExpect(jsonPath("$.checks[1].reasoningDetailsPresent").value(true))
                .andExpect(jsonPath("$.checks[1].contentShape").value("reasoning_only"))
                .andExpect(jsonPath("$.checks[1].reasoningDetailsPreview").value("这里只返回了 reasoning。"))
                .andExpect(jsonPath("$.checks[1].errorMessage", containsString("assistant content was empty")));
    }

    private JsonNode publicSpeechResponse(String speechText) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "action", Map.of(
                        "actionType", "PUBLIC_SPEECH",
                        "speechText", speechText
                ),
                "publicSpeech", speechText,
                "privateThought", "这是一次 provider 路由测试。",
                "auditReason", Map.of(
                        "goal", "生成一个合法的 PUBLIC_SPEECH 动作",
                        "reasonSummary", List.of("测试共享 OpenAI-compatible provider 路由"),
                        "confidence", 0.9
                )
        ));
        return objectMapper.readTree("""
                {
                  "id": "chatcmpl-provider-routing",
                  "model": "minimax-m2.7",
                  "usage": {"prompt_tokens": 17, "completion_tokens": 9},
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": %s
                      }
                    }
                  ]
                }
                """.formatted(objectMapper.writeValueAsString(payload)));
    }

    private JsonNode simpleTextResponse(String content) throws Exception {
        return objectMapper.readTree("""
                {
                  "id": "chatcmpl-probe",
                  "model": "minimax-m2.7",
                  "usage": {"prompt_tokens": 12, "completion_tokens": 5},
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": %s
                      }
                    }
                  ]
                }
                """.formatted(objectMapper.writeValueAsString(content)));
    }

    private JsonNode truncatedJsonResponse() throws Exception {
        return objectMapper.readTree("""
                {
                  "id": "chatcmpl-truncated",
                  "model": "minimax-m2.7",
                  "usage": {"prompt_tokens": 12, "completion_tokens": 320},
                  "choices": [
                    {
                      "finish_reason": "length",
                      "message": {
                        "content": "{\\"publicSpeech\\":\\"我是1号，先给个保守思路。\\",\\"privateThought\\":\\"先做低风险验证\\",\\"action\\":"
                      }
                    }
                  ]
                }
                """);
    }

    private JsonNode reasoningOnlyResponse(String reasoningText) throws Exception {
        return objectMapper.readTree("""
                {
                  "id": "chatcmpl-probe",
                  "model": "minimax-m2.7",
                  "usage": {"prompt_tokens": 12, "completion_tokens": 5},
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": {
                        "content": null,
                        "reasoning_details": [
                          {
                            "type": "reasoning.text",
                            "text": %s
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(objectMapper.writeValueAsString(reasoningText)));
    }

    private String createManagedCompatibleProfile(String modelId,
                                                  String provider,
                                                  String modelName,
                                                  String providerOptionsJson) throws Exception {
        return createManagedCompatibleProfile(modelId, provider, modelName, 180, providerOptionsJson);
    }

    private String createManagedCompatibleProfile(String modelId,
                                                  String provider,
                                                  String modelName,
                                                  int maxTokens,
                                                  String providerOptionsJson) throws Exception {
        String responseBody = mockMvc.perform(post("/model-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelId": "%s",
                                  "displayName": "%s",
                                  "provider": "%s",
                                  "modelName": "%s",
                                  "temperature": 0.2,
                                  "maxTokens": %s,
                                  "providerOptions": %s,
                                  "enabled": true
                                }
                                """.formatted(modelId, modelId, provider, modelName, maxTokens, providerOptionsJson)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).path("modelId").asText();
    }

    private String createGame(CreateGameRequest.LlmSelectionRequest llmSelection) throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(77L);
        request.setPlayers(playersWithSingleLlmSeat());
        request.setLlmSelection(llmSelection);

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).path("gameId").asText();
    }

    private List<CreateGameRequest.PlayerSlotRequest> playersWithSingleLlmSeat() {
        List<CreateGameRequest.PlayerSlotRequest> players = new ArrayList<>();
        for (int seatNo = 1; seatNo <= 5; seatNo++) {
            CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
            player.setSeatNo(seatNo);
            player.setDisplayName("P" + seatNo);
            player.setControllerType(seatNo == 1 ? "LLM" : "SCRIPTED");
            if (seatNo == 1) {
                player.setAgentConfig(new com.example.avalon.agent.model.PlayerAgentConfig());
            }
            players.add(player);
        }
        return players;
    }

    private CreateGameRequest.LlmSelectionRequest roleBindingSelection(String modelId) {
        CreateGameRequest.LlmSelectionRequest llmSelection = new CreateGameRequest.LlmSelectionRequest();
        llmSelection.setMode("ROLE_BINDING");
        llmSelection.setRoleBindings(Map.of(
                "MERLIN", modelId,
                "PERCIVAL", modelId,
                "LOYAL_SERVANT", modelId,
                "MORGANA", modelId,
                "ASSASSIN", modelId
        ));
        return llmSelection;
    }
}
