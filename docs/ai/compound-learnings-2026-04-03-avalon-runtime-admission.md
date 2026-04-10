# Compound Learnings - 2026-04-03 - Avalon Runtime Admission And Compatibility

## 背景

本轮把多阶段 Agent 兼容层继续往前推进，重点落在三件事：

1. `providerOptions.avalonRuntime` 驱动的兼容预算和准入配置
2. 建局前 admission 校验
3. probe / retry / 默认绑定 对多 provider 的一致性

同时保留已有行为：未显式配置 `avalonRuntime` 的旧 profile 不能被这次改造整体打挂。

## 可复用结论

### 1. 显式配置优先，未配置必须保留默认回退

`avalonRuntime` 的设计目标是“提供更强的显式治理”，不是“让所有旧 profile 立刻失效”。

因此 admission 的正确边界是：

- profile 显式配置了 `providerOptions.avalonRuntime`
  这时严格按配置判断是否准入
- profile 没有显式配置 `providerOptions.avalonRuntime`
  这时走默认兼容路径，不在建局阶段直接拒绝

否则会把：

- 旧的 managed profile
- 测试中的 inline/noop profile
- 还没补 runtime 配置的历史 profile

全部在 create game 阶段打成 400，回归面太大。

### 2. admission 只应该约束“可识别 profile”，不要误伤内联 slot profile

`PlayerAgentConfig.modelProfile` / `modelSlots` 里可以直接内联一个模型配置，这类配置未必存在于 catalog。

如果 admission 逻辑无条件拿 `modelId` 去 `requireEnabledProfile(...)`，会把合法的 inline profile 误判成：

- `Unknown model profile`

更稳妥的做法是：

- catalog 里存在的 profile，按 catalog admission 规则校验
- catalog 里不存在的 inline profile，不强行走 catalog 校验

这轮修复后，显式 actor/critic slot 的 noop inline profile 不再被 admission 误伤。

### 3. probe 诊断要区分“structured 失败”和“Avalon stage 失败”

多阶段 probe 引入后，如果没有运行任何 `AVALON_*` 检查，就不应该把 `avalonCompatible` 回填成 `structuredCompatible`。

否则会出现：

- 只做 `CONNECTIVITY + STRUCTURED_JSON`
- 但最终 diagnosis 却显示 `AVALON_STAGE_FAILED`

正确做法是：

- 没有 Avalon stage 检查时，`avalonCompatible = null`
- diagnosis 先反映 structured 结论

这样 probe 才能继续作为“普通 OpenAI-compatible 兼容性检查”使用，而不会被多阶段 agent 语义污染。

### 4. provider 路由测试必须固定默认策略，否则测试目标会漂移

当全局默认策略从 `legacy-single-shot` 变成 `tom-tot-critic-v1` 后，原本只想验证：

- provider 路由
- transport 透传
- retry token budget

的测试会被 belief/tot/critic 阶段影响，最终失败原因也会从“网关/兼容”变成“belief stage payload 不匹配”。

这类测试应显式固定：

- `avalon.agent.default-policy-id=legacy-single-shot`

否则测试验证的对象会随着全局默认策略变化而漂移。

## 本轮验证

本轮最终通过了全量测试：

- `mvn --% -q -Dmaven.repo.local=E:\Rubbish\avalon\.m2 test`

额外补上的测试点包括：

- `avalonRuntime` 本地配置不会被转发到上游请求体
- compatibility profile 可以从 `avalonRuntime` 派生 role / timeout / token budget
- 控制台默认 seat/role 绑定改为对 admission eligible profile 做轮换
- 显式 `admissionEligible=false` 的 profile 会在建局前被拒绝

## 后续建议

如果后面要继续增强 admission，建议再补一层“stage support 显式声明”，不要只依赖默认 stage budget 自动推导。

否则当前 admission 更像：

- “是否允许纳入 Avalon agent runtime”

而不是：

- “是否精确支持 belief/tot/critic/decision 的哪几个 stage”
