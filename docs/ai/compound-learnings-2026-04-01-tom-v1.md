# 2026-04-01 Tom V1

## ce-brainstorm

- `tom-v1` 的首个落点不该一上来做多策略混编，而应该先把“两阶段回合决策”做成稳定骨架：`belief-stage -> decision-stage -> memory/audit writeback`。
- 对现有工程来说，真正危险的不是 prompt 细节，而是接线边界。如果结构化推理和普通 completion 共用同一个 Spring 注入入口，很容易被测试里的 `@MockBean AgentGateway` 意外替换掉。
- belief 如果只写入审计、不写入长期记忆，那么 ToM 只会退化成一次性中间草稿，下一回合依然没有推理积累。

## ce-plan

- 保留 `legacy-single-shot` 作为默认策略，把 `tom-v1` 增量挂到 `DeliberationPolicy` 上。
- 新增结构化推理入口 `StructuredModelGateway`，并把 provider 路由拆成普通 completion 路由与 structured inference 路由两个 bean。
- 补齐三层验证：
  - agent 单测验证两阶段 trace / summary / memory merge。
  - runtime 单测验证 belief memory 注入与回写。
  - app 集成测试验证 Spring 上下文和审计链路没有被新注入方式破坏。

## ce-review

- 对带 `@MockBean AgentGateway` 的 Spring 集成测试，最稳的做法不是强行让 mock 同时承担 structured 能力，而是把 structured routing 做成独立 bean。否则策略层一旦依赖 `StructuredModelGateway`，测试上下文就会因为 mock 替换主路由而失去唯一候选。
- `tom-v1` 的失败元数据必须在策略层完成封装，而不是把 belief-stage 失败留给控制器临时拼装。只有这样才能保证 `executionTrace`、`policySummary` 在所有失败路径上结构一致。
- 回合级高级策略如果会写 belief，runtime 必须同时保证两件事：
  - 下回合上下文能重新注入 `beliefsByPlayerId`
  - 持久化快照能完整带上这些字段

## ce-compound

- 当系统同时存在“普通 completion 调用”和“结构化中间推理调用”时，路由层最好按能力拆分，而不是让一个接口承载所有模式。这样更容易测试，也更不容易被 mock 或装配顺序误伤。
- Agent 策略升级时，优先验证的不是最终 action 对不对，而是中间治理元数据是否完整：
  - `policyId`
  - `strategyProfileId`
  - `executionTrace`
  - `policySummary`
  - `beliefsByPlayerId`
- 多回合博弈里的 ToM 策略只有在“belief 可持久化、可回放、可再次注入”时才算真正落地；否则它只是更长的 prompt，不是更强的 agent。
