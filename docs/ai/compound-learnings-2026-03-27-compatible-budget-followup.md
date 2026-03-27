# Compound Learnings 2026-03-27 Compatible Budget Follow-up

## ce-brainstorm

- MiniMax 现场再次证明，`finishReason=length` 下的失败不只是“模型没按格式输出”，而是 completion budget 与输出契约共同失衡。
- 如果默认 profile 是 `320`，而重试下限仍然是 `320`，那第二次重试在 token budget 维度其实没有任何纠偏效果。

## ce-plan

- 兼容层的最小 JSON 契约应该明确成 action-first，而不是坚持完整字段固定顺序。
- 运行时预算下限应放在 shared compatible gateway，而不是只改某个静态模板值；否则 managed profile 和旧配置不会自动受益。

## ce-review

- 这次修复没有改变状态机、暂停语义或失败可见性，只增强了 compatible provider 的预算和重试策略。
- probe、运行时请求和失败重试三条链路现在共用同一套“最小合法 JSON + action-first + 高预算”方向，诊断和真实执行不再漂移。

## ce-compound

- 对 OpenAI-compatible reasoning provider，最有效的稳定性组合通常是：压缩输出契约、把 `action` 提前到 JSON 前半段、并把第二次重试预算提升到显著高于首轮的值。
- “提高 token budget”必须是实质性提高，而不是把已经在用的值再次写回；否则问题会被误判成 prompt 不够严格，而真实原因仍是预算没有变。
