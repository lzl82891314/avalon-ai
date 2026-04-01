package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.TomBeliefStageResult;
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
                你正在执行阿瓦隆玩家的 tom-v1 信念建模阶段。
                这一阶段只做结构化 Theory of Mind 建模，不直接输出最终动作。
                只返回一个 JSON 对象，不要输出 Markdown、代码块、<think> 或解释文字。
                第一层键按 beliefsByPlayerId、strategyMode、lastSummary、observationsToAdd、inferredFactsToAdd 的顺序输出。
                beliefsByPlayerId 的 key 只能是其他玩家的 playerId，不能写自己。
                每个 belief 对象只允许以下字段：
                - firstOrderEvilScore：我认为该玩家是邪恶或高风险的程度
                - secondOrderAwarenessScore：我认为该玩家已经意识到他正在被怀疑或观察的程度
                - thirdOrderManipulationRisk：我认为该玩家会利用他人预期进行伪装或误导的风险
                - confidence：我对上述判断的把握
                所有分数都必须在 0 到 1 之间。
                只能基于当前公开局面、私有知识与记忆推断，不能把候选身份写成确定事实。
                角色额外约束：%s
                """.formatted(roleDirective(request.getRoleId())).strip();
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

    public String buildDecisionPrompt(AgentTurnRequest request, TomBeliefStageResult beliefStageResult) {
        Map<String, Object> beliefPayload = new LinkedHashMap<>();
        beliefPayload.put("beliefsByPlayerId", beliefStageResult.getBeliefsByPlayerId());
        beliefPayload.put("strategyMode", beliefStageResult.getStrategyMode());
        beliefPayload.put("lastSummary", beliefStageResult.getLastSummary());
        beliefPayload.put("observationsToAdd", beliefStageResult.getObservationsToAdd());
        beliefPayload.put("inferredFactsToAdd", beliefStageResult.getInferredFactsToAdd());
        StringBuilder builder = new StringBuilder(promptBuilder.build(request));
        builder.append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("你当前处于 tom-v1 的最终决策阶段。")
                .append(System.lineSeparator())
                .append("你必须显式吸收下面这份本回合 ToM 工作记忆，再生成最终动作。")
                .append(System.lineSeparator())
                .append("角色额外约束：")
                .append(roleDirective(request.getRoleId()))
                .append(System.lineSeparator())
                .append("tomBeliefStage=")
                .append(json(beliefPayload))
                .append(System.lineSeparator())
                .append("上面的 beliefs 是工作假设而非确定事实。")
                .append(System.lineSeparator())
                .append("最终只输出既有 action/publicSpeech/privateThought/auditReason/memoryUpdate schema，不要把 beliefsByPlayerId 作为新的顶层字段输出。");
        return builder.toString();
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize tom-v1 prompt context", exception);
        }
    }
}
