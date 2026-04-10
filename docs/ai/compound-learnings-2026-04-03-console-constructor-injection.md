# Compound Learnings - 2026-04-03 - Console Constructor Injection Boundary

## 背景

本轮修复了 `AvalonConsoleRunner` 的启动失败。

表面报错是：

- Spring 缺少 `java.lang.String` bean

但根因不是配置文件缺失，而是组件上存在两个构造器时，把 `@Autowired` 放到了不完整的便捷构造器上。

## 可复用结论

### 1. Spring 组件存在重载构造器时，注入入口必须是“配置最完整”的那个

如果一个组件同时有：

- Spring 运行时使用的主构造器
- 测试或手工构造使用的便捷构造器

那么 `@Autowired` 必须标在主构造器上，而不是便捷构造器上。

否则 Spring 会把便捷构造器中的裸参数当成普通 bean 解析，例如：

- `String`
- `Path`
- 原始类型包装类

最终表现通常是启动时报：

- `required a bean of type 'java.lang.String' that could not be found`

### 2. `@Value` 只对 Spring 实际选择的构造器参数生效

即使另一个重载构造器里写了：

- `@Value("${...}") String reportOutputDir`

只要 Spring 没选它，这些配置就不会参与注入。

因此“多一个带 `@Value` 的构造器”本身不构成保护，真正决定行为的是：

- Spring 最终选中了哪个构造器

### 3. 便捷构造器应只作为委托层存在

较稳妥的组织方式是：

- 主构造器：带完整 `@Value` / Spring 注入语义
- 便捷构造器：不带注解，只负责委托到主构造器

这样既保留：

- 测试里直接 `new`

又不会污染：

- Spring 的自动装配边界

## 本轮验证

修复后执行了全量测试：

```powershell
mvn --% -q -Dmaven.repo.local=E:\Rubbish\avalon\.m2 test
```

结果通过，说明：

- `AvalonConsoleRunner` 启动路径恢复正常
- 现有控制台测试仍可通过便捷构造器直接实例化
- `avalon.agent.default-policy-id` 与 report output dir 的配置读取未回归
