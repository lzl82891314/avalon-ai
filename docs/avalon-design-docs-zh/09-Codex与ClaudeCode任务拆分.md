# 09 - Codex / Claude Code 任务拆分文档

本文件专门面向 AI 编码代理，要求其可以**按任务项直接开始开发**。

---

## 1. 对编码代理的总规则

1. `avalon-core` 是领域真相源
2. 不要把规则判断放进 Prompt 模板
3. 不要把架构写死为只支持 5 人
4. 规则 / 角色 / 模板必须数据驱动
5. 玩家控制器抽象必须保持干净
6. 每个改变游戏真相的步骤都要持久化
7. 倾向拆分小服务，不要写 God Class
8. 先保证正确，再考虑“智能感”

---

## 2. 推荐开发顺序

### Work Item A：Maven 多模块骨架

#### 目标
建立基础工程结构。

#### 需要创建的模块
- `avalon-app`
- `avalon-core`
- `avalon-config`
- `avalon-agent`
- `avalon-runtime`
- `avalon-persistence`
- `avalon-api`
- `avalon-testkit`

#### 完成标准
- 项目可编译
- 模块依赖单向清晰
- Spring Boot 可启动

---

### Work Item B：配置模型与加载器

#### 目标
建立规则、角色、模板的正式配置入口。

#### 需要实现
- `RuleSetDefinition`
- `RoleDefinition`
- `SetupTemplate`
- YAML 加载服务
- 启动校验逻辑

#### 完成标准
- 启动时能加载一个合法 5 人局配置
- 非法配置会快速失败

---

### Work Item C：领域核心

#### 目标
建立游戏会话、动作模型、阶段模型和基础规则接口。

#### 需要实现
- `GameSession`
- `GamePlayer`
- `RoleAssignment`
- phase/status 枚举
- action types
- allowed action model
- rule engine interfaces

#### 完成标准
- 编译通过
- 有基础单元测试

---

### Work Item D：身份分配与视野系统

#### 目标
完成 seed 分配与私有知识生成。

#### 需要实现
- 基于 seed 的角色分配
- 角色揭示
- `VisibilityService`
- 私有知识生成逻辑

#### 完成标准
- 5 个 seat 恰好拿到 5 个角色
- 每个 seat 只看到规则允许的信息

---

### Work Item E：脚本玩家全流程

#### 目标
在不接 LLM 的情况下先跑通完整流程。

#### 需要实现
- `PlayerController`
- `ScriptedPlayerController`
- `GameOrchestrator`
- 组队流程
- 投票流程
- 任务流程
- 刺杀流程

#### 完成标准
- 脚本玩家可以跑完整局
- 所有阶段事件都正确记录

---

### Work Item F：持久化层

#### 目标
为审计、回放、恢复建立数据库基础。

#### 需要实现
- 数据库迁移脚本
- event store repository
- snapshot repository
- memory snapshot repository
- replay projection repository

#### 完成标准
- 事件可持久化并可查询
- 快照可读取

---

### Work Item G：REST API

#### 目标
让系统能被外部驱动。

#### 需要实现
- create/start/run/step
- state/events/replay/player-view
- audit endpoint

#### 完成标准
- 脚本局可完全通过 API 控制

---

### Work Item H：LLM Agent Runtime

#### 目标
接入 LLM 玩家。

#### 需要实现
- `ModelProfile`
- `PlayerAgentConfig`
- `AgentGateway`
- `PromptBuilder`
- `ResponseParser`
- `ValidationRetryPolicy`
- `LlmPlayerController`

#### 完成标准
- 至少一个玩家可由 LLM 驱动
- LLM 响应经结构化校验后能进入规则引擎

---

### Work Item I：混合控制模式

#### 目标
支持真人玩家。

#### 需要实现
- `HumanPlayerController`
- 等待态处理
- 真人动作提交接口

#### 完成标准
- 支持 1 Human + N LLM 的对局

---

### Work Item J：恢复与回放

#### 目标
保证中断后继续运行与赛后分析能力。

#### 需要实现
- `RecoveryService`
- `ReplayQueryService`
- 回放 DTO
- 记忆快照恢复逻辑

#### 完成标准
- 任意中断后可以继续
- 可以查询整局关键步骤

---

## 3. 对编码代理的代码风格要求

### 必须遵守
- 领域对象命名清晰
- 服务职责单一
- JSON 字段有明确 schema version
- 关键路径有日志
- 所有外部输入做校验

### 不要这样做
- 不要在 controller 里写规则逻辑
- 不要在 PromptBuilder 里写规则引擎逻辑
- 不要把一个 service 做成万能大类
- 不要让恢复逻辑依赖临时内存状态

---

## 4. 建议的测试清单

### 单元测试
- ruleset config validation
- role assignment by seed
- team size validation
- mission fail threshold logic
- assassination resolution

### 集成测试
- scripted full game
- event store persistence
- snapshot recovery
- human wait state
- one-llm mixed game

### 回归测试
- 同 seed 同配置结果一致
- 不同规则集加载结果正确
- 恢复后继续运行结果一致

---

## 5. 编码代理第一批提交建议

第一批提交只做以下内容，不要贪多：

1. 多模块骨架
2. 配置模型
3. 游戏领域模型
4. seed 身份分配
5. 5 人脚本局跑通

这是最稳的起点。
