# 01 - Maven 项目结构设计

## 1. 目标

本文件定义整个项目的 Maven 模块划分、顶层目录结构、打包方式与推荐命名。目标是保证：

- 领域层纯净，不被 Spring 或 AI 框架污染
- 编排层与 AI 接入层分离
- 持久化、API、启动模块职责清晰
- 后续可平滑扩展为单机人机模式、多人联机模式与前端 UI 模式

---

## 2. 顶层项目命名

推荐根项目名：

- `avalon-ai`
- 或 `avalon-agent-game`

推荐 Maven `groupId`：

- `com.example.avalon`
- 若有你自己的组织前缀，可替换为正式公司域名反写

推荐版本起始值：

- `0.1.0-SNAPSHOT`

---

## 3. Maven 多模块结构

推荐结构如下：

```text
avalon-ai/
├── pom.xml
├── avalon-core/
├── avalon-runtime/
├── avalon-agent/
├── avalon-persistence/
├── avalon-api/
└── avalon-app/
```

### 模块说明

#### `avalon-core`
纯领域层。

只放以下内容：

- 游戏核心实体
- 值对象
- 枚举
- 规则定义模型
- 角色定义模型
- PlayerController 等核心接口
- RuleEngine 接口
- 与具体框架无关的异常、校验模型

禁止依赖：

- Spring
- Spring AI
- LangChain4j
- JPA
- Web

#### `avalon-runtime`
流程编排层。

只放以下内容：

- GameOrchestrator
- TurnScheduler
- ControllerResolver
- ContextBuilder
- Runtime 协调服务

允许依赖：

- `avalon-core`

尽量不要直接依赖 Spring，保持可测试性。

#### `avalon-agent`
AI 接入层。

只放以下内容：

- AgentGateway
- Spring AI 模型路由与调用
- LangChain4j 辅助封装
- Prompt 构造器
- 响应解析器
- 模型配置与适配器

允许依赖：

- `avalon-core`
- `avalon-runtime`（仅在必要时）
- Spring AI
- LangChain4j
- Jackson / Validation

#### `avalon-persistence`
持久化层。

只放以下内容：

- EventStore 实现
- Snapshot Repository
- PlayerMemory Repository
- JPA / JDBC Entity
- 数据库映射与转换器

允许依赖：

- `avalon-core`
- `avalon-runtime`
- Spring Data JPA 或 Spring JDBC

#### `avalon-api`
接口层。

只放以下内容：

- REST Controller
- WebSocket / SSE 推送入口
- DTO
- 请求响应转换
- API 异常处理

允许依赖：

- `avalon-core`
- `avalon-runtime`
- `avalon-persistence`
- Spring Web

#### `avalon-app`
启动模块。

只放以下内容：

- `@SpringBootApplication`
- Spring 配置装配
- Bean 装配
- 环境启动配置

这个模块是最终可运行模块，依赖其他所有模块。

---

## 4. 顶层 pom.xml 推荐职责

根 `pom.xml` 只负责：

- 定义 modules
- 统一 Java 版本
- 统一依赖版本管理
- 插件管理

建议使用：

- Java 21
- Spring Boot 3.x
- Spring AI 最新兼容版本
- LangChain4j 最新稳定版本

根 pom 中不放业务代码。

---

## 5. 推荐包结构

以 `com.example.avalon` 为例。

### `avalon-core`

```text
com.example.avalon.core
├── game
│   ├── model
│   ├── enums
│   ├── event
│   ├── rule
│   └── setup
├── player
│   ├── model
│   ├── controller
│   └── memory
├── role
│   ├── model
│   └── visibility
└── common
    ├── exception
    └── validation
```

### `avalon-runtime`

```text
com.example.avalon.runtime
├── orchestrator
├── scheduler
├── context
├── resolver
└── service
```

### `avalon-agent`

```text
com.example.avalon.agent
├── gateway
├── model
├── prompt
├── parser
├── config
└── support
```

### `avalon-persistence`

```text
com.example.avalon.persistence
├── entity
├── repository
├── mapper
├── store
└── snapshot
```

### `avalon-api`

```text
com.example.avalon.api
├── controller
├── dto
├── assembler
└── advice
```

### `avalon-app`

```text
com.example.avalon.app
├── config
└── AvalonApplication
```

---

## 6. 打包与运行建议

### 第一阶段

建议只运行：

- `avalon-app`

其余模块都打成普通 jar。

### 第二阶段

如需多人联机或独立后台，可继续保留单体 Spring Boot 架构，不急于拆微服务。

---

## 7. 给 AI 编码代理的硬性要求

Codex / Claude Code 在构建 Maven 项目时必须遵守：

1. 先创建根 pom 与全部模块目录
2. 先写空模块 pom，保证可以 `mvn -q -DskipTests compile`
3. 先创建接口和领域实体，再写实现
4. 不允许把所有代码都塞进 `avalon-app`
5. 不允许把规则引擎逻辑写进 Controller
6. 不允许让 `avalon-core` 依赖 Spring

---

## 8. 这一文件的交付目标

AI 编码代理读完本文件后，应能完成：

- 创建根目录
- 创建多模块 pom
- 建立模块目录与基础包结构
- 为后续骨架代码提供承载位置
