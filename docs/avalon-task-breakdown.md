# Avalon 全局任务拆解书

## 1. 文档目的

这份文档不是某一次 session 的临时记录，而是 Avalon 项目的全局任务拆解书。它回答四个问题：

- V1 到底包含哪些能力
- 当前已经完成了什么
- M1 到 M5 各自还剩什么
- 当前 V1 大约完成了多少

它和仓库内两份台账文档的关系如下：

- 本文档：看全局范围、里程碑、百分比、优先级
- `E:\Rubbish\avalon\avalon-ai\docs\implementation\master-plan.md`：看工作项编号、依赖、写入范围
- `E:\Rubbish\avalon\avalon-ai\docs\implementation\progress.md`：看连续 session 的最新完成记录和下一步

---

## 2. 当前统计口径

### 2.1 总范围

根据设计文档，整体路线可拆成三层：

- V1：纯后端自动对局，支持脚本玩家与 LLM 玩家
- V2：真人输入、人机混合、等待态
- V3：多人联机、实时推送、前端协同

### 2.2 当前只统计 V1

本阶段百分比只统计 V1，不把 V2/V3 混入。

V1 的完成标准是：

- 可以通过配置创建一局经典阿瓦隆
- 规则真相由后端规则引擎维护
- 支持身份分配、可见性、完整对局推进
- 支持事件持久化、快照、恢复、回放查询
- 默认通过 console 交互完成开局、运行、状态查看、回放与审计查看，不强依赖手工 HTTP
- REST 控制与查询仍可用，但属于显式 server 模式
- 支持 5 个席位接成脚本玩家、deterministic noop LLM 或显式 OpenAI LLM 自动对局

### 2.3 当前明确不计入 V1 的内容

- 真人动作提交闭环
- WAITING_HUMAN_INPUT 的完整产品化
- 联机房间
- SSE / WebSocket 实时推送
- 前端页面

---

## 3. V1 里程碑拆解

| 里程碑 | 名称 | 目标 | 权重 | 当前状态 |
| --- | --- | --- | --- | --- |
| M1 | 规则与脚本闭环 | 不接 LLM，先把规则、配置、脚本局跑通 | 30% | 已完成 |
| M2 | 持久化与恢复 | 事件、快照、恢复、回放读模型打底 | 25% | 已完成 |
| M3 | V1 API 面 | 通过 REST 控制和查询对局 | 15% | 已完成 |
| M4 | LLM 接入 | AgentGateway、PromptBuilder、结构化解析、LlmPlayerController | 20% | 已完成 |
| M5 | V1 收口与验收 | 端到端联调、稳定性、README、回归测试 | 10% | 进行中 |

### 当前 V1 总进度估算

按上述权重，当前估算如下：

- M1 已完成：30%
- M2 已完成：25%
- M3 已完成：15%
- M4 已完成：20%
- M5 约完成 80%：约 8%

**当前 V1 总进度：约 98%**

这里的 98% 是按可交付里程碑权重估算，不是按代码行数估算。

---

## 4. 各里程碑详细任务书

## 4.1 M1：规则与脚本闭环

### 目标

在不接 LLM 的前提下，完整跑通经典 5 人局，证明架构边界和规则真相是对的。

### 范围

- Maven 多模块骨架
- `avalon-core` 领域模型
- `avalon-config` YAML 配置加载
- 经典 5 人局规则/角色/模板
- 确定性身份分配
- 可见性与私有知识生成
- 最小规则引擎
- `ScriptedPlayerController`
- `GameOrchestrator`
- `run-to-end`
- 测试夹具与端到端脚本局测试

### 验收标准

- 全仓可编译
- 全仓测试通过
- 经典 5 人局可稳定跑到终局
- 相同 seed 输出稳定
- 每个 seat 的私有视图符合规则

### 当前状态

已完成。

### 已落地关键代码

- 代码根目录：`E:\Rubbish\avalon\avalon-ai`
- 核心规则引擎：[ClassicAvalonRuleEngine.java](/E:/Rubbish/avalon/avalon-ai/avalon-core/src/main/java/com/example/avalon/core/game/rule/ClassicAvalonRuleEngine.java)
- YAML 加载：[YamlConfigLoader.java](/E:/Rubbish/avalon/avalon-ai/avalon-config/src/main/java/com/example/avalon/config/io/YamlConfigLoader.java)
- 运行时编排：[GameOrchestrator.java](/E:/Rubbish/avalon/avalon-ai/avalon-runtime/src/main/java/com/example/avalon/runtime/orchestrator/GameOrchestrator.java)
- 脚本局测试：[ClassicFivePlayerRunToEndTest.java](/E:/Rubbish/avalon/avalon-ai/avalon-testkit/src/test/java/com/example/avalon/testkit/ClassicFivePlayerRunToEndTest.java)

---

## 4.2 M2：持久化与恢复

### 目标

把系统从“能跑一局”升级到“能落盘、能恢复、能回放”。

### 范围

- Flyway 迁移
- `game_event`
- `game_snapshot`
- `player_memory_snapshot`
- `audit_record`
- 事件仓储
- 快照仓储
- 运行时状态编解码
- 恢复服务
- 回放查询服务

### 当前已完成

- Flyway 初始迁移已落地
- 事件、快照、记忆快照、审计四类实体和索引已落地
- JPA repository 与 store 已落地
- `RuntimeStateCodec` 已落地
- `RuntimePersistenceService` 已落地
- 首次持久化和终局状态会强制落快照，不再依赖事件数刚好命中阈值
- `RecoveryService` 已支持“最新快照 + 之后事件重放”恢复 live state
- `ReplayQueryService` 已支持事件/审计的持久化查询
- 运行时恢复测试已覆盖快照恢复、tail event replay、以及恢复后继续 run-to-end

### 当前未完成

- phase 级 storyline 压缩仍可继续增强，但不再阻塞当前 M2 关闭

### 当前状态

已完成。

### 当前 M2 完成度估算

100%

### M2 完成后，V1 累计进度

约 55%

---

## 4.3 M3：V1 API 面

### 目标

把系统从“只能在代码里调”升级到“可以通过 REST 控制和查询”。

### 范围

- `POST /games`
- `POST /games/{id}/start`
- `POST /games/{id}/step`
- `POST /games/{id}/run`
- `GET /games/{id}/state`
- `GET /games/{id}/events`
- `GET /games/{id}/players/{playerId}/view`
- `GET /games/{id}/replay`
- `GET /games/{id}/audit`

### 当前已完成

- 内存 stub service 已被真实持久化 runtime service 替换
- `create/start/step/run/state/events/replay/audit` 已接入真实服务
- runtime 身份分配、私有视野和 `allowedActions` 已对齐到 `avalon-core` + 配置真相源，不再继续依赖 runtime 内的简化占位逻辑
- `GameController` 已补齐显式 `@PathVariable`
- Spring Boot 应用已补 JPA repository/entity 扫描
- 配置资源路径已兼容从仓库根目录和模块目录启动
- `GameApiIntegrationTest` 已通过 H2 + Flyway + JPA + MockMvc 打通 `create/start/state/events/replay/audit/player-view`
- `GameApiIntegrationTest` 已补齐角色差异化 `player-view` 断言，以及 proposal phase 下 `allowedActions` 的 actor 级断言
- API 层已覆盖 session eviction 后通过 `GET /state` 触发恢复，再继续 `POST /run` 跑到终局
- `/replay` 已不再等于原始事件列表，而是带 `replayKind` 和 `summary` 的 projection 视图
- `submitPlayerAction` 已明确收口为 V2 能力边界，当前返回 `501 Not Implemented`

### 当前未完成

- `audit` 目前只是查询通路打通，真实生产仍要等后续 LLM/校验链路

### 当前状态

已完成。

### 当前 M3 完成度估算

100%

### M3 完成后，V1 累计进度

约 70%

---

## 4.4 M4：LLM 接入

### 目标

把脚本玩家闭环升级为真正的 V1 目标：5 个席位可以由 LLM 自动完成整局。

### 范围

- `ModelProfile`
- `PlayerAgentConfig`
- `AgentTurnRequest`
- `AgentTurnResult`
- `AgentGateway`
- `PromptBuilder`
- `ResponseParser`
- `ValidationRetryPolicy`
- `LlmPlayerController`

### 当前状态

已完成。

### 当前基础

- `avalon-agent` 模块骨架已存在
- `PromptBuilder`、`ResponseParser`、`ValidationRetryPolicy`、`LlmPlayerController` 已落地
- runtime `PlayerControllerResolver` 已支持按玩家配置解析 `LLM` 控制器
- `GameApiIntegrationTest` 已证明至少一个 LLM 席位在默认 deterministic gateway 下可以 `run` 到终局
- `AgentTurnRequest` 已带上 provider、modelName、temperature、maxTokens 与 providerOptions
- [RoutingAgentGateway.java](/E:/Rubbish/avalon/avalon-ai/avalon-agent/src/main/java/com/example/avalon/agent/gateway/RoutingAgentGateway.java) 已按 per-seat provider 在 deterministic noop fallback 与 OpenAI gateway 之间路由
- [OpenAiChatCompletionsGateway.java](/E:/Rubbish/avalon/avalon-ai/avalon-agent/src/main/java/com/example/avalon/agent/gateway/OpenAiChatCompletionsGateway.java) 与 [JdkOpenAiHttpTransport.java](/E:/Rubbish/avalon/avalon-ai/avalon-agent/src/main/java/com/example/avalon/agent/gateway/JdkOpenAiHttpTransport.java) 已落地，真实 provider 路径改为显式 opt-in
- OpenAI 路径已有请求构建、provider 路由、响应解析与缺省回退单测覆盖，同时全仓 `mvn -q test` 继续通过

---

## 4.5 M5：V1 收口与验收

### 目标

把“开发可运行”提升到“V1 可交付”。

### 范围

- 端到端联调
- 恢复后一致性验证
- 公开视图与私有视图隔离验证
- LLM 非法输出兜底
- README 与运行说明
- 回归测试补齐

### 当前状态

进行中。

### 当前已完成

- 根目录 `README.md` 已改为以 console-first 运行方式为主，补齐了默认启动命令、console 操作说明、显式 server 模式入口以及 OpenAI provider 配置方式
- `docs/implementation/openai-smoke-checklist.md` 已补齐真实 provider 的手工 smoke 路径
- LLM 成功动作现在会通过 runtime 持久化链路写入 `audit_record`，`/audit` 不再只是空查询通路
- LLM 连续非法输出现在会把对局安全切到 `PAUSED`，同时把失败上下文写入 `audit_record`
- `GAME_PAUSED` 现在会进入 `/replay` 投影视图，且 session eviction 后恢复仍保持 `PAUSED`
- 当前已经明确默认 `LLM` 席位仍走 deterministic noop fallback，真实 OpenAI 适配仅在 per-seat `provider=openai` 时启用
- `avalon-app` 现在默认以 console 模式启动，终端可直接执行 `new/start/step/run/state/player/players/events/replay/audit`
- console 运行时会在终端打印完整公开流程，包括每一轮状态、每个公开发言、组队、投票、任务结果、暂停原因和最终胜负

### 当前未完成

- 真实密钥条件下的手工 smoke test 仍待执行
- 如果要把显式 OpenAI provider 视为完全验收，还需要在真实联网和真实密钥条件下完成最终 smoke

---

## 5. 当前已完成内容总结

截至当前 session，已完成的高价值内容是：

- M1 全量完成
- M2 的持久化基础、快照、恢复 replay、恢复后继续执行验证已完成
- M3 的核心 REST 查询面已完成收口，`player-view` 已按角色规则返回正确私有视图，V2 action submit 边界已显式固定为 `501`
- M4 已完成，provider-routed `AgentGateway`、OpenAI adapter、请求/响应映射和默认 noop fallback 已全部落地
- M5 已进入收口后段，console-first 启动链路、终端全流程输出、README/运行说明、provider 配置说明以及成功动作的审计落库已补齐
- 全仓 `mvn -q test` 已在最新状态下通过
- 统一任务拆解书、执行计划、连续进度台账均已建立并持续更新

---

## 6. 为什么当前是约 98%

### 不是更低的原因

- 最容易把架构做坏的基础层已经完成
- 规则真相、配置真相、运行时边界、持久化边界都已经成型
- 恢复链路已经不再只是“能读快照”，而是能重放快照后的事件
- API 不只是“接口已接线”，而是已经对齐 core/config 真相源，并有真实 Spring 集成测试证明私有视图与 V2 边界
- LLM 路径不再是纯骨架，控制器、提示构造、结构化解析、重试、provider 路由和 OpenAI 适配都已落地
- 默认启动路径已经切到 console，终端能够直接承载 V1 需要的开始、过程、进度、公开发言、回放与审计查看

### 也不是更高的原因

- 显式 OpenAI provider 的最终人工联网验收还没有完成
- 如果后续还想继续打磨 operator 体验，console 输出样式仍有精修空间，但这已经不阻塞 V1 功能闭环

---

## 7. 下一步推荐顺序

下一次 session 建议严格按下面顺序推进：

1. 用真实 API key 做 console 或 server 模式下的 OpenAI smoke
2. 如有需要，再根据 operator 反馈微调 console 输出样式
3. V2 的真人等待态继续保持延后

### 7.1 M5 下一步

优先补：

- 用真实 API key 做 1 席与 5 席 smoke，确认 provider 适配链路在联网条件下可跑
- 如果用户对 console operator 体验还有新要求，再补相应输出和命令
- V2 范围继续延后，不要把真人输入闭环重新混入 V1

---

## 8. 与仓库内台账文档的对应关系

- 全局拆解书：`E:\Rubbish\avalon\avalon-task-breakdown.md`
- 代码仓库：`E:\Rubbish\avalon\avalon-ai`
- 执行计划：`E:\Rubbish\avalon\avalon-ai\docs\implementation\master-plan.md`
- 连续进度：`E:\Rubbish\avalon\avalon-ai\docs\implementation\progress.md`

后续如果继续多 agent 并行开发：

- 先看本文档，判断当前整体位置
- 再看 `master-plan.md`，认领具体工作项
- 再看 `progress.md`，判断哪些已经做完、哪些仍在进行中

---

## 9. 当前一句话结论

当前状态不是“V1 快做完了”，也不是“还停在骨架阶段”，而是：

**M1 已完成，M2 已完成，M3 已完成，M4 已完成，M5 已进入最后收口，当前 V1 约 98%，代码层面的 console-first 功能已经闭环，剩余主要是显式 OpenAI provider 的真实密钥 smoke。**
