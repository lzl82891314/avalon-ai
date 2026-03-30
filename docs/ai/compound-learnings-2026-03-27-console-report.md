# Compound Learnings 2026-03-27 Console Report

## ce-brainstorm

- console 可读性问题的根因不是数据缺失，而是当前输出按 `event` 和 `audit` 逐条直出，阅读粒度与排障粒度混在了一起。
- 对“局后汇总”这类需求，优先在 console 侧做阅读投影，而不是反向污染 runtime 事件模型；底层事件流继续保持原子、可恢复、可审计。
- `PLAYER_ACTION` 只记录动作类型和公开发言，不包含投票/组队/任务细节，所以阅读投影必须允许从后续的 `TEAM_PROPOSED`、`TEAM_VOTE_CAST`、`MISSION_ACTION_CAST`、`ASSASSINATION_SUBMITTED` 回填动作细节。

## ce-plan

- 终端报告和 Markdown 报告应该共享同一份聚合模型，而不是分别从原始 DTO 再解析一遍；这样字段优先级、轮次分组和失败补行规则只会存在一处。
- “终局自动输出”应该放在 console runner 边界，而不是下沉到 application service；报告是操作员体验，不是核心运行时职责。
- 对暂停局，报告不能只展示 `GAME_PAUSED` 事件，还需要把同序号 audit 转成一条失败决策行，否则最关键的失败上下文会丢掉。

## ce-review

- 这次实现没有改动 runtime 状态机、持久化 schema、recovery 逻辑或 API DTO；所有新增行为都限制在 `avalon-app` console 层，架构边界保持稳定。
- 终局自动输出只在状态从非终态跃迁到 `PAUSED/ENDED` 时触发，避免 `use` 绑定历史终局局面时重复刷屏。
- 报告写盘路径落在 `target/reports/avalon/`，避免把运行期产物默认写进仓库追踪目录。

## ce-compound

- 对“给现有系统补可读性”的需求，新增一个面向阅读的 projection 通常比改造原始领域事件更稳妥；前者改善 UX，后者容易影响恢复、重放和审计语义。
- 如果一个动作的完整语义被拆在多个原子事件里，阅读投影层应显式支持“主事件 + 后续细节事件”的关联规则，而不是假设主事件天然自描述。
- console 输出和 Markdown 输出虽然面向不同介质，但最好共用同一份报告模型；介质差异只应该体现在截断、转义和版式层，而不是数据选择层。
- 角色私有知识里“精确知道某角色是谁”和“只知道若干候选身份”必须建成两种显式规则语义；一旦把两者压扁成同一个 `SEE_PLAYERS_BY_ROLE`，配置层就会在无报错的前提下制造规则泄漏。
- 对 Avalon 这类带伪装效果的角色，`伪装给别人看` 不等于 `自己获得那份知识`；像 Morgana 这类效果应该体现在 Percival 的歧义视野上，而不是反向写成 Morgana 自身的 knowledge rule。

## ce-compound-followup

- console 调试视图不能假设 `privateKnowledge.visiblePlayers` 一定已经是 JSON `Map`；在同进程调用 application service 时，它很可能还是 JVM 内对象，阅读层必须同时兼容 `VisiblePlayerInfo` 和反序列化后的 `Map`。
- 当需要判断“模型是不是被规则错误告知了信息”时，单看最终 `privateThought` 不够，必须把当轮实际送给模型的 `inputContext.privateKnowledge` 暴露出来；否则无法区分是 prompt 泄漏、上下文串线，还是模型自行越权推断。
- 对带候选身份的私有知识，prompt 约束和 runtime 校验要同时存在；只靠 prompt 容易被模型忽略，只靠校验又会让错误信息在首轮生成时毫无引导。
- `candidateRoleIds` 的语义应该被当成“可疑集合”而不是“未排序答案”；凡是从候选集合直接落到确定断言的文本，都应该被视为知识越权并触发 retry 或暂停。
- 玩家 memory 的 API 视图和 LLM 实际收到的上下文不能分叉；如果 `getPlayerView` 展示的是持久化 memory，但 `TurnContextBuilder` 仍然注入 `PlayerMemoryState.empty(...)`，调试时会出现明显的“UI 看起来有记忆，模型其实没有”的错觉。
