# 05 - Codex / Claude Code 构建指令

## 1. 目标

本文件不是讲架构，而是直接给 AI 编码代理下达可执行的构建指令。你可以将本文件整段贴给 Codex / Claude Code，让其按阶段生成项目代码。

---

## 2. 总体执行要求

请基于以下要求创建一个 Java Maven 多模块项目：

- 项目名称：Avalon AI
- 技术栈：Java 21, Maven, Spring Boot 3.x
- AI 框架：Spring AI 为主，LangChain4j 可作为辅助
- 项目目标：构建一个可扩展的阿瓦隆游戏后端，支持 LLM / Human / Scripted 三类玩家

必须遵守以下规则：

1. 使用 Maven 多模块结构
2. `avalon-core` 不允许依赖 Spring
3. 规则定义必须配置化，不能只写在 prompt 中
4. 玩家控制器必须抽象为统一接口
5. 所有关键动作必须可审计
6. 必须为断点恢复预留快照与玩家记忆持久化结构

---

## 3. 第一阶段构建任务

### Task 1：创建 Maven 多模块骨架

创建以下模块：

- `avalon-core`
- `avalon-runtime`
- `avalon-agent`
- `avalon-persistence`
- `avalon-api`
- `avalon-app`

要求：

- 根 pom 统一管理版本
- 各模块 pom 先可编译
- 所有模块目录、包结构、基础空类先创建出来

### Task 2：创建核心领域模型

在 `avalon-core` 中创建：

- `Game`
- `Player`
- `Phase`
- `GameStatus`
- `PlayerType`
- `Camp`
- `RoleDefinition`
- `RuleSetDefinition`
- `SetupTemplate`
- `RoleAssignment`
- `PlayerAction`
- `PlayerTurnContext`
- `PlayerActionResult`
- `PlayerController`
- `GameRuleEngine`

要求：

- 先创建基础字段与 getter/setter 或 record
- 不要求完整实现
- 不使用 Spring 注解

### Task 3：创建运行时骨架

在 `avalon-runtime` 中创建：

- `GameOrchestrator`
- `PlayerControllerResolver`
- `TurnContextBuilder`
- `GameSessionService`

要求：

- 先给出方法签名
- 使用接口注入依赖
- 先写占位逻辑，不要求完整跑通

### Task 4：创建持久化骨架

在 `avalon-persistence` 中创建：

- `GameEventEntity`
- `GameSnapshotEntity`
- `PlayerMemoryEntity`
- 对应 Repository
- 简单 Mapper

要求：

- 支持 JSON 字段落库
- 先用 PostgreSQL 假设设计

### Task 5：创建 API 骨架

在 `avalon-api` 中创建：

- `GameController`
- DTO
- `GlobalExceptionHandler`

最少接口：

- 创建游戏
- 开始游戏
- 单步推进
- 查询状态

---

## 4. 第二阶段构建任务

### Task 6：实现规则、角色、模板配置加载

要求：

- 从 resources 读取 YAML
- 反序列化为 Java 模型
- 支持按 ruleSetId / roleId / templateId 查询

### Task 7：实现身份分配服务

创建 `RoleAssignmentService`。

要求：

- 输入：规则集、setup template、玩家列表、随机 seed
- 输出：角色分配结果
- 必须可重放

### Task 8：实现最小规则引擎

先只支持经典 5 人局。

必须支持：

- 讨论阶段
- 队长提名
- 全员投票
- 任务执行
- 胜负判定
- 刺杀阶段

### Task 9：实现 ScriptedPlayerController

要求：

- 不接 LLM
- 先实现随机或固定策略
- 用于打通最小流程

### Task 10：实现 run-to-end

要求：

- 从创建游戏到整局结束可自动推进
- 中途写入事件与快照

---

## 5. 第三阶段构建任务

### Task 11：接入 Spring AI

要求：

- 创建 `ModelProfile`
- 创建 `ChatModelFactory`
- 创建 `SpringAiAgentGateway`
- 支持 per-player 独立模型配置

### Task 12：实现 LlmPlayerController

要求：

- 输入 `PlayerTurnContext`
- 输出 `PlayerActionResult`
- 模型必须返回结构化 JSON
- 解析失败时支持兜底策略

### Task 13：实现玩家记忆存储

要求：

- 保存 `PlayerMemoryState`
- 每回合支持 memory update merge
- 为恢复预留 snapshot 版本号

### Task 14：实现恢复能力

要求：

- 从最新 game snapshot 恢复局面
- 从最新 player memory 恢复各玩家记忆
- 从中断 phase 继续推进

---

## 6. 对 AI 编码代理的输出要求

在每一阶段结束后，AI 编码代理都应输出：

1. 已创建的模块与类清单
2. 当前未完成项
3. 能否编译通过
4. 下一阶段建议

---

## 7. 禁止事项

AI 编码代理不得：

- 把所有代码放到一个模块
- 把规则硬编码在 Controller
- 把 prompt 当作规则真相源
- 让 `avalon-core` 依赖 Spring
- 不做事件和快照却声称支持恢复
