# Avalon V1 实现完整性评审

日期：2026-03-25

## 评审结论

结论先行：

- 当前实现已经具备较完整的 V1 运行闭环：可以创建 5 人经典局、启动、逐步推进、自动跑完全局、查看公开状态、查看私有视图、查看事件/回放/审计，并且支持快照恢复和 console/server 两种运行方式。
- 代码层面当前是稳定的：`mvn --% -q -Dmaven.repo.local=E:\Rubbish\avalon\.m2 test` 已于 2026-03-25 实测通过。
- 但如果按设计文档评审“V1 后端游戏平台”的完整性，而不是按“能跑一个 demo 局”的口径评审，我不建议接受“已实现 92%+ 且与设计基本一致”的说法。
- 主要原因不是功能不可用，而是仍存在两个严重设计偏差：
  - 玩家记忆闭环没有真正落地。
  - runtime 没有把 `avalon-core` 规则引擎作为唯一状态真相源。

建议判断：

- 从“可运行交付度”看：高。
- 从“设计一致性”看：中等，且存在严重偏差。
- 从“V1 设计验收是否可完全收口”看：尚未完全收口。

## 已符合设计的部分

- 多模块结构基本符合设计文档的分层方向：`avalon-core`、`avalon-config`、`avalon-runtime`、`avalon-agent`、`avalon-persistence`、`avalon-api`、`avalon-app`、`avalon-testkit` 都已存在并可协同工作。
- 角色分配和可见性没有写死在 prompt 中，而是由 core/config 真相层驱动。`avalon-runtime` 的 `RoleAssignmentService` 和 `VisibilityService` 都在委托 `avalon-core` 实现。
- API 面已经具备 V1 主要控制与查询能力：`create/start/step/run/state/events/replay/audit/player-view` 已落地；真人动作提交明确返回 `501`，边界清晰。
- 持久化、恢复、回放、审计四条主链路都能工作。尤其是“session eviction 后通过持久化恢复再继续运行”的能力，已有集成测试覆盖。
- LLM 路径不是直接让业务层调用 provider SDK，而是通过 `AgentGateway`、`PromptBuilder`、`ResponseParser`、`ValidationRetryPolicy`、`LlmPlayerController` 分层接入。
- 对非法 LLM 输出的处理是安全的：不会直接 500，而是把游戏切到 `PAUSED` 并保留审计记录。
- 默认启动模式确实是 console-first，不是 README 误写。启动逻辑在 `avalon-app/src/main/java/com/example/avalon/app/AvalonLaunchMode.java:7` 到 `:42`，默认会选择 `CONSOLE` 并注入 `--avalon.console.enabled=true`。

## 严重偏差

### 1. 玩家记忆闭环没有真正实现

现状：

- `PlayerMemoryState` 和 `MemoryUpdate` 的领域模型是完整的，`PlayerMemoryState.merge(...)` 也已经实现，见 `avalon-core/src/main/java/com/example/avalon/core/player/memory/PlayerMemoryState.java:34` 到 `:82`。
- 但 runtime 在构建回合上下文时，没有把持久化或内存中的真实玩家记忆注入给玩家，而是每次都塞一个新的 `PlayerMemoryState.empty(...)`，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/service/TurnContextBuilder.java:34` 到 `:51`，其中关键点在 `:47`。
- `PlayerActionResult` 已经带有 `memoryUpdate` 字段，见 `avalon-core/src/main/java/com/example/avalon/core/game/model/PlayerActionResult.java:7` 到 `:12`，但 `GameOrchestrator.recordAction(...)` 完全没有消费它，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/orchestrator/GameOrchestrator.java:290` 到 `:303`。
- `GameRuntimeState` 里保存的只是 `Map<String, Map<String, Object>> memoryByPlayerId`，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/model/GameRuntimeState.java:29`、`:57` 到 `:59`、`:135` 到 `:140`。
- `RuntimePersistenceService` 会把这些 raw map 快照化，但不会做领域级合并，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/persistence/RuntimePersistenceService.java:107` 到 `:116`。

影响：

- LLM 每回合收到的“记忆”并不是上一回合累积出来的真实记忆，而是一个新的空对象。
- `memoryUpdate` 目前只是模型输出的一部分，没有进入运行态，也不会反过来影响下一回合。
- `/players/{playerId}/view` 返回的 `memorySnapshot` 与 LLM 实际收到的 `context.memoryState` 不是一回事；前者来自 `state.memoryOf(playerId)`，后者在 `TurnContextBuilder` 中被重建为空。
- 这意味着“中断后依赖快照 + 玩家记忆继续运行”的设计目标目前只完成了“快照存取”，没有完成“玩家记忆参与推理闭环”。

结论：

- 这是当前实现与设计最严重的不一致点之一。

### 2. runtime 没有把 core 规则引擎作为唯一真相源

现状：

- `avalon-core` 已经有一套完整的规则引擎接口，包含 `allowedActions`、`validateAction`、`applyAction`、`nextPhase`、`isGameEnded`，见 `avalon-core/src/main/java/com/example/avalon/core/game/rule/GameRuleEngine.java:9` 到 `:18`。
- 但 `avalon-runtime` 自己又定义了一套更薄的 `GameRuleEngine`，只保留 `teamSizeForRound`、`missionFailThresholdForRound`、`shouldEnterAssassination`、`resolveWinner` 等辅助函数，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/engine/GameRuleEngine.java:5` 到 `:14`。
- `GameOrchestrator.step(...)` 及后续的 `processDiscussionStep/processProposalStep/processVoteStep/processMissionStep/resolveMission/processAssassination` 用了大量手写状态机分支推进局面，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/orchestrator/GameOrchestrator.java:94` 到 `:280`。
- 当前只有“允许动作计算”还走 `avalon-core` 规则引擎，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/service/TurnContextBuilder.java:37`。

影响：

- 当前实现并不是“规则引擎是唯一真相源”，而是“core 决定 allowed actions，runtime 自己推进主状态机”。
- 这在经典 5 人局上仍然可运行，但一旦扩展到更多人数、更多角色、更多变体，很容易出现 `core` 规则与 `runtime` 手写状态推进不一致。
- `avalon-core` 的规则单测并不能直接证明 `avalon-runtime` 的真实运行路径也严格遵守同一套规则真相。

结论：

- 这是严重的架构偏差。它未必马上导致当前 V1 跑不起来，但已经违背了设计文档最核心的一条约束。

## 重要偏差与未收口项

### 3. Prompt 规则摘要没有从规则元数据生成

现状：

- `PlayerTurnContext` 为 `rulesSummary` 预留了字段，见 `avalon-core/src/main/java/com/example/avalon/core/game/model/PlayerTurnContext.java:8` 到 `:22`。
- `AgentTurnRequestFactory` 会直接把 `context.rulesSummary()` 填进请求，见 `avalon-agent/src/main/java/com/example/avalon/agent/service/AgentTurnRequestFactory.java:34` 到 `:36`。
- 但 `TurnContextBuilder` 目前传入的是硬编码字符串 `"Classic five-player Avalon"`，见 `avalon-runtime/src/main/java/com/example/avalon/runtime/service/TurnContextBuilder.java:49` 到 `:51`。
- `PromptBuilder` 只是把这个值原样塞进 prompt，见 `avalon-agent/src/main/java/com/example/avalon/agent/service/PromptBuilder.java:8` 到 `:35`。

影响：

- 规则说明没有从 `RuleSetDefinition` 动态生成。
- 这与“规则是一等数据、Prompt 不应成为规则真相”的设计原则不完全一致。
- 当前因为只支持一个经典 5 人局，所以影响还没有扩大；但一旦扩展规则集，这里会首先漂移。

结论：

- 这是重要但尚未阻塞当前经典 V1 的偏差。

### 4. 持久化形态比设计文档更轻，缺少若干推荐表与读模型

现状：

- 当前迁移脚本只创建了 `game_event`、`game_snapshot`、`player_memory_snapshot`、`audit_record` 四张表，见 `avalon-app/src/main/resources/db/migration/V1__init_persistence.sql:1` 到 `:64`。
- 设计文档推荐的 `game_session`、`game_player`、`role_assignment`、`replay_projection` 没有单独落表。
- 当前做法是把 `GameSetup`、players、roleAssignments、当前局面等整体编码进 `game_snapshot.state_json`，再由恢复服务重建运行态。
- `ReplayQueryService` 也不是读取物化 projection 表，而是对事件流做查询时投影。

影响：

- 这套实现对当前 V1 是可用的，恢复链路也已经被测试验证。
- 但它与设计文档里的数据库层形态不一致，后续如果要做运维查询、后台管理、跨局分析、前端高频读取，会比设计文档预期更难扩展。

结论：

- 这是“架构简化”而不是“V1 当前不可用”，但需要在评审中明确写出来，不能当作已经完全对齐设计。

### 5. 真实 OpenAI provider 路径仍缺少仓库内可验证的验收证据

现状：

- README 明确写了：真实 OpenAI smoke 仍需要手工执行，且不在 `mvn -q test` 范围内，见 `README.md:146` 到 `:152`。
- 当前自动化测试覆盖的是：
  - provider 路由；
  - OpenAI request/response 映射；
  - 无网 deterministic noop fallback；
  - LLM 失败时的 `PAUSED` 路径。
- 代码层面显式 `provider=openai` 的路径已经存在，但仓库没有提供“真实联网 + 真密钥 + 跑完一局”的已执行证据。

影响：

- 不能说“OpenAI 适配器不存在”。
- 但也不能把“真实 provider 已完成 V1 验收”当作事实。

结论：

- 这是 V1 close-out 剩余项，不是最严重的设计偏差，但应该在结论里保留。

### 6. `GameStateResponse` 结构预留了更多字段，但当前状态接口没有完全填满

现状：

- `GameStateResponse` 里有 `nextRequiredActor` 和 `waitingReason` 字段，见 `avalon-api/src/main/java/com/example/avalon/api/dto/GameStateResponse.java:11` 到 `:13`、`:55` 到 `:68`。
- `PersistentGameApplicationService.getState(...)` 目前只填了 `gameId/status/phase/roundNo/publicState`，见 `avalon-api/src/main/java/com/example/avalon/api/service/PersistentGameApplicationService.java:98` 到 `:115`。

影响：

- 当前 V1 仍然够用，因为真人等待态是 V2 范围。
- 但从 API 设计文档口径看，状态视图还没有完全收口。

结论：

- 这是轻度偏差，且主要影响 V2/V3 预留能力。

## 不应算作缺陷的边界

- `POST /games/{gameId}/players/{playerId}/actions` 返回 `501` 是合理的 V2 边界，不应算作 V1 缺陷。
- `WAITING_FOR_HUMAN_INPUT` / `HUMAN` controller 没有完整闭环，不应算作 V1 漏实现，但应在使用说明中明确为未开放能力。
- 当前只支持 classic 5-player ruleset，本身符合 V1 收敛范围。

## 测试与证据覆盖

已验证：

- 全仓测试已通过。
- API 集成测试覆盖了 `create/start/state/events/replay/audit/player-view/run` 这些主路径。
- 恢复测试覆盖了 snapshot + tail event replay，以及恢复后继续 `run-to-end`。
- LLM 失败测试覆盖了 `PAUSED`、审计落库、回放可见、恢复后保持 `PAUSED`。
- 启动模式测试证明默认是 console，显式 `--avalon.mode=server` 可切 server。

未验证或未自动化验证：

- 真实 OpenAI provider 跑通 1 席或 5 席。
- 玩家记忆 update 的真正运行时合并与下一回合再注入。
- HUMAN controller 的等待态与提交流程。

## 最终判断

如果评审口径是“另一个 agent 是否已经做出一个可以演示、可以测试、可以恢复的 V1 版本”，答案是：

- 是，已经做出来了，而且完成度不低。

如果评审口径是“它是否已经较完整地按设计文档实现了 V1 的关键设计约束”，答案是：

- 还没有。
- 当前至少有两个不应被忽略的严重偏差：
  - 玩家记忆闭环未实现。
  - runtime 未把 core 规则引擎当作唯一真相源。

因此更准确的总结不是“V1 已 92%+ 且只差真实 key smoke”，而是：

- V1 的运行闭环、控制面、持久化恢复和 console 交付已经基本成型；
- 但 V1 的设计一致性尚未完全收口，尤其是记忆系统和规则真相源这两项。
