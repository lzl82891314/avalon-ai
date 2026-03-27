package com.example.avalon.app;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.store.AuditRecordStore;
import com.example.avalon.runtime.service.GameSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AvalonApplication.class)
@AutoConfigureMockMvc
class GameApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditRecordStore auditRecordStore;

    @Autowired
    private GameSessionService gameSessionService;

    @Test
    void controllerShouldExposeStateEventsReplayAndAudit() throws Exception {
        JsonNode createResponse = performCreateGame();
        String gameId = createResponse.get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/games/{gameId}/state", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.phase").value("DISCUSSION"))
                .andExpect(jsonPath("$.roundNo").value(1))
                .andExpect(jsonPath("$.nextRequiredActor").value("P1"))
                .andExpect(jsonPath("$.waitingReason").value("等待玩家公开发言"));

        mockMvc.perform(get("/games/{gameId}/events", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].type").value("GAME_CREATED"))
                .andExpect(jsonPath("$[1].type").value("GAME_STARTED"));

        mockMvc.perform(get("/games/{gameId}/replay", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].type").value("GAME_CREATED"))
                .andExpect(jsonPath("$[0].replayKind").value("GAME_STARTUP"))
                .andExpect(jsonPath("$[0].summary").value("游戏已创建"))
                .andExpect(jsonPath("$[1].type").value("GAME_STARTED"))
                .andExpect(jsonPath("$[1].replayKind").value("ROUND_OPENING"));

        auditRecordStore.save(new AuditRecord(
                "audit-1",
                gameId,
                2L,
                "P1",
                "ADMIN_ONLY",
                "{\"seed\":1}",
                "hash-1",
                "{\"raw\":true}",
                "{\"parsed\":true}",
                "{\"reason\":\"test\"}",
                "{\"valid\":true}",
                null,
                Instant.parse("2026-03-23T00:00:00Z")
        ));

        mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].auditId").value("audit-1"))
                .andExpect(jsonPath("$[0].eventSeqNo").value(2))
                .andExpect(jsonPath("$[0].visibility").value("ADMIN_ONLY"))
                .andExpect(jsonPath("$[0].rawModelResponseJson").value("{\"raw\":true}"));
    }

    @Test
    void playerViewShouldExposeOnlyPrivateViewFields() throws Exception {
        JsonNode createResponse = performCreateGame();
        String gameId = createResponse.get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/games/{gameId}/players/{playerId}/view", gameId, "P1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatNo").value(1))
                .andExpect(jsonPath("$.roleSummary").isNotEmpty())
                .andExpect(jsonPath("$.privateKnowledge.camp").isNotEmpty())
                .andExpect(jsonPath("$.privateKnowledge.notes").isArray())
                .andExpect(jsonPath("$.privateKnowledge.visiblePlayers").isArray())
                .andExpect(jsonPath("$.memorySnapshot").isMap())
                .andExpect(jsonPath("$.allowedActions[0]").value("PUBLIC_SPEECH"))
                .andExpect(jsonPath("$.gameId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.phase").doesNotExist())
                .andExpect(jsonPath("$.roundNo").doesNotExist())
                .andExpect(jsonPath("$.publicState").doesNotExist())
                .andExpect(jsonPath("$.events").doesNotExist())
                .andExpect(jsonPath("$.replay").doesNotExist())
                .andExpect(jsonPath("$.audit").doesNotExist());
    }

    @Test
    void playerViewShouldReflectRoleSpecificVisibility() throws Exception {
        JsonNode createResponse = performCreateGame();
        String gameId = createResponse.get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk());

        List<JsonNode> views = new ArrayList<>();
        for (int seatNo = 1; seatNo <= 5; seatNo++) {
            views.add(performPlayerView(gameId, "P" + seatNo));
        }

        Set<String> roleSummaries = views.stream()
                .map(view -> view.get("roleSummary").asText())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(roleSummaries).containsExactlyInAnyOrder("MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN");

        for (JsonNode view : views) {
            String roleSummary = view.get("roleSummary").asText();
            JsonNode visiblePlayers = view.path("privateKnowledge").path("visiblePlayers");
            switch (roleSummary) {
                case "MERLIN" -> {
                    List<String> exactRoles = jsonTextList(visiblePlayers, "exactRoleId");
                    assertThat(exactRoles).containsExactlyInAnyOrder("MORGANA", "ASSASSIN");
                }
                case "PERCIVAL" -> {
                    assertThat(visiblePlayers.size()).isEqualTo(2);
                    for (JsonNode candidate : visiblePlayers) {
                        assertThat(jsonTextList(candidate.path("candidateRoleIds"))).containsExactlyInAnyOrder("MERLIN", "MORGANA");
                        assertThat(candidate.path("exactRoleId").isNull()).isTrue();
                    }
                }
                case "LOYAL_SERVANT" -> assertThat(visiblePlayers.isEmpty()).isTrue();
                case "ASSASSIN" -> {
                    List<String> exactRoles = jsonTextList(visiblePlayers, "exactRoleId");
                    assertThat(exactRoles).containsExactly("MORGANA");
                }
                default -> {
                }
            }
        }
    }

    @Test
    void playerViewShouldExposePhaseSpecificAllowedActions() throws Exception {
        JsonNode createResponse = performCreateGame();
        String gameId = createResponse.get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk());

        for (int step = 0; step < 5; step++) {
            mockMvc.perform(post("/games/{gameId}/step", gameId))
                    .andExpect(status().isOk());
        }

        JsonNode leaderView = performPlayerView(gameId, "P1");
        JsonNode nonLeaderView = performPlayerView(gameId, "P2");

        assertThat(jsonTextList(leaderView.path("allowedActions"))).containsExactly("TEAM_PROPOSAL");
        assertThat(jsonTextList(nonLeaderView.path("allowedActions"))).isEmpty();
    }

    @Test
    void submitPlayerActionShouldReturnNotImplemented() throws Exception {
        JsonNode createResponse = performCreateGame();
        String gameId = createResponse.get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/players/{playerId}/actions", gameId, "P1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.message").value("human action submission is reserved for V2"));
    }

    @Test
    void runShouldSupportLlmControlledSeatWithDeterministicGateway() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(99L);
        request.setPlayers(playersWithSingleLlmSeat());

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(responseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("ENDED"));
    }

    @Test
    void runShouldPersistAuditEntriesForLlmControlledSeat() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(101L);
        request.setPlayers(playersWithSingleLlmSeat());

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(responseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        String auditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].playerId").value("P1"))
                .andExpect(jsonPath("$[0].visibility").value("ADMIN_ONLY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode auditEntries = objectMapper.readTree(auditResponseBody);
        assertThat(auditEntries.get(0).path("parsedActionJson").asText()).contains("actionType");
        assertThat(auditEntries.get(0).path("rawModelResponseJson").asText()).contains("privateThought");
        assertThat(auditEntries.get(0).path("auditReasonJson").asText()).contains("生成一个合法的");
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("\"valid\":true");
        assertThat(auditEntries.get(0).path("validationResultJson").asText()).contains("\"attempts\"");
    }

    @Test
    void modelProfileEndpointsShouldSupportStaticAndManagedCatalogViews() throws Exception {
        String listResponseBody = mockMvc.perform(get("/model-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$[0].modelId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode openAiProfile = openAiProfile(objectMapper.readTree(listResponseBody));

        assertThat(openAiProfile.path("displayName").asText()).isEqualTo("OpenAI GPT-5.4");
        assertThat(openAiProfile.path("editable").asBoolean()).isFalse();
        assertThat(openAiProfile.path("source").asText()).isEqualTo("STATIC");
        assertThat(openAiProfile.path("provider").asText()).isEqualTo("openai");
        assertThat(openAiProfile.path("providerOptions").path("baseUrl").asText()).isEqualTo("https://gcapi.cn/v1");
        assertThat(openAiProfile.path("providerOptions").has("apiKey")).isFalse();

        mockMvc.perform(get("/model-profiles/{modelId}", "minimax-m2.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("minimax-m2.7"))
                .andExpect(jsonPath("$.provider").value("minimax"))
                .andExpect(jsonPath("$.providerOptions.baseUrl").value("https://gcapi.cn/v1"))
                .andExpect(jsonPath("$.providerOptions.instructionRole").value("system"))
                .andExpect(jsonPath("$.providerOptions.apiKey").doesNotExist());

        mockMvc.perform(get("/model-profiles/{modelId}", "claude-compatible-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("claude"));

        mockMvc.perform(get("/model-profiles/{modelId}", "glm-compatible-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("glm"));

        mockMvc.perform(get("/model-profiles/{modelId}", "qwen-compatible-template"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("qwen"));

        mockMvc.perform(get("/model-profiles/{modelId}", "minimax-m2.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.source").value("STATIC"));

        String createBody = """
                {
                  "modelId": "managed-noop-zeta",
                  "displayName": "Managed Noop Zeta",
                  "provider": "noop",
                  "modelName": "deterministic-fallback-zeta",
                  "temperature": 0.0,
                  "maxTokens": 1,
                  "providerOptions": {},
                  "enabled": true
                }
                """;

        mockMvc.perform(post("/model-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("managed-noop-zeta"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.source").value("MANAGED"));

        mockMvc.perform(put("/model-profiles/{modelId}", "managed-noop-zeta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Managed Noop Zeta Updated",
                                  "provider": "noop",
                                  "modelName": "deterministic-fallback-zeta-v2",
                                  "temperature": 0.0,
                                  "maxTokens": 1,
                                  "providerOptions": {},
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Managed Noop Zeta Updated"))
                .andExpect(jsonPath("$.modelName").value("deterministic-fallback-zeta-v2"));

        mockMvc.perform(delete("/model-profiles/{modelId}", "managed-noop-zeta"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/model-profiles/{modelId}", "minimax-m2.7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Static model profile is read-only: minimax-m2.7"));
    }

    @Test
    void modelProfileCreationShouldRejectBaseUrlThatAlreadyPointsToChatCompletionsEndpoint() throws Exception {
        mockMvc.perform(post("/model-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelId": "managed-openai-invalid-endpoint",
                                  "displayName": "Managed Invalid Endpoint",
                                  "provider": "openai",
                                  "modelName": "openai/gpt-5.4",
                                  "temperature": 0.2,
                                  "maxTokens": 320,
                                  "providerOptions": {
                                    "baseUrl": "https://openrouter.ai/api/v1/chat/completions"
                                  },
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("providerOptions.baseUrl must be the API root, not the /chat/completions endpoint"));
    }

    @Test
    void runShouldResolveRoleBindingSelectionFromModelCatalog() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(88L);
        request.setPlayers(playersWithSingleLlmSeat());
        String modelId = createManagedNoopProfile("managed-noop-role-binding", "deterministic-fallback-role-binding");
        request.setLlmSelection(roleBindingSelection(modelId));

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(responseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        List<AuditRecord> audits = auditRecordStore.findByGameId(gameId);
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).inputContextJson()).contains("\"modelId\":\"" + modelId + "\"");
    }

    @Test
    void runShouldResolveRandomPoolSelectionFromModelCatalog() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(66L);
        request.setPlayers(playersWithSingleLlmSeat());

        String modelId = createManagedNoopProfile("managed-noop-random-pool", "deterministic-fallback-random-pool");
        CreateGameRequest.LlmSelectionRequest llmSelection = new CreateGameRequest.LlmSelectionRequest();
        llmSelection.setMode("RANDOM_POOL");
        llmSelection.setCandidateModelIds(List.of(modelId));
        request.setLlmSelection(llmSelection);

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(responseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));

        List<AuditRecord> audits = auditRecordStore.findByGameId(gameId);
        assertThat(audits).isNotEmpty();
        assertThat(audits.get(0).inputContextJson()).contains("\"modelId\":\"" + modelId + "\"");
    }

    @Test
    void stateShouldRecoverFromPersistenceAfterSessionEviction() throws Exception {
        JsonNode createResponse = performCreateGame();
        String gameId = createResponse.get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        gameSessionService.evict(gameId);

        mockMvc.perform(get("/games/{gameId}/state", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.phase").value("DISCUSSION"))
                .andExpect(jsonPath("$.roundNo").value(1));

        mockMvc.perform(post("/games/{gameId}/run", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.status").value("ENDED"));
    }

    private JsonNode performCreateGame() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(42L);
        request.setPlayers(players());

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody);
    }

    private JsonNode performPlayerView(String gameId, String playerId) throws Exception {
        String responseBody = mockMvc.perform(get("/games/{gameId}/players/{playerId}/view", gameId, playerId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody);
    }

    private List<String> jsonTextList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
    }

    private List<String> jsonTextList(JsonNode arrayNode, String fieldName) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.path(fieldName).asText()));
        return values;
    }

    private JsonNode openAiProfile(JsonNode profiles) {
        for (JsonNode profile : profiles) {
            if ("openai".equals(profile.path("provider").asText())
                    && "OpenAI GPT-5.4".equals(profile.path("displayName").asText())) {
                return profile;
            }
        }
        throw new IllegalStateException("Missing static OpenAI GPT-5.4 model profile");
    }

    private List<CreateGameRequest.PlayerSlotRequest> players() {
        List<CreateGameRequest.PlayerSlotRequest> players = new ArrayList<>();
        for (int seatNo = 1; seatNo <= 5; seatNo++) {
            CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
            player.setSeatNo(seatNo);
            player.setDisplayName("P" + seatNo);
            player.setControllerType("SCRIPTED");
            players.add(player);
        }
        return players;
    }

    private List<CreateGameRequest.PlayerSlotRequest> playersWithSingleLlmSeat() {
        List<CreateGameRequest.PlayerSlotRequest> players = players();
        players.get(0).setControllerType("LLM");
        players.get(0).setAgentConfig(new com.example.avalon.agent.model.PlayerAgentConfig());
        return players;
    }

    private String createManagedNoopProfile(String modelId, String modelName) throws Exception {
        mockMvc.perform(post("/model-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelId": "%s",
                                  "displayName": "%s",
                                  "provider": "noop",
                                  "modelName": "%s",
                                  "temperature": 0.0,
                                  "maxTokens": 1,
                                  "providerOptions": {},
                                  "enabled": true
                                }
                                """.formatted(modelId, modelId, modelName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value(modelId));
        return modelId;
    }

    private CreateGameRequest.LlmSelectionRequest roleBindingSelection(String modelId) {
        CreateGameRequest.LlmSelectionRequest llmSelection = new CreateGameRequest.LlmSelectionRequest();
        llmSelection.setMode("ROLE_BINDING");
        llmSelection.getRoleBindings().put("MERLIN", modelId);
        llmSelection.getRoleBindings().put("PERCIVAL", modelId);
        llmSelection.getRoleBindings().put("LOYAL_SERVANT", modelId);
        llmSelection.getRoleBindings().put("MORGANA", modelId);
        llmSelection.getRoleBindings().put("ASSASSIN", modelId);
        return llmSelection;
    }
}

