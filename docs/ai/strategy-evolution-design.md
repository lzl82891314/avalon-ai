# 阿瓦隆大模型策略进化设计

## 1. 背景与问题

当前系统已经能够运行经典 5 人阿瓦隆，并且具备以下基础能力：

- 运行时会构造完整的 `PlayerTurnContext`
- LLM 回合会产出 `audit`、`decision report`、`memoryUpdate`
- 游戏结束后可以通过 `replay` 和 `audit` 回放模型行为
- 已有 `PlayerMemoryState`、`MemoryUpdate`、`promptProfileId` 等可扩展钩子

但现阶段大模型仍表现出明显的固定策略模式，例如：

- 派西维尔作为首轮队长时，经常稳定地把两个候选位一起带上车
- 某些角色在类似局面下会重复同一类投票或发言模式
- 多局运行后不会把失败经验沉淀为下一局可用的策略差异

这说明当前系统的核心问题不是“模型不够随机”，而是“系统没有学习闭环”。  
低温度、固定 prompt、固定角色视角和缺少评估晋升机制，会让模型持续重复相同策略。

## 2. 设计目标

本设计的目标是为阿瓦隆 LLM 玩家建立真正可演化的策略系统，而不是仅靠增加随机性制造表面差异。

目标分为两层：

- 局内进化：同一局中，模型能根据提案、投票、任务结果、他人发言持续调整判断
- 跨局进化：一局结束后，系统能复盘表现，产生候选策略，并通过评估筛选出更优版本

本设计采用以下默认策略：

- 双层进化：同时支持局内适应和跨局沉淀
- 评估驱动：通过离线评估、自博弈和指标比较决定策略是否更优
- 按角色独立：梅林、派西维尔、忠臣、莫甘娜、刺客各自演化
- 人工批准晋升：候选策略通过评估后，仍需人工确认才能成为正式版本

## 3. 当前系统现状

### 3.1 已有基础设施

- `TurnContextBuilder` 已经会把 `state.memoryOf(playerId)` 注入回 `PlayerTurnContext.memoryState`
- `PromptBuilder` 已经把 `memory` 注入 prompt
- `LlmPlayerController` 已经能把模型输出中的 `memoryUpdate` 转成领域对象
- `RuntimePersistenceService` 已经会持久化 `player_memory_snapshot`
- `audit_record`、`decision report` 已经能提供较好的复盘输入

### 3.2 关键缺口

- `GameOrchestrator.recordAction(...)` 当前没有消费 `PlayerActionResult.memoryUpdate`
- `player_memory_snapshot` 只按 `gameId + playerId` 保存，主要用于局内恢复，不承担跨局学习
- `PlayerAgentConfig.promptProfileId` 已存在，但尚未真正承载“策略版本”
- 当前没有 `StrategyProfile`、评估批次、胜率对比、晋升流程等策略演化基础设施
- 当前没有把“某策略为什么输”稳定转化成“下一版该怎么改”的复盘器

### 3.3 直接后果

- 模型有“记忆字段”，但没有真正写回运行态并影响后续行为时，局内学习基本不存在
- 模型有“审计日志”，但没有策略版本与评估器时，跨局学习基本不存在
- 结果是系统更像“带日志的固定 prompt 执行器”，而不是“可进化玩家”

## 4. 总体方案

总体方案分为四个层次：

1. 局内记忆闭环  
   把 `memoryUpdate` 和事件派生观察真正合并回运行态，并在下一回合注入 prompt。

2. 角色级策略版本化  
   用 `StrategyProfile` 管理每个角色的策略版本、父子关系、状态和摘要。

3. 评估驱动的候选筛选  
   用固定 seed 套件、座位轮换、自博弈批次比较 candidate 与 champion 的表现。

4. 人工晋升与可回滚发布  
   评估通过后进入待审；人工批准后替换 champion，并保留回滚能力。

系统核心原则如下：

- 随机性只用于探索，不用于替代学习
- 正式对局使用稳定 champion，探索发生在离线评估或灰度环境
- 策略按角色演化，不混合为一个全局经验池
- 进化必须是可写回、可评估、可晋升、可回滚的闭环

## 5. 局内进化机制

### 5.1 运行态记忆写回

在玩家动作被接受后，runtime 需要正式消费 `PlayerActionResult.memoryUpdate`：

- 读取当前 `PlayerMemoryState`
- 执行 `PlayerMemoryState.merge(update, now)`
- 把结果写回 `state.memoryByPlayerId`
- 让后续同一玩家回合读取到更新后的 memory

这一步是局内进化的最小闭环，没有它，`memoryUpdate` 只是审计附属品。

### 5.2 事件派生记忆

不能把记忆完全依赖模型自述。系统需要引入事件派生记忆器，从客观事件补充结构化观察：

- 某玩家提案是否连续带入失败位
- 某玩家是否频繁赞成高风险队伍
- 某次任务失败后，哪些成员进入嫌疑池
- 某角色是否在讨论中持续推动固定组合
- 某玩家的公开表态与投票/行动是否明显不一致

这些派生结果写入：

- `suspicionScores`
- `trustScores`
- `observations`
- `lastSummary`

### 5.3 Prompt 注入分层

Prompt 不再只注入“当前局面 + 私有视角”，而是分四层注入：

- 规则契约：本阶段允许动作、阿瓦隆硬规则、输出约束
- 角色基础 playbook：该角色的基本职责、常见风险、应优先观察的信号
- 激活策略版本：当前 `StrategyProfile` 的行为偏好、判断框架、角色特定启发式
- 当前局记忆：`memoryState`、最近关键事件摘要、候选人与嫌疑变化

这样局内变化才会真实影响行为，而不是只出现在审计字段里。

## 6. 跨局进化机制

### 6.1 策略版本模型

引入 `StrategyProfile`，每个角色独立维护自己的策略谱系。建议字段包括：

- `strategyProfileId`
- `roleId`
- `status`：`DRAFT`、`CANDIDATE`、`READY_FOR_REVIEW`、`CHAMPION`、`ARCHIVED`
- `parentStrategyProfileId`
- `summary`
- `systemPromptDelta`
- `heuristicConfigJson`
- `sourceRunId`
- `createdAt`、`updatedAt`

`CHAMPION` 表示正式对局使用版本，`CANDIDATE` 表示待评估版本。

### 6.2 复盘生成候选策略

引入 `StrategyRefiner` 作业，输入为：

- `replay`
- `audit`
- 最终胜负
- 角色真值
- 关键误判点

输出为新的候选策略版本，主要产物不是代码补丁，而是：

- 更精确的角色行为规则
- 对高风险局面的处理偏好
- 对特定反模式的限制语句
- 角色特定的观察顺序和优先级

例如针对派西维尔，可以沉淀出：

- 首轮不默认把两个候选同时上车
- 优先通过发言、投票、失败暴露去区分真假候选
- 在缺乏强证据时，不把“候选集合”直接当作“可信集合”

### 6.3 评估驱动筛选

引入 `StrategyTournamentService` 和评估批次对象，对 candidate 做离线比较。评估必须具备：

- 固定 seed suite
- 座位轮换
- 对手版本固定或受控
- 足够样本量
- 角色专属指标

建议核心指标包括：

- 角色胜率
- 非法输出率 / 暂停率
- 关键阶段误判率
- 首轮高风险提案率
- 任务失败暴露后的修正能力
- token 成本

这里只把“更强”定义为综合表现提升，而不是“决策看起来更不一样”。

## 7. 数据模型与接口

### 7.1 领域与持久化对象

建议新增：

- `StrategyProfile`
- `StrategyEvaluationRun`
- `StrategyEvaluationRecord`

建议新增表：

- `strategy_profile`
- `strategy_evaluation_run`
- `strategy_evaluation_record`

### 7.2 现有接口的角色变化

- `PlayerAgentConfig.promptProfileId` 正式承载“策略版本 ID”
- `player_memory_snapshot` 继续承担局内恢复职责，不直接承担跨局版本管理
- `audit_record` 和 `decision report` 继续作为评估与复盘输入

### 7.3 新增 API / Console 能力

建议新增 API：

- `GET /strategy-profiles`
- `POST /strategy-profiles`
- `POST /strategy-profiles/{id}/evaluate`
- `POST /strategy-profiles/{id}/promote`
- `POST /strategy-profiles/{id}/archive`
- `POST /strategy-profiles/{id}/rollback`
- `GET /strategy-leaderboard?roleId=...`

建议新增 console 命令：

- `strategy list`
- `strategy eval <id>`
- `strategy promote <id>`
- `strategy rollback <id>`
- `strategy report <id>`

## 8. 评估与晋升流程

建议流程如下：

1. 正式 champion 在生产对局中运行，持续积累 `audit`、`replay`、`decision report`
2. `StrategyRefiner` 从失败样本和代表性样本中生成新的 `CANDIDATE`
3. `StrategyTournamentService` 对 candidate 与 champion 做固定批次比较
4. 如果 candidate 达到阈值，则进入 `READY_FOR_REVIEW`
5. 人工审查评估报告、异常率、角色指标和行为变化
6. 审批通过后，将 candidate 晋升为新的 `CHAMPION`
7. 保留上一版 champion，支持快速回滚

这条链路必须避免两种错误：

- 未评估版本直接进入正式对局
- 只因为更随机就被误判为更优

## 9. 测试方案

### 9.1 局内闭环测试

- 某玩家在任务失败后，下一轮 `memoryState` 必须发生可观测变化
- 相同玩家在收到新 memory 后，prompt 中必须体现新的 `strategyMode`、`summary` 或嫌疑变化
- 同一局中，后续决策不应完全等同于“没有失败信息时的默认行为”

### 9.2 跨局评估测试

- 相同 seed suite 下，同一 candidate 的评估结果应可重复
- candidate 和 champion 的比较必须稳定，不依赖一次幸运对局
- 策略版本切换后，正式局要能明确追溯到具体 `strategyProfileId`

### 9.3 安全与发布测试

- `CANDIDATE` 不得自动进入正式局
- promote 后正式局应切换到新 champion
- rollback 后正式局应恢复上一 champion
- recovery / replay / audit 主路径不得因为策略版本化退化

### 9.4 专项案例测试

针对“派西维尔首轮固定带双候选上车”建立专项评估：

- 比较旧策略与新策略的长期胜率
- 比较首轮高风险提案率
- 比较在候选视角下的误判率
- 比较是否只是更随机，还是更有效地区分真假候选

## 10. 边界与非目标

本阶段不包括：

- 微调 / RL / 外部训练管线
- 把具体玩家 ID、某局隐藏身份结论直接带入下一局
- 让 candidate 自动替换 champion
- 用高温度扰动代替策略改进

本阶段的重点是把进化闭环做实，而不是把学习系统做重。

## 11. 总结

阿瓦隆 LLM 玩家要具备“逐步进化”的能力，关键不在于让模型每次说得更不一样，而在于建立完整闭环：

- 局内：记忆能写回，观察能累积，下一回合能读取
- 跨局：策略有版本，候选能评估，正式版本能晋升和回滚

只有当系统具备 `写回 -> 评估 -> 晋升 -> 回滚` 的能力时，模型策略才会真正随经验演化。  
随机性可以帮助探索，但不能替代学习；审计可以帮助复盘，但不能替代进化。
