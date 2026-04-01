# 07 - API 与运行模式

## 1. 运行模式

系统从第一天开始要支持三种玩家控制模式：

### 1.1 LLM 模式
由模型自动完成发言和动作。

### 1.2 HUMAN 模式
由真人通过 API 或未来前端提交动作。

### 1.3 SCRIPTED 模式
用于测试、回归、基准性能或无模型情况下跑通流程。

---

## 2. 为什么要先支持多控制器

因为产品阶段天然会演化：

- V1：5 个 LLM 自动局
- V2：1 真人 + 4 LLM
- V3：多人 + LLM 补位

如果一开始只有 `LlmPlayerController`，后续必然大重构。

---

## 3. 核心接口建议

### 创建游戏
`POST /games`

### 启动游戏
`POST /games/{gameId}/start`

### 单步推进
`POST /games/{gameId}/step`

### 自动运行至结束或等待态
`POST /games/{gameId}/run`

### 查询当前公开状态
`GET /games/{gameId}/state`

### 查询事件流
`GET /games/{gameId}/events`

### 查询某玩家私有视图
`GET /games/{gameId}/players/{playerId}/view`

### 真人玩家提交动作
`POST /games/{gameId}/players/{playerId}/actions`

### 查询回放
`GET /games/{gameId}/replay`

### 查询审计
`GET /games/{gameId}/audit`

---

## 4. API 设计原则

### 4.1 面向视图，不面向数据库实体
返回给调用方的应该是：
- 当前 phase
- 当前可执行动作
- 公开历史
- 私有可见信息
- 是否等待真人输入

而不是一堆裸数据库字段。

### 4.2 后端是真相源
前端未来只是读取事件和提交动作，不负责判定规则。

### 4.3 长远要支持实时推送
未来实时 UI 推荐增加：
- SSE
- WebSocket

但 V1 先用 REST 跑通即可。

---

## 5. WAIT 状态设计

当某个 HUMAN 玩家轮到操作时，编排器应将游戏置为：

- `WAITING_FOR_HUMAN_INPUT`

并记录：
- 当前玩家
- 当前 phase
- 允许动作类型
- 当前截止要求（如需要）

在真人动作提交后再继续推进。

---

## 6. 推荐 DTO 方向

### CreateGameRequest
包含：
- ruleSetId
- setupTemplateId
- seed
- players（seat、controllerType、agentConfig 等）

### GameStateResponse
包含：
- gameId
- status
- phase
- roundNo
- publicState
- nextRequiredActor
- waitingReason

### PlayerPrivateViewResponse
包含：
- seatNo
- roleSummary（仅该玩家可见）
- privateKnowledge
- memorySnapshot
- allowedActions

---

## 7. 给开发 Agent 的实施要求

### V1 必做接口
- create
- start
- step
- run
- state
- events
- player private view

### V2 扩展接口
- human action submission
- wait state query

### V3 扩展接口
- SSE / WebSocket
- connection session 管理

### 禁止
- 禁止把阶段推进写在前端
- 禁止将私密视图通过公开接口泄漏
