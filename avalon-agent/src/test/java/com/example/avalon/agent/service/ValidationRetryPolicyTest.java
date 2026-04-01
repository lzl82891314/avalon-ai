package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleMessageAnalysis;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationRetryPolicyTest {
    @Test
    void shouldNotRetryTransportFailuresAtValidationLayer() {
        ValidationRetryPolicy policy = new ValidationRetryPolicy();
        RecordingGateway gateway = RecordingGateway.alwaysThrow(transportFailure());
        RecordingResponseParser parser = new RecordingResponseParser();

        AgentTurnExecutionException error = assertInstanceOf(
                AgentTurnExecutionException.class,
                assertThrows(RuntimeException.class, () -> policy.execute(context(), request(), gateway, parser))
        );

        assertEquals(1, error.attempts());
        assertEquals(1, gateway.requests().size());
        assertEquals(0, parser.invocations());
    }

    @Test
    void shouldRetryStructuredFailuresAtValidationLayer() {
        ValidationRetryPolicy policy = new ValidationRetryPolicy();
        RecordingGateway gateway = new RecordingGateway(List.of(structuredFailure(), validResult()));
        RecordingResponseParser parser = new RecordingResponseParser();

        policy.execute(context(), request(), gateway, parser);

        assertEquals(2, gateway.requests().size());
        assertEquals(1, parser.invocations());
        AgentTurnRequest secondAttempt = gateway.requests().get(1);
        assertTrue(secondAttempt.getPromptText().contains("最小合法 JSON"));
        assertTrue(secondAttempt.getMaxTokens() >= 640);
    }

    private AgentTurnRequest request() {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setPromptText("原始提示");
        request.setAllowedActions(List.of("TEAM_VOTE"));
        request.setOutputSchemaVersion("v1");
        request.setProvider("glm");
        request.setModelName("glm-5");
        request.setMaxTokens(320);
        return request;
    }

    private PlayerTurnContext context() {
        PlayerPrivateView privateView = new PlayerPrivateView(
                "game-1",
                "P1",
                1,
                "LOYAL_SERVANT",
                Camp.GOOD,
                new PlayerPrivateKnowledge(List.of(), List.of()),
                List.of()
        );
        PlayerMemoryState memoryState = PlayerMemoryState.empty("game-1", "P1", "LOYAL_SERVANT", Camp.GOOD, Instant.now());
        return new PlayerTurnContext(
                "game-1",
                1,
                "TEAM_VOTE",
                "P1",
                1,
                "LOYAL_SERVANT",
                new PublicGameSnapshot("game-1", null, null, 1, 0, 0, 0, 1, List.of(), null, null, List.of(), Instant.now()),
                privateView,
                memoryState,
                new AllowedActionSet("game-1", "P1", 1, EnumSet.of(PlayerActionType.TEAM_VOTE)),
                null,
                null,
                "规则摘要"
        );
    }

    private RuntimeException transportFailure() {
        return new OpenAiCompatibleResponseException(
                "OpenAI-compatible HTTP transport failed after 3/3 attempts (java.net.http.HttpTimeoutException: request timed out)",
                null,
                "glm",
                "glm-5",
                null,
                null,
                Map.of(
                        "failureDomain", "transport",
                        "failureKind", "timeout",
                        "transportAttempts", 3,
                        "timeoutMs", 60000L,
                        "rootExceptionClass", "java.net.http.HttpTimeoutException",
                        "rootExceptionMessage", "request timed out"
                )
        );
    }

    private RuntimeException structuredFailure() {
        return new OpenAiCompatibleResponseException(
                "OpenAI-compatible assistant content looked like truncated JSON (shape=truncated_json_candidate, bodyPreview={) [finishReason=length]",
                null,
                "glm",
                "glm-5",
                "length",
                new OpenAiCompatibleMessageAnalysis(
                        true,
                        false,
                        "truncated_json_candidate",
                        "{",
                        null,
                        null
                )
        );
    }

    private AgentTurnResult validResult() {
        AgentTurnResult result = new AgentTurnResult();
        result.setActionJson("{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE\"}");
        result.setPrivateThought("先过一轮");
        return result;
    }

    private static final class RecordingGateway implements AgentGateway {
        private final List<Object> scriptedResults;
        private final List<AgentTurnRequest> requests = new ArrayList<>();

        private RecordingGateway(List<Object> scriptedResults) {
            this.scriptedResults = new ArrayList<>(scriptedResults);
        }

        private static RecordingGateway alwaysThrow(RuntimeException exception) {
            return new RecordingGateway(List.of(exception));
        }

        @Override
        public AgentTurnResult playTurn(AgentTurnRequest request) {
            requests.add(request.copy());
            Object scripted = scriptedResults.get(Math.min(requests.size() - 1, scriptedResults.size() - 1));
            if (scripted instanceof RuntimeException exception) {
                throw exception;
            }
            return (AgentTurnResult) scripted;
        }

        private List<AgentTurnRequest> requests() {
            return requests;
        }
    }

    private static final class RecordingResponseParser extends ResponseParser {
        private int invocations;

        @Override
        public PlayerAction parse(PlayerTurnContext context, AgentTurnResult turnResult) {
            invocations++;
            return new TeamVoteAction(VoteChoice.APPROVE);
        }

        private int invocations() {
            return invocations;
        }
    }
}
