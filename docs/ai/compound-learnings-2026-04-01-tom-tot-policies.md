# Compound Learnings - 2026-04-01 - ToT/Critic Policies

## 背景

本轮在既有 `TurnAgent -> DeliberationPolicy -> ModelGateway` 分层上，补齐了：

- `tom-tot-v1`
- `tom-tot-critic-v1`

同时补了对应的 `noop` 结构化返回、单元测试与应用集成测试。

## 可复用结论

### 1. 多阶段策略不要把状态写散在各策略类里

ToM、ToT、Critic 三类策略共享的真正稳定能力只有几类：

- belief payload 解析与清洗
- belief -> memory 合并
- belief memoryUpdate 与最终 decision memoryUpdate 合并
- stage trace / policy summary 组装
- model slot 去重统计

这些内容抽到包内支持类后，新增策略时只需要关心“阶段顺序”和“阶段间数据流”，不需要重复拷贝 `tom-v1` 的审计与记忆拼装逻辑。

### 2. 结构化阶段必须有显式的 stage contract

`StructuredModelGateway` 目前只接收 prompt，没有单独的 stage 字段。

因此需要在 prompt 中建立稳定约定：

- `policyId=...`
- `stageId=belief-stage|tot-stage|critic-stage`

这样做的收益：

- `noop` 网关可以稳定返回不同的结构化 payload
- 测试可以按 `stageId` 定向构造 mock 返回
- 后续如果接入别的 provider/gateway，也有统一的阶段识别锚点

结论：只要没有单独的 stage schema 字段，就必须保持 prompt marker 是稳定协议，而不是临时文案。

### 3. Critic 回退不能只改模型选择，还要改审计口径

`critic` 槽位缺失时，不能只是“内部偷偷用 actor”。

必须同步满足：

- critic stage 请求实际使用 `actor`
- `executionTrace[*].modelSlotId` 反映实际槽位
- `policySummary.modelSlotIds` 只统计实际使用过的槽位

否则 audit 会把一次单模型决策伪装成双模型决策，后续实验统计会失真。

### 4. belief 仍然是唯一允许写长期 memory 的中间阶段

ToT 和 Critic 都会产出有价值的中间推理，但当前阶段应继续保持：

- belief-stage 写 `beliefsToUpsert / observationsToAdd / inferredFactsToAdd`
- tot-stage / critic-stage 只进入 `executionTrace` 和 `policySummary`
- 最终 action 仍通过 decision stage 的既有 schema 返回

这样可以避免把候选行动模拟和对立辩驳直接污染长期记忆，保证 memory 仍然以相对稳定的信念层为主。

### 5. 新策略验证要分成两层

这轮验证证明分层测试是必要的：

- 单元测试验证编排语义
  - 阶段顺序
  - prompt 注入
  - summary/trace 字段
  - critic slot fallback
- 应用集成测试验证 wiring
  - Spring 自动注册策略
  - `noop` 结构化返回形状
  - audit 落库内容

如果只做单元测试，很容易漏掉 `noop` payload shape 与 audit 落库的真实接线问题。

## 后续建议

### 1. 如果继续加策略，优先抽象“阶段定义”

当前已经有共享支持层，但“阶段顺序”仍是策略类手写。
下一步如果继续增加混合策略，可以考虑抽象：

- stage descriptor
- stage executor
- stage result merger

前提是不要把当前清晰的策略代码抽成过度泛化框架。

### 2. 如果要做真实实验平台，先补 trace 解析器而不是先补更多策略

现在 `policySummary` 和 `executionTrace` 已足够描述：

- stage count
- model calls
- model slots
- candidate count
- selected candidate
- critic status

下一步更有价值的是把这些字段接入实验汇总，而不是继续堆新的 prompt 模式。
