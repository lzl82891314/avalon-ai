# Compound Learnings 2026-03-27

## ce-brainstorm

- MiniMax 这轮暴露出的不是 provider 路由问题，而是 structured output 契约问题：`content` 可能带 `<think>`、Markdown、说明文字，甚至直接为 `null`，而真实推理落在 `reasoning_details`。
- 只做 `"hi"` 连通性探测不够。排障必须把“网络可达”和“结构化 JSON 可用”拆开，否则很容易出现 key 正常、游戏首步仍然停局的假象。
- 不能为了继续游戏而静默回退到 `noop`。正确做法是有限容错加可观测性增强：能提取合法 JSON 就继续，提取不了就暂停并暴露 content 形态。

## ce-plan

- 共享 OpenAI-compatible 网关需要 provider 级默认请求策略，不能假设所有 provider 的请求形态一致。
- 对 reasoning-capable provider，prompt 和 developer/system instruction 都要收紧成“最终只允许一个 JSON 对象”，否则 `response_format = json_object` 仍然可能被长文本拖垮。
- assistant 响应分析应抽成复用对象，同时服务于运行时解析、probe 诊断和失败审计，避免三处各写一套判断逻辑。

## ce-review

- 这轮实现保持了事务边界和状态机边界不变：没有改变 `PAUSED` 语义，没有引入隐式 `noop` 回退，也没有破坏恢复后的幂等行为。
- 风险控制点在于：第二次重试不再是“原样再打一次”，而是仅对明显的结构化失败做纠偏重试，且仍然维持最多两次，避免把失败路径变成无界重试。
- 静态 profile 和 managed profile 都新增了 `baseUrl` 形态校验，能把“把完整 `/chat/completions` endpoint 当 baseUrl”这类错误尽量前移到配置阶段。

## ce-compound

- `response_format = json_object` 不能单独保证 structured output 稳定。如果 prompt 默认鼓励长 `privateThought`、`auditReason` 和 `memoryUpdate`，而 completion token budget 偏小，真实故障往往是“JSON 被截断”，而不是“模型完全没有输出 JSON”。
- OpenAI-compatible 配置层必须把 `baseUrl` 定义成 API root，而不是完整 `/chat/completions` endpoint。最佳实践是：配置写入时 fail fast，runtime gateway 同时做向后兼容，避免旧配置直接打断运行链路。
- LLM 重试不应该只是“原样再打一次”。对 `finishReason=length` 或疑似截断 JSON 的失败，第二次尝试应该同时提高 token budget、收紧输出契约、明确允许省略 `auditReason/memoryUpdate`。
- 审计和控制台诊断里应该把“疑似截断 JSON”作为一等内容形态暴露出来，并同步显示 `finishReason`。这样才能快速区分“解析器挑剔”和“模型被 token budget 截断”。
- 对 `action` 和 `auditReason/memoryUpdate` 这种可选附加字段，失败域必须分开。只要 `action` 已经合法，可选段的坏形状就应该降级成 warning，而不是把整轮动作判成失败并推进到 `PAUSED`。
- 如果某个 structured 字段在语义上允许“省略”或“丢弃”，对应 DTO 就不应该默认初始化成空对象。否则运行时、审计和控制台都会把“模型没给”与“模型给了空对象”混成一类，排障会失真。
