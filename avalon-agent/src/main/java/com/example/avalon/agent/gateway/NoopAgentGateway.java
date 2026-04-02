package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NoopAgentGateway implements ModelGateway, StructuredModelGateway {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        AgentTurnResult result = new AgentTurnResult();
        result.setPublicSpeech(buildSpeech(request));
        result.setPrivateThought(buildPrivateThought(request));
        result.setActionJson(writeJson(actionPayload(request, result.getPublicSpeech())));
        result.setAuditReason(buildAuditReason(request));
        result.setMemoryUpdate(buildMemoryUpdate(request));
        result.setModelMetadata(buildMetadata(request));
        return result;
    }

    @Override
    public StructuredInferenceResult infer(StructuredInferenceRequest request) {
        String stageId = structuredStageId(request);
        JsonNode payload = objectMapper.valueToTree(structuredPayload(stageId));
        StructuredInferenceResult result = new StructuredInferenceResult();
        result.setPayload(payload);
        result.setRawJson(payload.toString());
        result.setModelMetadata(buildStructuredMetadata(request, stageId));
        return result;
    }

    private Map<String, Object> structuredPayload(String stageId) {
        if ("tot-stage".equals(stageId)) {
            Map<String, Object> candidate1 = new LinkedHashMap<>();
            candidate1.put("candidateId", "C1");
            candidate1.put("actionDraft", Map.of("mode", "stabilize"));
            candidate1.put("actionPlanSummary", "Prefer the lowest-risk legal action.");
            candidate1.put("projectedPublicReaction", "Most players see it as cautious.");
            candidate1.put("projectedVoteOutcome", "Likely to avoid immediate conflict.");
            candidate1.put("projectedMissionRisk", "Low short-term exposure.");
            candidate1.put("expectedUtility", 0.62);
            candidate1.put("keyRisks", List.of("May reveal too little new information."));

            Map<String, Object> candidate2 = new LinkedHashMap<>();
            candidate2.put("candidateId", "C2");
            candidate2.put("actionDraft", Map.of("mode", "pressure-test"));
            candidate2.put("actionPlanSummary", "Probe one suspicious player while preserving flexibility.");
            candidate2.put("projectedPublicReaction", "Creates debate but remains defensible.");
            candidate2.put("projectedVoteOutcome", "Mixed support from the table.");
            candidate2.put("projectedMissionRisk", "Medium information gain and medium exposure.");
            candidate2.put("expectedUtility", 0.78);
            candidate2.put("keyRisks", List.of("Can trigger counter-pressure."));

            Map<String, Object> candidate3 = new LinkedHashMap<>();
            candidate3.put("candidateId", "C3");
            candidate3.put("actionDraft", Map.of("mode", "aggressive"));
            candidate3.put("actionPlanSummary", "Force a sharper stance to test alignments.");
            candidate3.put("projectedPublicReaction", "High table friction.");
            candidate3.put("projectedVoteOutcome", "Higher rejection probability.");
            candidate3.put("projectedMissionRisk", "High exposure if read incorrectly.");
            candidate3.put("expectedUtility", 0.44);
            candidate3.put("keyRisks", List.of("Can expose hidden role intentions."));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("candidates", List.of(candidate1, candidate2, candidate3));
            payload.put("selectedCandidateId", "C2");
            payload.put("summary", "Candidate C2 balances pressure and concealment best.");
            return payload;
        }
        if ("critic-stage".equals(stageId)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "MIXED");
            payload.put("riskFindings", List.of(
                    "Selected candidate may create a readable pattern of suspicion.",
                    "Overcommitting too early can leak hidden role incentives."
            ));
            payload.put("counterSignals", List.of(
                    "The line is still explainable from a cautious good-player view."
            ));
            payload.put("recommendedAdjustments", List.of(
                    "Keep public speech short and avoid naming certainty.",
                    "Preserve flexibility in later rounds."
            ));
            payload.put("summary", "Challenge the line slightly, but it remains salvageable.");
            return payload;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("beliefsByPlayerId", Map.of());
        payload.put("strategyMode", "SAFE_DEFAULT");
        payload.put("lastSummary", "noop structured inference");
        payload.put("observationsToAdd", List.of("noop structured inference"));
        payload.put("inferredFactsToAdd", List.of());
        return payload;
    }

    private String structuredStageId(StructuredInferenceRequest request) {
        String combinedPrompt = (request.getDeveloperPrompt() == null ? "" : request.getDeveloperPrompt())
                + "\n"
                + (request.getUserPrompt() == null ? "" : request.getUserPrompt());
        String normalized = combinedPrompt.toLowerCase();
        if (normalized.contains("stageid=critic-stage")) {
            return "critic-stage";
        }
        if (normalized.contains("stageid=tot-stage")) {
            return "tot-stage";
        }
        return "belief-stage";
    }

    private Map<String, Object> actionPayload(AgentTurnRequest request, String speech) {
        List<String> allowedActions = request.getAllowedActions();
        if (allowedActions.isEmpty()) {
            throw new IllegalStateException("No allowed actions for request " + request.getPlayerId());
        }

        String actionType = allowedActions.get(0);
        return switch (actionType) {
            case "PUBLIC_SPEECH" -> Map.of(
                    "actionType", actionType,
                    "speechText", speech
            );
            case "TEAM_PROPOSAL" -> Map.of(
                    "actionType", actionType,
                    "selectedPlayerIds", buildProposal(request)
            );
            case "TEAM_VOTE" -> Map.of(
                    "actionType", actionType,
                    "vote", "APPROVE"
            );
            case "MISSION_ACTION" -> Map.of(
                    "actionType", actionType,
                    "choice", "EVIL".equals(String.valueOf(request.getPrivateKnowledge().get("camp"))) ? "FAIL" : "SUCCESS"
            );
            case "ASSASSINATION" -> Map.of(
                    "actionType", actionType,
                    "targetPlayerId", chooseAssassinationTarget(request)
            );
            default -> throw new IllegalStateException("Unsupported noop action type: " + actionType);
        };
    }

    private String buildSpeech(AgentTurnRequest request) {
        return "%s号位表示：第%s轮%s阶段按既定策略行动。".formatted(
                request.getSeatNo(),
                request.getRoundNo(),
                phaseLabel(request.getPhase())
        );
    }

    private String buildPrivateThought(AgentTurnRequest request) {
        return "我是%s号位，当前阶段是%s，可执行动作是%s。我会优先给出一个稳定且合法的默认动作。".formatted(
                request.getSeatNo(),
                phaseLabel(request.getPhase()),
                request.getAllowedActions().stream().map(this::actionLabel).toList()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> buildProposal(AgentTurnRequest request) {
        int teamSize = ((Number) request.getPublicState().getOrDefault("teamSize", 2)).intValue();
        List<Map<String, Object>> players = (List<Map<String, Object>>) request.getPublicState().getOrDefault("players", List.of());
        List<String> proposal = new ArrayList<>();
        proposal.add(request.getPlayerId());

        int currentSeat = request.getSeatNo();
        while (proposal.size() < teamSize) {
            currentSeat = seatAtOffset(players.size(), currentSeat, 1);
            String playerId = playerIdForSeat(players, currentSeat);
            if (playerId != null && !proposal.contains(playerId)) {
                proposal.add(playerId);
            }
        }
        return proposal;
    }

    private int seatAtOffset(int playerCount, int seatNo, int offset) {
        int index = (seatNo - 1 + offset) % playerCount;
        if (index < 0) {
            index += playerCount;
        }
        return index + 1;
    }

    private String playerIdForSeat(List<Map<String, Object>> players, int seatNo) {
        for (Map<String, Object> player : players) {
            if (((Number) player.getOrDefault("seatNo", 0)).intValue() == seatNo) {
                return String.valueOf(player.get("playerId"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String chooseAssassinationTarget(AgentTurnRequest request) {
        List<Map<String, Object>> visiblePlayers = (List<Map<String, Object>>) request.getPrivateKnowledge().getOrDefault("visiblePlayers", List.of());
        for (Map<String, Object> visiblePlayer : visiblePlayers) {
            if ("MERLIN".equals(String.valueOf(visiblePlayer.get("exactRoleId")))) {
                return String.valueOf(visiblePlayer.get("playerId"));
            }
        }
        for (Map<String, Object> visiblePlayer : visiblePlayers) {
            List<String> candidateRoleIds = (List<String>) visiblePlayer.getOrDefault("candidateRoleIds", List.of());
            if (candidateRoleIds.contains("MERLIN")) {
                return String.valueOf(visiblePlayer.get("playerId"));
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) request.getPublicState().getOrDefault("players", List.of());
        for (Map<String, Object> player : players) {
            String playerId = String.valueOf(player.get("playerId"));
            if (!request.getPlayerId().equals(playerId)) {
                return playerId;
            }
        }
        return request.getPlayerId();
    }

    private AuditReason buildAuditReason(AgentTurnRequest request) {
        AuditReason reason = new AuditReason();
        reason.setGoal("生成一个合法的" + actionLabel(request.getAllowedActions().get(0)) + "动作");
        reason.setReasonSummary(List.of(
                "遵循当前阶段允许的动作约束",
                "使用确定性的 noop 回退策略"
        ));
        reason.setConfidence(0.25);
        reason.setBeliefs(Map.of("mode", "noop-gateway", "language", "zh-CN"));
        return reason;
    }

    private MemoryUpdate buildMemoryUpdate(AgentTurnRequest request) {
        MemoryUpdate update = new MemoryUpdate();
        update.setObservationsToAdd(List.of("观察到阶段 " + phaseLabel(request.getPhase())));
        update.setStrategyMode("SAFE_DEFAULT");
        update.setLastSummary("noop 网关生成了确定性动作");
        return update;
    }

    private RawCompletionMetadata buildMetadata(AgentTurnRequest request) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider("noop");
        metadata.setModelName("deterministic-fallback");
        metadata.setInputTokens((long) request.getPromptText().length());
        metadata.setOutputTokens(32L);
        metadata.setAttributes(Map.of("schemaVersion", request.getOutputSchemaVersion()));
        return metadata;
    }

    private RawCompletionMetadata buildStructuredMetadata(StructuredInferenceRequest request, String stageId) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider("noop");
        metadata.setModelName("deterministic-fallback");
        long developerTokens = request.getDeveloperPrompt() == null ? 0L : request.getDeveloperPrompt().length();
        long userTokens = request.getUserPrompt() == null ? 0L : request.getUserPrompt().length();
        metadata.setInputTokens(developerTokens + userTokens);
        metadata.setOutputTokens(24L);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("modelSlotId", request.getModelSlotId());
        attributes.put("stageId", stageId);
        metadata.setAttributes(attributes);
        return metadata;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize deterministic noop action", e);
        }
    }

    private String phaseLabel(String phase) {
        if (phase == null) {
            return "未知";
        }
        return switch (phase) {
            case "DISCUSSION" -> "公开讨论";
            case "TEAM_PROPOSAL" -> "组队提案";
            case "TEAM_VOTE" -> "队伍投票";
            case "MISSION_ACTION" -> "任务执行";
            case "MISSION_RESOLUTION" -> "任务结算";
            case "ASSASSINATION" -> "刺杀";
            default -> phase;
        };
    }

    private String actionLabel(String actionType) {
        if (actionType == null) {
            return "动作";
        }
        return switch (actionType) {
            case "PUBLIC_SPEECH" -> "公开发言";
            case "TEAM_PROPOSAL" -> "组队提案";
            case "TEAM_VOTE" -> "队伍投票";
            case "MISSION_ACTION" -> "任务执行";
            case "ASSASSINATION" -> "刺杀";
            default -> actionType;
        };
    }
}
