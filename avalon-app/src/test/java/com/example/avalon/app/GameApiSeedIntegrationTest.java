package com.example.avalon.app;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.service.GameSessionService;
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

@SpringBootTest(classes = AvalonApplication.class)
@AutoConfigureMockMvc
class GameApiSeedIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GameSessionService gameSessionService;

    @Test
    void createGameShouldGenerateSeedWhenSeedIsOmitted() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setPlayers(players());

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String gameId = objectMapper.readTree(responseBody).get("gameId").asText();
        GameRuntimeState state = gameSessionService.find(gameId).orElseThrow();
        assertThat(state.setup().seed()).isPositive();
    }

    @Test
    void startGameShouldPickLeaderFromExplicitSeed() throws Exception {
        CreateGameRequest request = new CreateGameRequest();
        request.setRuleSetId("avalon-classic-5p-v1");
        request.setSetupTemplateId("classic-5p-v1");
        request.setSeed(99L);
        request.setPlayers(players());

        String responseBody = mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String gameId = objectMapper.readTree(responseBody).get("gameId").asText();

        mockMvc.perform(post("/games/{gameId}/start", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(get("/games/{gameId}/state", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicState.leaderSeat").value(3));
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
}
