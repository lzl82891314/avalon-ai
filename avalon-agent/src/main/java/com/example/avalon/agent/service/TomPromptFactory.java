package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.TomBeliefStageResult;
import com.example.avalon.agent.model.TomCriticStageResult;
import com.example.avalon.agent.model.TomTotStageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TomPromptFactory {
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public TomPromptFactory(PromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    public String buildBeliefDeveloperPrompt(AgentTurnRequest request) {
        return """
                policyId=%s
                stageId=belief-stage
                你正在执行阿瓦隆玩家的 ToM belief stage。
                这一阶段只做结构化信念建模，不直接输出最终动作。
                只返回一个 JSON 对象，不要输出 Markdown、代码块、<think> 或解释文字。
                第一层键按 beliefsByPlayerId、strategyMode、lastSummary、observationsToAdd、inferredFactsToAdd 的顺序输出。
                beliefsByPlayerId 的 key 只能是其他玩家的 playerId，不能写自己。
                 每个 belief 对象只允许以下字段：
                 - firstOrderEvilScore
                 - secondOrderAwarenessScore
                 - thirdOrderManipulationRisk
                 - confidence
                 所有分数都必须在 0 到 1 之间。
                 所有分数最多保留 2 位小数。
                 lastSummary 只允许 1 句短句。
                 observationsToAdd 与 inferredFactsToAdd 最多各 1 条短句。
                 如果某个可选字段为空，优先直接省略而不是输出冗余空结构。
                 只能基于当前公开局面、私有知识与记忆推断，不能把候选身份写成确定事实。
                 角色额外约束：%s
                 """.formatted(
                policyId(request),
                roleDirective(request.getRoleId())
        ).strip();
    }

    public String buildBeliefUserPrompt(AgentTurnRequest request) {
        return """
                请先对其他玩家建立一阶、二阶、三阶信念，再给出本回合建议 strategyMode 与简短 summary。
                gameId=%s
                roundNo=%s
                phase=%s
                playerId=%s
                seatNo=%s
                roleId=%s
                allowedActions=%s
                analyzablePlayerIds=%s
                privateKnowledge=%s
                memory=%s
                publicState=%s
                """.formatted(
                request.getGameId(),
                request.getRoundNo(),
                request.getPhase(),
                request.getPlayerId(),
                request.getSeatNo(),
                request.getRoleId(),
                request.getAllowedActions(),
                analyzablePlayerIds(request),
                json(request.getPrivateKnowledge()),
                json(request.getMemory()),
                json(request.getPublicState())
        ).strip();
    }

    public String buildTotDeveloperPrompt(AgentTurnRequest request) {
        return """
                policyId=%s
                stageId=tot-stage
                你正在执行阿瓦隆玩家的 Tree of Thoughts stage。
                固定生成 3 个候选行动，并为每个候选行动估计桌面反应与收益。
                只返回一个 JSON 对象，不要输出 Markdown、代码块、<think> 或解释文字。
                第一层键按 candidates、selectedCandidateId、summary 的顺序输出。
                每个 candidate 对象只允许以下字段：
                - candidateId
                - actionDraft
                - actionPlanSummary
                - projectedPublicReaction
                - projectedVoteOutcome
                 - projectedMissionRisk
                 - expectedUtility
                 - keyRisks
                 expectedUtility 必须在 0 到 1 之间。
                 actionDraft 只保留最小动作字段。
                 actionPlanSummary、projectedPublicReaction、projectedVoteOutcome、projectedMissionRisk 都压缩成短语或 1 句短句。
                 每个 candidate 的 keyRisks 最多 1 条。
                 selectedCandidateId 必须指向其中一个 candidateId。
                 summary 只允许 1 句短句。
                 """.formatted(policyId(request)).strip();
    }

    public String buildTotUserPrompt(AgentTurnRequest request, TomBeliefStageResult beliefStageResult) {
        return """
                请基于 belief stage 的结论，固定生成 3 个候选行动，分别模拟其他玩家的反应，再选出本回合最优候选。
                policyId=%s
                gameId=%s
                roundNo=%s
                phase=%s
                playerId=%s
                allowedActions=%s
                roleId=%s
                tomBeliefStage=%s
                privateKnowledge=%s
                memory=%s
                publicState=%s
                """.formatted(
                policyId(request),
                request.getGameId(),
                request.getRoundNo(),
                request.getPhase(),
                request.getPlayerId(),
                request.getAllowedActions(),
                request.getRoleId(),
                json(beliefPayload(beliefStageResult)),
                json(request.getPrivateKnowledge()),
                json(request.getMemory()),
                json(request.getPublicState())
        ).strip();
    }

    public String buildCriticDeveloperPrompt(AgentTurnRequest request) {
        return """
                policyId=%s
                stageId=critic-stage
                你正在执行阿瓦隆玩家的 Critic stage。
                 你的职责是从对立视角反驳已选候选行动，指出风险与反信号，但不直接输出最终动作。
                 只返回一个 JSON 对象，不要输出 Markdown、代码块、<think> 或解释文字。
                 第一层键按 status、riskFindings、counterSignals、recommendedAdjustments、summary 的顺序输出。
                 status 只能表达批判结论，例如 SUPPORT、MIXED、CHALLENGE。
                 riskFindings、counterSignals、recommendedAdjustments 各最多 2 条短句。
                 summary 只允许 1 句短句。
                 """.formatted(policyId(request)).strip();
    }

    public String buildCriticUserPrompt(AgentTurnRequest request,
                                        TomBeliefStageResult beliefStageResult,
                                        TomTotStageResult totStageResult) {
        return """
                请站在对立怀疑者视角审视当前已选候选行动，重点找出会误导判断、暴露身份或导致任务失败的风险。
                policyId=%s
                gameId=%s
                roundNo=%s
                phase=%s
                playerId=%s
                roleId=%s
                allowedActions=%s
                tomBeliefStage=%s
                tomTotStage=%s
                privateKnowledge=%s
                memory=%s
                publicState=%s
                """.formatted(
                policyId(request),
                request.getGameId(),
                request.getRoundNo(),
                request.getPhase(),
                request.getPlayerId(),
                request.getRoleId(),
                request.getAllowedActions(),
                json(beliefPayload(beliefStageResult)),
                json(totPayload(totStageResult)),
                json(request.getPrivateKnowledge()),
                json(request.getMemory()),
                json(request.getPublicState())
        ).strip();
    }

    public String buildDecisionPrompt(AgentTurnRequest request, TomBeliefStageResult beliefStageResult) {
        return buildDecisionPrompt(request, beliefStageResult, null, null);
    }

    public String buildDecisionPrompt(AgentTurnRequest request,
                                      TomBeliefStageResult beliefStageResult,
                                      TomTotStageResult totStageResult) {
        return buildDecisionPrompt(request, beliefStageResult, totStageResult, null);
    }

    public String buildDecisionPrompt(AgentTurnRequest request,
                                      TomBeliefStageResult beliefStageResult,
                                      TomTotStageResult totStageResult,
                                      TomCriticStageResult criticStageResult) {
        StringBuilder builder = new StringBuilder(promptBuilder.build(request));
        builder.append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("你当前处于 ")
                .append(policyId(request))
                .append(" 的最终决策阶段。")
                .append(System.lineSeparator())
                .append("你必须显式吸收下面这些中间推理产物，再生成最终动作。")
                .append(System.lineSeparator())
                .append("角色额外约束：")
                .append(roleDirective(request.getRoleId()))
                .append(System.lineSeparator())
                .append("tomBeliefStage=")
                .append(json(beliefPayload(beliefStageResult)))
                .append(System.lineSeparator());
        if (totStageResult != null) {
            builder.append("tomTotStage=")
                    .append(json(totPayload(totStageResult)))
                    .append(System.lineSeparator());
        }
        if (criticStageResult != null) {
            builder.append("tomCriticStage=")
                    .append(json(criticPayload(criticStageResult)))
                    .append(System.lineSeparator());
        }
        builder.append("上面的 beliefs、candidate 模拟和 critic 结论都属于工作假设，而非确定事实。")
                .append(System.lineSeparator())
                .append("最终只输出既有 action/publicSpeech/privateThought/auditReason/memoryUpdate schema，不要把 tomBeliefStage、tomTotStage、tomCriticStage 作为新的顶层字段输出。");
        return builder.toString();
    }

    private Map<String, Object> beliefPayload(TomBeliefStageResult beliefStageResult) {
        Map<String, Object> beliefPayload = new LinkedHashMap<>();
        beliefPayload.put("beliefsByPlayerId", beliefStageResult.getBeliefsByPlayerId());
        beliefPayload.put("strategyMode", beliefStageResult.getStrategyMode());
        beliefPayload.put("lastSummary", beliefStageResult.getLastSummary());
        beliefPayload.put("observationsToAdd", beliefStageResult.getObservationsToAdd());
        beliefPayload.put("inferredFactsToAdd", beliefStageResult.getInferredFactsToAdd());
        return beliefPayload;
    }

    private Map<String, Object> totPayload(TomTotStageResult totStageResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidates", totStageResult.getCandidates());
        payload.put("selectedCandidateId", totStageResult.getSelectedCandidateId());
        payload.put("summary", totStageResult.getSummary());
        return payload;
    }

    private Map<String, Object> criticPayload(TomCriticStageResult criticStageResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", criticStageResult.getStatus());
        payload.put("riskFindings", criticStageResult.getRiskFindings());
        payload.put("counterSignals", criticStageResult.getCounterSignals());
        payload.put("recommendedAdjustments", criticStageResult.getRecommendedAdjustments());
        payload.put("summary", criticStageResult.getSummary());
        return payload;
    }

    private List<String> analyzablePlayerIds(AgentTurnRequest request) {
        Object rawPlayers = request.getPublicState().get("players");
        if (!(rawPlayers instanceof Collection<?> players)) {
            return List.of();
        }
        List<String> playerIds = new ArrayList<>();
        for (Object rawPlayer : players) {
            if (!(rawPlayer instanceof Map<?, ?> player)) {
                continue;
            }
            Object rawPlayerId = player.get("playerId");
            if (rawPlayerId == null) {
                continue;
            }
            String playerId = String.valueOf(rawPlayerId);
            if (!playerId.isBlank() && !playerId.equals(request.getPlayerId())) {
                playerIds.add(playerId);
            }
        }
        return List.copyOf(playerIds);
    }

    private String roleDirective(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return "优先做低风险、可回退的信念建模。";
        }
        return switch (roleId) {
            case "MERLIN" -> "优先隐藏高价值身份，不要让公开行为和私下信念过度一致。";
            case "PERCIVAL" -> "优先区分梅林/莫甘娜候选，不要把双候选直接当成可信集合。";
            case "LOYAL_SERVANT" -> "优先依据公开一致性、任务结果和投票行为更新信念。";
            case "MORGANA" -> "优先伪装成可信好人，不要过度保护同伙。";
            case "ASSASSIN" -> "优先保留后续刺杀线索，同时避免提前暴露恶方协同。";
            default -> "优先做低风险、可回退的信念建模。";
        };
    }

    private String policyId(AgentTurnRequest request) {
        if (request.getAgentPolicyId() == null || request.getAgentPolicyId().isBlank()) {
            return AgentPolicyIds.TOM_V1;
        }
        return request.getAgentPolicyId();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize tom prompt context", exception);
        }
    }
}
