package com.example.avalon.app;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentPolicyIds;
import com.example.avalon.api.dto.CreateGameRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = AvalonApplication.class,
        properties = "avalon.agent.default-policy-id=tom-v1"
)
@AutoConfigureMockMvc
class GameApiDefaultPolicyIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldUseConfiguredDefaultPolicyWhenRequestDoesNotSpecifyPolicy() throws Exception {
        String gameId = createGame(playersWithSingleLlmSeat());

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/games/{gameId}/step", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        JsonNode auditEntries = auditEntries(gameId);
        assertThat(auditEntries.get(0).path("policySummaryJson").asText()).contains("\"policyId\":\"tom-v1\"");
        assertThat(auditEntries.get(0).path("policySummaryJson").asText()).contains("\"stageCount\":2");
    }

    @Test
    void shouldPreferExplicitRequestPolicyOverConfiguredDefaultPolicy() throws Exception {
        String gameId = createGame(playersWithSinglePolicyLlmSeat(AgentPolicyIds.TOM_TOT_V1, "tom-tot-v1-baseline"));

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/games/{gameId}/step", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        JsonNode auditEntries = auditEntries(gameId);
        assertThat(auditEntries.get(0).path("policySummaryJson").asText()).contains("\"policyId\":\"tom-tot-v1\"");
        assertThat(auditEntries.get(0).path("policySummaryJson").asText()).contains("\"stageCount\":3");
    }

    private String createGame(List<CreateGameRequest.PlayerSlotRequest> players) throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(120L);
        request.setPlayers(players);

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).get("gameId").asText();
    }

    private JsonNode auditEntries(String gameId) throws Exception {
        String auditResponseBody = mockMvc.perform(get("/games/{gameId}/audit", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(auditResponseBody);
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
        players.get(0).setAgentConfig(new PlayerAgentConfig());
        return players;
    }

    private List<CreateGameRequest.PlayerSlotRequest> playersWithSinglePolicyLlmSeat(String policyId, String strategyProfileId) {
        List<CreateGameRequest.PlayerSlotRequest> players = players();
        players.get(0).setControllerType("LLM");
        PlayerAgentConfig agentConfig = new PlayerAgentConfig();
        agentConfig.setAgentPolicyId(policyId);
        agentConfig.setStrategyProfileId(strategyProfileId);
        players.get(0).setAgentConfig(agentConfig);
        return players;
    }
}
