# 05 - Agent 运行时设计

## 1. 目标

Agent 运行层负责把“一个具备私有视野与记忆的玩家”映射为一次受控、可审计、可恢复的模型调用。

它不是裁判层，不负责决定规则是否成立。  
它负责的是：

- 组装结构化输入
- 调用指定模型
- 解析结构化输出
- 校验与必要重试
- 返回：
  - 公开发言
  - 结构化动作
  - 审计摘要
  - 记忆更新建议

---

## 2. 为什么每个玩家必须单独配置模型

系统必须允许：
- 5 个席位用 5 个不同模型
- 不同 provider 混搭
- 某一个席位使用本地模型
- 某一个席位改成真人控制
- 某一个席位改成脚本策略

因此必须按玩家保存独立的模型配置。

### 推荐模型配置

```java
public class ModelProfile {
    private String provider;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> providerOptions;
}
```

```java
public class PlayerAgentConfig {
    private String playerId;
    private String promptProfileId;
    private String outputSchemaVersion;
    private String auditLevel;
    private ModelProfile modelProfile;
}
```

---

## 3. Agent Gateway 抽象

```java
public interface AgentGateway {
    AgentTurnResult playTurn(AgentTurnRequest request);
}
```

### 说明
- `AgentGateway` 负责屏蔽底层 provider 差异
- 内部可使用 Spring AI、LangChain4j 或你自己的适配层
- 业务层不应直接依赖具体 provider SDK

---

## 4. 一次 Agent 回合的输入

```java
public class AgentTurnRequest {
    private String gameId;
    private Integer roundNo;
    private String phase;
    private String playerId;
    private Integer seatNo;
    private String roleId;
    private Map<String, Object> privateKnowledge;
    private PublicGameSnapshot publicState;
    private PlayerMemoryState memory;
    private List<String> allowedActions;
    private String rulesSummary;
    private String outputSchemaVersion;
}
```

### 输入内容应包含
- 当前局面公开信息
- 当前玩家可见私有信息
- 当前玩家的结构化记忆
- 当前阶段允许的动作类型
- 当前局规则摘要
- 输出 schema 版本

---

## 5. 一次 Agent 回合的输出

```java
public class AgentTurnResult {
    private PublicSpeech publicSpeech;
    private PlayerAction action;
    private AuditReason auditReason;
    private MemoryUpdate memoryUpdate;
    private RawCompletionMetadata modelMetadata;
}
```

### 输出分层含义

#### 公开发言（publicSpeech）
给所有玩家可见的自然语言发言。

#### 结构化动作（action）
真正提交给规则引擎判断的动作。

#### 审计摘要（auditReason）
给系统记录与回放的理由摘要，不等于原始 chain-of-thought。

#### 记忆更新（memoryUpdate）
模型建议系统如何更新该玩家的私有记忆。

---

## 6. 为什么不要依赖原始 CoT

不要把“可审计”理解成“必须保存模型私有思维链全文”。

正确做法是保存：
- 结构化理由摘要
- 信念变化
- 策略模式
- 关键判断依据

这样更稳定，也更适合产品化。

---

## 7. 输出校验与重试

模型输出可能有问题，例如：
- JSON 结构不合法
- 动作类型不合法
- 组队人数不合法
- 输出内容缺字段

因此必须做：
1. JSON 解析
2. Schema 校验
3. 业务规则校验
4. 必要时一次或有限次重试
5. 仍失败则走 fallback 策略

---

## 8. Prompt Builder 的职责

Prompt Builder 不应写业务规则，只负责把系统真相转换成模型可读文本或结构。

### 它应该做
- 拼装规则摘要
- 插入当前阶段说明
- 插入当前私有视野
- 插入结构化记忆摘要
- 限定输出格式

### 它不应该做
- 决定谁赢
- 决定是否合法
- 判断该阶段能不能进入下一阶段

---

## 9. PlayerController 与 LLM 的关系

`LlmPlayerController` 是 `PlayerController` 的一种实现。

```java
public class LlmPlayerController implements PlayerController {
    private final AgentGateway agentGateway;
    // ...
}
```

### 运行流程
1. 构建 `PlayerTurnContext`
2. 转换为 `AgentTurnRequest`
3. 调用 `agentGateway.playTurn`
4. 解析为 `PlayerActionResult`
5. 提交给规则引擎与编排器

---

## 10. 给开发 Agent 的实施要求

### 必做
- 先定义输入输出协议与 JSON schema
- 再写 Prompt Builder
- 模型调用层必须支持按玩家路由
- 审计摘要与公开发言必须分开

### 推荐顺序
1. `ModelProfile`
2. `PlayerAgentConfig`
3. `AgentTurnRequest/Result`
4. `AgentGateway`
5. `PromptBuilder`
6. `ResponseParser`
7. `ValidationRetryPolicy`
8. `LlmPlayerController`

### 禁止
- 禁止直接让业务层调用 provider SDK
- 禁止把所有历史原文无脑塞进 Prompt
- 禁止把模型上下文当数据库用
