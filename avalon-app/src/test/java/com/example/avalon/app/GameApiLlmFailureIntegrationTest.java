package com.example.avalon.app;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.runtime.service.GameSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AvalonApplication.class)
@AutoConfigureMockMvc
class GameApiLlmFailureIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameSessionService gameSessionService;

    @MockBean
    private AgentGateway agentGateway;

    @BeforeEach
    void setUp() {
        when(agentGateway.playTurn(any())).thenReturn(new AgentTurnResult());
    }

    @Test
    void runShouldPauseAndPersistAuditWhenLlmOutputIsInvalid() throws Exception {
        when(agentGateway.playTurn(any())).thenThrow(new OpenAiCompatibleResponseException(
                "OpenAI-compatible assistant content was empty (shape=reasoning_only, reasoningPreview=这里只返回了 reasoning。)",
                null,
                "minimax",
                "minimax-m2.7",
                "stop",
                new OpenAiCompatibleMessageAnalysis(
                        false,
                        true,
                        "reasoning_only",
                        null,
                        "这里只返回了 reasoning。",
                        null
                )
        ));

        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(202L);
        request.setPlayers(playersWithSingleLlmSeat());

        String createResponseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(createResponseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(get("/games/{gameId}/state", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("PAUSED"));

        String auditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].playerId").value("P1"))
                .andExpect(jsonPath("$[0].visibility").value("ADMIN_ONLY"))
                .andExpect(jsonPath("$[0].errorMessage").value("Agent turn validation failed after 2 attempts"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode auditEntries = objectMapper.readTree(auditResponseBody);
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("\"valid\":false");
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("reasoning_only");
        assertThat(auditEntries.get(0).path("rawModelResponseJson").asText()).contains("\"assistantContentShape\":\"reasoning_only\"");
        assertThat(auditEntries.get(0).path("rawModelResponseJson").asText()).contains("这里只返回了 reasoning。");
    }

    @Test
    void pausedGameShouldRecoverReplayAndAvoidDuplicateFailureAudits() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(303L);
        request.setPlayers(playersWithSingleLlmSeat());

        String createResponseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(createResponseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        String replayResponseBody = mockMvc.perform(get("/games/{gameId}/replay", gameId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode replaySteps = objectMapper.readTree(replayResponseBody);
        JsonNode pausedStep = null;
        for (JsonNode replayStep : replaySteps) {
            if ("GAME_PAUSED".equals(replayStep.path("type").asText())) {
                pausedStep = replayStep;
                break;
            }
        }
        assertThat(pausedStep).isNotNull();
        assertThat(pausedStep.path("replayKind").asText()).isEqualTo("RUN_PAUSED");

        String firstAuditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode firstAuditEntries = objectMapper.readTree(firstAuditResponseBody);
        String firstAuditId = firstAuditEntries.get(0).path("auditId").asText();

        gameSessionService.evict(gameId);

        mockMvc.perform(get("/games/{gameId}/state", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        String secondAuditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode secondAuditEntries = objectMapper.readTree(secondAuditResponseBody);
        assertThat(secondAuditEntries.get(0).path("auditId").asText()).isEqualTo(firstAuditId);
    }

    @Test
    void stepShouldContinueWhenOptionalPayloadSectionsAreMalformed() throws Exception {
        when(agentGateway.playTurn(any())).thenAnswer(invocation -> successfulSpeechWithOptionalWarnings(
                invocation.getArgument(0, AgentTurnRequest.class)
        ));

        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(404L);
        request.setPlayers(playersWithSingleLlmSeat());

        String createResponseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(createResponseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        String stepResponseBody = mockMvc.perform(post("/games/{gameId}/step", gameId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode stepResponse = objectMapper.readTree(stepResponseBody);
        assertThat(stepResponse.path("status").asText()).isEqualTo("RUNNING");

        String auditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode auditEntries = objectMapper.readTree(auditResponseBody);
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("\"valid\":true");
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("\"optionalSectionWarnings\"");
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("memoryUpdate");
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

    private AgentTurnResult successfulSpeechWithOptionalWarnings(AgentTurnRequest request) {
        AgentTurnResult result = new AgentTurnResult();
        result.setPublicSpeech("我先给一个低风险思路。");
        result.setPrivateThought("先过第一轮信息。");
        result.setActionJson("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"我先给一个低风险思路。\"}");
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider(request.getProvider());
        metadata.setModelName(request.getModelName());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("assistantContentShape", "json_object");
        attributes.put("finishReason", "stop");
        attributes.put("optionalSectionWarnings", List.of(Map.of(
                "field", "memoryUpdate",
                "reason", "dto_conversion_failed",
                "contentPreview", "{\"trustDelta\":{\"P1\":{\"score\":1}}}"
        )));
        metadata.setAttributes(attributes);
        result.setModelMetadata(metadata);
        return result;
    }
}
