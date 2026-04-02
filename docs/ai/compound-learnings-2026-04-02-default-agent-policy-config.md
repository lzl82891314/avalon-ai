# Compound Learnings - 2026-04-02 - Default Agent Policy Config

## 背景

本轮补充了 agent 策略选择的配置文件默认值能力。

当前优先级固定为：

1. `player.agentConfig.agentPolicyId`
2. `avalon.agent.default-policy-id`
3. `legacy-single-shot`

## 可复用结论

### 1. “默认值”不应写进请求模型本身

`PlayerAgentConfig` 表达的是玩家显式配置。

如果把“配置文件默认策略”直接继续塞进 `PlayerAgentConfig.effectiveAgentPolicyId()`，会把：

- 玩家显式输入
- 应用启动配置
- 内置兜底

三种来源混在一个纯模型类里，后续很难区分优先级来源。

更稳妥的做法是把“应用级默认值”放在最终调度入口解析，也就是 `DefaultTurnAgent`。

### 2. 真正的策略选择入口只有一个

最终决定执行哪个 `DeliberationPolicy` 的地方是 `DefaultTurnAgent`，不是：

- `CreateGameRequest`
- `PlayerAgentConfig`
- `AgentTurnRequestFactory`

因此配置文件默认值也应该在这个入口生效，否则容易出现：

- 实际执行策略是一套
- audit/inputContext 记录的 `agentPolicyId` 又是另一套

这次的处理方式是：

- `DefaultTurnAgent` 先解析最终 `policyId`
- 再把该值写回当前 `PlayerAgentConfig`
- 后续 request/audit/trace 统一读取同一个最终值

### 3. 启动期校验比运行时失败更合适

配置文件里的 `avalon.agent.default-policy-id` 如果写错，不应该等到第一局游戏开始时才报错。

更好的边界是：

- Spring 创建 `DefaultTurnAgent` 时
- 立刻用 `DeliberationPolicyRegistry.require(...)` 校验
- 非法配置直接启动失败

这样错误暴露更早，也不会让运行时错误混入业务流程。

### 4. `strategyProfileId` 不能参与策略选择

这一轮再次确认：

- `agentPolicyId` 负责决定“策略类型”
- `strategyProfileId` 负责标记“策略版本/实验标签”

如果把 `strategyProfileId` 也混入回退链，会让实验标签和执行逻辑耦合，后续很难稳定做离线评估。

## 测试结论

本轮新增测试覆盖了三种核心行为：

- 显式 `agentPolicyId` 覆盖配置文件默认值
- 未显式配置时使用 `avalon.agent.default-policy-id`
- 两者都没有时回退到 `legacy-single-shot`

另外补了非法默认策略的构造期校验测试。

## 环境备注

当前 Codex Windows 沙箱下，部分既有 JUnit `@TempDir` 测试在清理系统临时目录时会报 `AccessDeniedException`，这会影响全模块回归结果，但不影响本轮新增默认策略相关测试的通过情况。
