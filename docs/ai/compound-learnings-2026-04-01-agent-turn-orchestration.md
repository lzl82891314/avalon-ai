# 2026-04-01 Agent Turn Orchestration

## ce-brainstorm

- 阿瓦隆这种多回合博弈里，真正的架构分界不是“哪个 provider”，而是“单次推理”和“整回合决策”。
- 如果控制器直接依赖单次 completion，那么 ToM、ToT、Critic 之类的策略永远只能继续被塞进一个 prompt，无法做治理、审计和对比。
- 审计如果只记最终 action，而不记结构化策略轨迹，后续就无法比较不同编排模式到底强在哪里、贵在哪里。

## ce-plan

- 先把 provider 调用层和回合编排层拆开，新增 `TurnAgent`、`DeliberationPolicy`、`ModelGateway`。
- 先把旧链路包成 `legacy-single-shot` 基线策略，确保兼容，再扩审计和实验骨架。
- 同步补上 `memoryUpdate` 写回运行态，否则 belief 类策略没有闭环。

## ce-review

- 兼容性优先时，最稳的做法不是直接替换旧链路，而是把旧链路纳入新抽象作为默认策略。
- 新增策略轨迹字段时，不要只塞回 `rawModelResponseJson`；应给审计表独立列，否则后面统计会很痛苦。
- `PlayerAgentConfig` 里已有 `promptProfileId` 时，应把它视为策略版本兼容别名，而不是再发明一套平行字段并彻底割裂。

## ce-compound

- 当系统未来需要支持多种 agent 编排模式时，最关键的第一步不是实现某个高级策略，而是把“回合编排”提升成一等抽象，并让旧实现先迁入这个抽象作为基线。
- 在有 replay/audit 能力的系统里，新增治理层时应优先把 `policyId`、`strategyProfileId`、`executionTrace` 做成可查询、可统计的正式结构，而不是继续堆在自由 JSON 里。
- 对带记忆的 agent 系统来说，“模型会输出 memoryUpdate”不等于“系统具备学习闭环”；只有 update 被接受、合并、持久化并在下一回合重新注入，闭环才真正成立。
