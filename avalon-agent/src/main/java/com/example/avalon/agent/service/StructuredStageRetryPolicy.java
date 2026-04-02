package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleResponseException;
import com.example.avalon.agent.gateway.StructuredModelGateway;
import com.example.avalon.agent.model.StructuredInferenceRequest;
import com.example.avalon.agent.model.StructuredInferenceResult;
import org.springframework.stereotype.Component;

@Component
public class StructuredStageRetryPolicy {
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final int MIN_CORRECTIVE_MAX_TOKENS = 960;

    public StructuredStageExecution execute(String stageId,
                                            StructuredInferenceRequest request,
                                            StructuredModelGateway structuredModelGateway) {
        RuntimeException lastFailure = null;
        StructuredInferenceRequest attemptRequest = request.copy();
        for (int attempts = 1; attempts <= DEFAULT_MAX_ATTEMPTS; attempts++) {
            try {
                StructuredInferenceResult result = structuredModelGateway.infer(attemptRequest);
                return new StructuredStageExecution(result, attempts, attemptRequest.copy());
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempts < DEFAULT_MAX_ATTEMPTS && shouldRetry(exception)) {
                    attemptRequest = nextAttemptRequest(stageId, attemptRequest);
                    continue;
                }
                throw new StructuredStageExecutionException(stageId, attemptRequest.copy(), attempts, exception);
            }
        }
        throw new StructuredStageExecutionException(stageId, attemptRequest.copy(), DEFAULT_MAX_ATTEMPTS, lastFailure);
    }

    private boolean shouldRetry(RuntimeException failure) {
        if (!(failure instanceof OpenAiCompatibleResponseException responseException)) {
            return false;
        }
        if (ResponseRetrySupport.isTransportFailure(responseException)) {
            return false;
        }
        return ResponseRetrySupport.requiresCompressionRetry(responseException);
    }

    private StructuredInferenceRequest nextAttemptRequest(String stageId, StructuredInferenceRequest request) {
        StructuredInferenceRequest next = request.copy();
        next.setDeveloperPrompt(appendPrompt(next.getDeveloperPrompt(), correctivePrompt(stageId)));
        next.setMaxTokens(ResponseRetrySupport.raisedMaxTokens(next.getMaxTokens(), MIN_CORRECTIVE_MAX_TOKENS));
        return next;
    }

    private String appendPrompt(String originalPrompt, String correctivePrompt) {
        if (originalPrompt == null || originalPrompt.isBlank()) {
            return correctivePrompt;
        }
        return originalPrompt + System.lineSeparator() + System.lineSeparator() + correctivePrompt;
    }

    private String correctivePrompt(String stageId) {
        return switch (stageId) {
            case "belief-stage" -> """
                    上一轮 belief-stage 输出被截断或未满足结构化要求。请重试，并严格压缩输出：
                    - 只输出一个 JSON 对象
                    - 只保留 beliefsByPlayerId、strategyMode、lastSummary、observationsToAdd、inferredFactsToAdd
                    - beliefsByPlayerId 只包含其他玩家；每个 belief 只保留四个分数字段，分数最多保留 2 位小数
                    - lastSummary 只写 1 句短句
                    - observationsToAdd 与 inferredFactsToAdd 最多各 1 条短句
                    - 能省略的字段就省略，不要输出解释、Markdown、代码块或 <think>
                    """.strip();
            case "tot-stage" -> """
                    上一轮 tot-stage 输出被截断或未满足结构化要求。请重试，并严格压缩输出：
                    - 只输出一个 JSON 对象
                    - 只保留 candidates、selectedCandidateId、summary
                    - 必须固定输出 3 个 candidates
                    - 每个 candidate 只保留 candidateId、actionDraft、actionPlanSummary、projectedPublicReaction、projectedVoteOutcome、projectedMissionRisk、expectedUtility、keyRisks
                    - actionDraft 只保留最小动作字段；自由文本字段都压缩成短语或 1 句短句
                    - 每个 candidate 的 keyRisks 最多 1 条；summary 只写 1 句短句
                    - 不要输出解释、Markdown、代码块或 <think>
                    """.strip();
            case "critic-stage" -> """
                    上一轮 critic-stage 输出被截断或未满足结构化要求。请重试，并严格压缩输出：
                    - 只输出一个 JSON 对象
                    - 只保留 status、riskFindings、counterSignals、recommendedAdjustments、summary
                    - status 必填
                    - riskFindings、counterSignals、recommendedAdjustments 各最多 2 条短句
                    - summary 只写 1 句短句
                    - 不要输出解释、Markdown、代码块或 <think>
                    """.strip();
            default -> """
                    上一轮结构化输出被截断或未满足要求。请重试，并只输出一个最小合法 JSON 对象，不要输出解释、Markdown、代码块或 <think>。
                    """.strip();
        };
    }

    public record StructuredStageExecution(StructuredInferenceResult result,
                                           int attempts,
                                           StructuredInferenceRequest request) {
    }

    static final class StructuredStageExecutionException extends RuntimeException {
        private final String stageId;
        private final StructuredInferenceRequest request;
        private final int attempts;

        StructuredStageExecutionException(String stageId,
                                          StructuredInferenceRequest request,
                                          int attempts,
                                          RuntimeException cause) {
            super("Structured stage failed: " + stageId + " after " + attempts + " attempts", cause);
            this.stageId = stageId;
            this.request = request;
            this.attempts = attempts;
        }

        String stageId() {
            return stageId;
        }

        StructuredInferenceRequest request() {
            return request;
        }

        int attempts() {
            return attempts;
        }

        RuntimeException failure() {
            return getCause() instanceof RuntimeException runtimeException ? runtimeException : this;
        }
    }
}
