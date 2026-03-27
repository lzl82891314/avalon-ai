# Compound Learnings 2026-03-25

## Durable Learnings

- 当设计文档把 `avalon-core` 定义为规则真相源时，`avalon-runtime` 不应再自己维护一套平行的主状态机推进逻辑；否则测试会同时证明两套规则，而不是证明一套规则。
- 如果领域模型已经引入 `PlayerMemoryState` 和 `MemoryUpdate`，那么最小闭环必须同时包含三件事：运行时合并、快照持久化、下一回合注入。只做模型和快照而不做运行时合并，等于没有真正实现记忆系统。
- 持久化层如果选择用 `state_json` 聚合保存运行态，必须在文档中明确它是“有意简化”，不能默认视为已经对齐了更细粒度的架构文档表设计。
- README 里凡是写“默认模式”或“已完成 provider 支持”的地方，都应该以启动入口和自动化验证为准，而不是以 `application.yml` 的基础配置或手工假设为准。
- 对 LLM provider 的评估要拆成两层：代码路径已存在，不等于真实 provider 已验收。单测覆盖 request/response mapping，只能证明适配器可解析，不能证明真实联网运行稳定。
