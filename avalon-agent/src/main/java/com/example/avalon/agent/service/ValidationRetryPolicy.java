package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ValidationRetryPolicy {
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final int MIN_CORRECTIVE_MAX_TOKENS = 320;

    public ValidatedAgentTurn execute(PlayerTurnContext context,
                                      AgentTurnRequest request,
                                      AgentGateway agentGateway,
                                      ResponseParser responseParser) {
        RuntimeException lastFailure = null;
        AgentTurnResult lastResult = null;
        AgentTurnRequest attemptRequest = request.copy();
        for (int attempts = 1; attempts <= DEFAULT_MAX_ATTEMPTS; attempts++) {
            try {
                AgentTurnResult result = agentGateway.playTurn(attemptRequest);
                lastResult = result;
                PlayerAction action = responseParser.parse(context, result);
                return new ValidatedAgentTurn(result, action, attempts, attemptRequest.copy());
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempts < DEFAULT_MAX_ATTEMPTS) {
                    attemptRequest = nextAttemptRequest(attemptRequest, exception);
                }
            }
        }
        throw new AgentTurnExecutionException(
                "Agent turn validation failed after " + DEFAULT_MAX_ATTEMPTS + " attempts",
                attemptRequest,
                lastResult,
                DEFAULT_MAX_ATTEMPTS,
                lastFailure
        );
    }

    private AgentTurnRequest nextAttemptRequest(AgentTurnRequest request, RuntimeException failure) {
        AgentTurnRequest next = request.copy();
        String correctivePrompt = correctivePrompt(failure, next.getAllowedActions());
        if (correctivePrompt != null) {
            next.setPromptText(appendPrompt(next.getPromptText(), correctivePrompt));
            if (next.getMaxTokens() == null || next.getMaxTokens() < MIN_CORRECTIVE_MAX_TOKENS) {
                next.setMaxTokens(MIN_CORRECTIVE_MAX_TOKENS);
            }
        }
        return next;
    }

    private String appendPrompt(String originalPrompt, String correctivePrompt) {
        if (originalPrompt == null || originalPrompt.isBlank()) {
            return correctivePrompt;
        }
        return originalPrompt + System.lineSeparator() + System.lineSeparator() + correctivePrompt;
    }

    private String correctivePrompt(RuntimeException failure, List<String> allowedActions) {
        if (!(failure instanceof OpenAiCompatibleResponseException responseException)) {
            return null;
        }
        String finishReason = stringValue(responseException.diagnostics().get("finishReason"));
        String contentShape = stringValue(responseException.diagnostics().get("assistantContentShape"));
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if ("length".equalsIgnoreCase(finishReason) || "truncated_json_candidate".equals(contentShape)) {
            return """
                    上一次输出疑似被截断。请重试并压缩到最小合法 JSON：
                    - 最终回复只能是一个 JSON 对象，首字符必须是 {，尾字符必须是 }
                    - publicSpeech 只写 1 到 2 句简短中文
                    - privateThought 只写一句简短中文
                    - %s
                    - auditReason 和 memoryUpdate 可直接写 null 或省略
                    - 不要输出解释、Markdown、代码块或长篇分析
                    """.formatted(actionRequirement(allowedActions)).strip();
        }
        if ("plain_text".equals(contentShape)
                || "markdown_explanation".equals(contentShape)
                || message.contains("did not include an action object")) {
            return """
                    上一次输出没有满足结构化要求。请重试并严格按最小 JSON 返回：
                    - 只能输出一个 JSON 对象，不能补充解释文字
                    - 必须包含 action 对象
                    - publicSpeech 与 privateThought 都保持简短
                    - %s
                    - auditReason 和 memoryUpdate 可直接写 null 或省略
                    """.formatted(actionRequirement(allowedActions)).strip();
        }
        return null;
    }

    private String actionRequirement(List<String> allowedActions) {
        if (allowedActions == null || allowedActions.isEmpty()) {
            return "action.actionType 必须是当前允许的动作";
        }
        if (allowedActions.size() == 1) {
            return "action.actionType 必须严格等于 " + allowedActions.get(0);
        }
        return "action.actionType 只能从 " + allowedActions.stream()
                .map(action -> action.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }
}
