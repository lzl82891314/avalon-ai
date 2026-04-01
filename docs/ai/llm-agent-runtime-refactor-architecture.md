# Avalon LLM Agent 化重构架构书

## 1. 背景

当前 Avalon 的 LLM 决策链路本质上是单次 completion：

`TurnContextBuilder -> AgentTurnRequestFactory -> PromptBuilder -> AgentGateway -> ResponseParser -> GameOrchestrator`

这种模式适合问答或单轮结构化输出，但不适合阿瓦隆这种：

- 多回合重复博弈
- 需要身份隐藏和角色扮演
- 需要长期记忆与动态信念更新
- 需要在行动前做对手反应预测和反制

因此，本次重构的目标不是继续堆 prompt，而是把 LLM 调用层升级为真正的回合级 Agent 编排层。

## 2. 设计目标

### 2.1 核心目标

- 把“模型推理”与“回合编排”分离
- 把 Agent 策略模式抽象成可插拔策略接口
- 保留现有单次推理链路作为 `legacy-single-shot` 基线
- 为 ToM、ToT、Critic 等高级策略预留统一挂载点
- 扩展审计模型，使策略执行轨迹可回放、可对比、可评测
- 提供最小离线实验框架的数据结构与指标汇总能力

### 2.2 非目标

- 本期不做完整 champion/candidate 晋升平台
- 本期不做自动化策略发布和自动回滚
- 本期不引入 RL、微调或外部训练流水线

## 3. 分层架构

### 3.1 模型调用层

模型调用层只负责 provider 路由和单次推理，不负责博弈策略。

- `ModelGateway`
  - 新的一次推理抽象
  - 当前由现有 `AgentGateway` 兼容实现
- `RoutingAgentGateway`
  - 按 provider 路由到 noop 或 OpenAI-compatible gateway
- `OpenAiChatCompletionsGateway`
  - 单次结构化 completion 适配器

### 3.2 回合编排层

回合编排层负责“一个玩家在一回合里如何完成决策”。

- `TurnAgent`
  - 回合级统一入口
- `DefaultTurnAgent`
  - 根据 `PlayerAgentConfig.agentPolicyId` 选择策略
- `DeliberationPolicy`
  - 可插拔策略接口
- `DeliberationPolicyRegistry`
  - 策略注册表

当前已落地策略：

- `legacy-single-shot`
  - 由 `LegacySingleShotDeliberationPolicy` 实现
  - 把旧链路包装成新的策略实现

预留策略 ID：

- `tom-v1`
- `tom-tot-v1`
- `tom-tot-critic-v1`

## 4. 关键数据模型

### 4.1 PlayerAgentConfig

新增/激活字段：

- `agentPolicyId`
  - 指定当前回合编排策略
  - 默认回退到 `legacy-single-shot`
- `strategyProfileId`
  - 正式承载策略版本 ID
- `promptProfileId`
  - 保留兼容别名，旧配置仍可使用
- `modelSlots`
  - 支持多模型槽位，例如 `actor`、`critic`、`simulator`
- `modelProfile`
  - 保留兼容字段，默认作为 `actor` 槽位回退

### 4.2 AgentTurnRequest

新增字段：

- `agentPolicyId`
- `strategyProfileId`
- `modelSlotId`

用途：

- 进入 audit 的输入上下文
- 进入 trace 的阶段标识
- 便于后续多模型策略按槽位复用同一请求结构

### 4.3 TurnAgentResult

`TurnAgentResult` 是回合编排层的统一输出，包含：

- 最终使用的 `AgentTurnRequest`
- 模型返回的 `AgentTurnResult`
- 解析后的 `PlayerAction`
- 实际尝试次数
- `policyId`
- `strategyProfileId`
- `executionTrace`
- `policySummary`

这让控制器不再只知道“模型回了什么”，还能知道“策略是如何得出该动作的”。

## 5. 决策数据流

### 5.1 旧路径

`LlmPlayerController` 直接依赖 provider 调用链，控制器本身隐式承担了回合编排职责。

### 5.2 新路径

新路径改为：

`PlayerTurnContext -> TurnAgent -> DeliberationPolicy -> ModelGateway -> TurnAgentResult -> LlmPlayerController -> PlayerActionResult`

具体流程：

1. `LlmPlayerController` 只负责调用 `TurnAgent`
2. `TurnAgent` 根据 `agentPolicyId` 选择 `DeliberationPolicy`
3. `DeliberationPolicy` 组装请求并调用 `ModelGateway`
4. 策略输出 `TurnAgentResult`
5. `LlmPlayerController` 再转换为领域侧 `PlayerActionResult`

这样后续要增加多阶段策略时，不需要再改控制器或 runtime 主循环。

## 6. 审计与回放

### 6.1 新增审计字段

`audit_record` 扩展了两列：

- `execution_trace_json`
- `policy_summary_json`

对应 API 也扩展了：

- `GameAuditEntryResponse.executionTraceJson`
- `GameAuditEntryResponse.policySummaryJson`

### 6.2 trace 原则

trace 只保存结构化执行摘要，不保存原始 CoT：

- 阶段 ID
- 使用的 policy / model slot
- provider / model
- 模型调用次数
- token 统计
- 结构化 attributes

这满足：

- 回放可读
- 评测可统计
- 不落原始私有思维链

## 7. 运行态记忆闭环

本次同步补上了之前缺失的关键闭环：

- `GameOrchestrator.recordAction(...)` 现在会正式消费 `PlayerActionResult.memoryUpdate`
- 接受动作后：
  - 读取当前 `PlayerMemoryState`
  - 调用 `merge(update, now)`
  - 写回 `state.memoryByPlayerId`

这意味着：

- 记忆更新不再只是审计附件
- 下一回合 `TurnContextBuilder` 能真正读到更新后的记忆
- belief/stateful agent 的最小闭环已经成立

## 8. 最小离线实验框架

本期不做完整赛事调度平台，但补齐了最小实验数据模型：

- `PolicyExperimentRun`
- `PolicyExperimentGameResult`
- `PolicyExperimentPolicySummary`
- `PolicyExperimentSummary`
- `PolicyExperimentService`

当前能力：

- 接收一组对局结果
- 按 policy 聚合指标
- 输出可比较的 summary

当前指标：

- `winRate`
- `pauseRate`
- `illegalOutputRate`
- `avgModelCalls`
- `avgInputTokens`
- `avgOutputTokens`
- `avgFirstRoundRiskScore`
- `avgPostFailureRecoveryScore`
- `avgAssassinHitScore`

这为后续真正实现：

- 固定 seed suite
- 座位轮换
- baseline vs candidate 对照实验

提供了稳定的数据结构。

## 9. 兼容性策略

### 9.1 向后兼容

本次重构默认策略仍是：

- `legacy-single-shot`

因此现有：

- API 行为
- provider 路由
- noop fallback
- structured output retry
- replay / recovery

都保持兼容。

### 9.2 渐进切换

后续引入高级策略时，建议顺序为：

1. `tom-v1`
2. `tom-tot-v1`
3. `tom-tot-critic-v1`

并始终与 `legacy-single-shot` 做离线对照，未跑出稳定收益前不切默认。

## 10. 已实现清单

本次代码已完成：

- `TurnAgent / DeliberationPolicy / ModelGateway` 分层
- 旧单次推理链路封装为 `LegacySingleShotDeliberationPolicy`
- `PlayerAgentConfig` 支持 `agentPolicyId / strategyProfileId / modelSlots`
- audit 持久化新增 `executionTraceJson / policySummaryJson`
- `memoryUpdate` 正式写回运行态
- 最小实验汇总骨架与单测

## 11. 后续实现建议

下一阶段建议直接基于当前抽象实现真正的高级策略，而不是再改底层边界：

### 11.1 ToM

- 增加显式 belief stage
- 产出一阶/二阶/三阶信念对象
- 写入 `beliefState` / `metaBeliefs`

### 11.2 ToT

- 增加 candidate action generation stage
- 至少产出 3 个候选动作
- 为每个候选动作模拟队友/对手反应

### 11.3 Critic

- 为 `critic` 槽位引入独立模型
- 让 critic 只做反驳，不直接给最终动作
- finalizer 再综合 actor + critic 输出

## 12. 验收标准

可以认为这一轮重构达标的标准是：

- LLM 控制器已不再直接依赖 provider 层做整轮决策
- 策略模式可以通过配置切换
- 审计能明确追溯到 `policyId`、`strategyProfileId`、`executionTrace`
- 记忆更新能进入运行态并影响后续回合
- 离线实验已具备稳定的数据结构和指标汇总能力
