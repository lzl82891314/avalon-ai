# Avalon AI 使用说明

## 当前范围

当前仓库实现的是：

- 经典 5 人阿瓦隆 V1
- 后端优先
- 支持 `SCRIPTED`、`LLM(noop fallback)`、`LLM(openai-compatible provider)` 三类席位配置
- 支持 console 模式和显式 server 模式
- 支持事件、快照、回放、审计、恢复

当前没有完成的能力：

- HUMAN 控制器闭环
- `POST /games/{gameId}/players/{playerId}/actions` 提交真人动作
- 实时 SSE / WebSocket
- 前端 UI
- 仓库内自动化真实 OpenAI smoke

## 环境要求

- JDK 21
- Maven 3.9+

标准测试命令：

```bash
mvn -q test
```

如果是在受限沙箱或 CI 环境中遇到 Maven 本地仓库写权限问题，可显式指定本地仓库：

```bash
mvn --% -q -Dmaven.repo.local=E:\Rubbish\avalon\.m2 test
```

## 运行模式

### 1. 默认 console 模式

默认启动命令：

```bash
mvn -f avalon-app/pom.xml spring-boot:run
```

这里虽然 `application.yml` 里的基础值是 `avalon.console.enabled=false`，但启动入口会通过 `AvalonLaunchMode` 默认切到 console 模式。

console 支持的主要命令：

- `new`
- `use <gameId>`
- `config`
- `start`
- `step`
- `run`
- `state`
- `players`
- `player <playerId>`
- `events`
- `replay`
- `audit`
- `help`
- `exit`

### 2. 显式 server 模式

如果需要 REST 接口和 H2 console，用显式 server 模式启动：

```bash
mvn -f avalon-app/pom.xml spring-boot:run -Dspring-boot.run.arguments=--avalon.mode=server
```

默认地址：

- API: `http://localhost:8080`
- H2 console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:avalon;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- 用户名: `sa`
- 密码: 空

## Console 快速上手

### 1. 纯脚本席位

最简单的本地 smoke：

```text
new
<回车，使用默认 ruleset>
<回车，使用默认 template>
<回车，使用默认 seed>
scripted
run
state
replay
audit
exit
```

适合用途：

- 验证规则/恢复/回放链路
- 不依赖任何模型或网络

### 2. 离线 LLM 模式

如果在 `new` 时选择 `noop`，系统会创建 `controllerType=LLM` 的席位，但没有显式 provider 时会回退到 deterministic `NoopAgentGateway`。

这条路径的特点：

- 离线可跑
- 测试覆盖最好
- 它验证的是 LLM 控制器链路，不是真实模型质量

### 3. OpenAI-compatible provider 模式

在 console `new` 向导中选择 `openai` 时，会继续要求输入：

- model name
- temperature
- max tokens
- API key env
- base URL

默认值大致为：

- model: `gpt-5.2`
- temperature: `0.2`
- max tokens: `180`
- api key env: `OPENROUTER_API_KEY`

如果环境变量不存在，console 会给出 warning；真正跑到该席位时，若 provider 调用失败或返回非法结构，游戏会切到 `PAUSED`，并且可以通过 `audit` 查看失败原因。

## REST 使用方式

### 1. 创建游戏

示例：5 个 scripted 席位

```bash
curl -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{
    "ruleSetId": "avalon-classic-5p-v1",
    "setupTemplateId": "classic-5p-v1",
    "seed": 42,
    "players": [
      {"seatNo": 1, "displayName": "P1", "controllerType": "SCRIPTED"},
      {"seatNo": 2, "displayName": "P2", "controllerType": "SCRIPTED"},
      {"seatNo": 3, "displayName": "P3", "controllerType": "SCRIPTED"},
      {"seatNo": 4, "displayName": "P4", "controllerType": "SCRIPTED"},
      {"seatNo": 5, "displayName": "P5", "controllerType": "SCRIPTED"}
    ]
  }'
```

### 2. 启动和运行

```bash
curl -X POST http://localhost:8080/games/{gameId}/start
curl -X POST http://localhost:8080/games/{gameId}/step
curl -X POST http://localhost:8080/games/{gameId}/run
```

常用建议：

- 想看详细过程时，用 `step`
- 想直接跑到结束或暂停时，用 `run`

### 3. 查询接口

```bash
curl http://localhost:8080/games/{gameId}/state
curl http://localhost:8080/games/{gameId}/events
curl http://localhost:8080/games/{gameId}/replay
curl http://localhost:8080/games/{gameId}/audit
curl http://localhost:8080/games/{gameId}/players/P1/view
```

各接口用途：

- `state`: 当前公开局面
- `events`: 原始事件流
- `replay`: 面向阅读的回放投影
- `audit`: 审计摘要与模型失败信息
- `players/{playerId}/view`: 某玩家私有视图

### 4. 真人动作提交

当前仍是 V2 边界：

```bash
POST /games/{gameId}/players/{playerId}/actions
```

返回结果是：

- `501 Not Implemented`

## 席位配置说明

### 1. SCRIPTED

最稳定、最适合回归测试：

```json
{
  "seatNo": 1,
  "displayName": "P1",
  "controllerType": "SCRIPTED"
}
```

### 2. LLM + noop fallback

如果 `controllerType=LLM`，但没有显式 `provider`，会走 deterministic noop fallback：

```json
{
  "seatNo": 1,
  "displayName": "P1",
  "controllerType": "LLM",
  "agentConfig": {
    "outputSchemaVersion": "v1"
  }
}
```

适合：

- 本地离线演示
- 自动化测试
- 验证 LLM controller 流程

### 3. LLM + OpenAI-compatible provider

```json
{
  "seatNo": 1,
  "displayName": "P1",
  "controllerType": "LLM",
  "agentConfig": {
    "outputSchemaVersion": "v1",
    "modelProfile": {
      "provider": "openai",
      "modelName": "gpt-5.2",
      "temperature": 0.2,
      "maxTokens": 180,
      "providerOptions": {
        "apiKeyEnv": "OPENROUTER_API_KEY",
        "response_format": { "type": "json_object" }
      }
    }
  }
}
```

`providerOptions` 支持的连接字段：

- `apiKey`
- `apiKeyEnv`
- `baseUrl`
- `organization`
- `project`
- `timeoutMillis`

如果没有显式提供 `apiKey` 或 `apiKeyEnv`，网关还会尝试读取 `OPENAI_API_KEY`。

## 常见观察面

### 1. `state`

看当前公开进度：

- `status`
- `phase`
- `roundNo`
- `leaderSeat`
- `failedTeamVoteCount`
- `approvedMissionRounds`
- `failedMissionRounds`
- `currentProposalTeam`
- `winnerCamp`

### 2. `player-view`

看某个玩家自己的：

- seat
- role summary
- camp
- visible players
- notes
- memory snapshot
- allowed actions

### 3. `events`

更接近原始事件流，适合：

- 排查状态推进
- 验证事件是否完整落库

### 4. `replay`

更接近“可读回放”，适合：

- 看队伍提案
- 看投票
- 看任务成败
- 看 `GAME_PAUSED`

### 5. `audit`

重点用于：

- 查看 LLM 审计摘要
- 查看非法输出的失败上下文
- 定位为什么进入 `PAUSED`

## 当前限制

- 当前仓库最可靠的自动化路径仍然是 `SCRIPTED` 和 `LLM(noop fallback)`。
- 显式 provider 路由已覆盖 `openai|minimax|glm|claude|qwen`，但真实联网 smoke 不是自动化测试的一部分。
- 当前玩家记忆以快照与接口字段的形式存在，但还没有形成完整的“回合更新 -> 持久化 -> 下回合再注入”闭环。
- 经典 5 人局是当前唯一稳定支持的规则集。

## 建议使用顺序

如果你是第一次接触这个项目，建议按下面顺序使用：

1. 先跑 `mvn -q test`
2. 再用 console 模式跑一局 `scripted`
3. 然后试一局 `noop` LLM
4. 最后在有真实密钥时再试 `openai` 或其他 OpenAI-compatible provider

这样能最快区分：

- 是规则链路问题
- 是运行时/恢复问题
- 还是 provider/模型问题
