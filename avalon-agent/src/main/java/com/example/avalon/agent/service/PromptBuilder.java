package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import com.example.avalon.agent.model.AgentTurnRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {
    public String build(AgentTurnRequest request) {
        StringBuilder builder = new StringBuilder("""
                你正在扮演一名阿瓦隆玩家。
                请严格代入当前玩家身份，只依据你可见的信息行动。
                游戏ID：%s
                轮次：%s
                阶段：%s
                玩家：%s（座位 %s，身份 %s）
                可执行动作：%s
                规则摘要：%s
                私有情报：%s
                记忆：%s
                公开局面：%s
                输出 schema 版本：%s
                只返回一个 JSON 对象，不要输出 Markdown、代码块、<think>、项目符号或任何解释文字。
                最终输出的第一个字符必须是 {，最后一个字符必须是 }。
                优先返回最小合法 JSON，并把第一层键按 action、publicSpeech、privateThought、auditReason、memoryUpdate 的顺序写出。
                action 必填，且必须是当前阶段合法的结构化动作 JSON。
                publicSpeech 只有在当前阶段需要公开发言时才提供；如果提供，只写 1 到 2 句简短中文。
                privateThought 可以省略或写 null；如果提供，只写一句极短中文，不要长篇分析。
                auditReason 和 memoryUpdate 默认省略；只有在确有必要时才提供。
                如果提供 auditReason，它必须是 JSON 对象，字段只允许 goal、reasonSummary、confidence、beliefs。
                如果提供 memoryUpdate，它必须是 JSON 对象，字段只允许 suspicionDelta、trustDelta、observationsToAdd、commitmentsToAdd、inferredFactsToAdd、strategyMode、lastSummary。
                """.formatted(
                request.getGameId(),
                request.getRoundNo(),
                request.getPhase(),
                request.getPlayerId(),
                request.getSeatNo(),
                request.getRoleId(),
                request.getAllowedActions(),
                request.getRulesSummary(),
                request.getPrivateKnowledge(),
                request.getMemory(),
                request.getPublicState(),
                request.getOutputSchemaVersion()
        ).strip());
        builder.append(System.lineSeparator())
                .append("最小合法示例：")
                .append(System.lineSeparator())
                .append(exampleJson(request.getAllowedActions()))
                .append(System.lineSeparator())
                .append("如果确实需要提供 memoryUpdate，最小合法示例：")
                .append(System.lineSeparator())
                .append("{\"action\":{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"我先给出公开看法。\"},\"publicSpeech\":\"我先给出公开看法。\",\"memoryUpdate\":{\"observationsToAdd\":[\"记录一条新观察\"],\"strategyMode\":\"BALANCED\",\"lastSummary\":\"保持低风险验证。\"}}");
        if ("minimax".equals(OpenAiCompatibleSupport.providerId(request.getProvider()))) {
            builder.append(System.lineSeparator())
                    .append("""
                            兼容要求：
                            - 不要输出项目符号
                            - action.actionType 只能从当前 allowedActions 中选择
                            """.strip());
        }
        return builder.toString();
    }

    private String exampleJson(List<String> allowedActions) {
        String primaryAction = allowedActions == null || allowedActions.isEmpty()
                ? "PUBLIC_SPEECH"
                : allowedActions.get(0);
        return switch (primaryAction) {
            case "TEAM_PROPOSAL" -> """
                    {"action":{"actionType":"TEAM_PROPOSAL","selectedPlayerIds":["P1","P2"]},"publicSpeech":"我先提一个可验证的队伍。","privateThought":"先做一轮低风险验证。"}
                    """.strip();
            case "TEAM_VOTE" -> """
                    {"action":{"actionType":"TEAM_VOTE","vote":"APPROVE"},"publicSpeech":"我暂时支持这支队伍。","privateThought":"先看这一轮投票。"}
                    """.strip();
            case "MISSION_ACTION" -> """
                    {"action":{"actionType":"MISSION_ACTION","choice":"SUCCESS"},"privateThought":"先执行当前任务动作。"}
                    """.strip();
            case "ASSASSINATION" -> """
                    {"action":{"actionType":"ASSASSINATION","targetPlayerId":"P1"},"publicSpeech":"我现在给出刺杀目标。","privateThought":"优先锁定最像梅林的玩家。"}
                    """.strip();
            default -> """
                    {"action":{"actionType":"PUBLIC_SPEECH","speechText":"我先给出公开看法。"},"publicSpeech":"我先给出公开看法。","privateThought":"先收集一轮信息。"}
                    """.strip();
        };
    }
}
