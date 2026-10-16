# 后端（Spring Boot 模块化单体 · Java 17）

> 事实来源：PRD **v0.3** + 技术设计 **S1a v0.4** + 后端 API 契约 **S2a v1.2**。

## 模块边界（S1a v0.4 §5.1，14 模块 · 包级强边界）

`com.mars.common` / `account` / `live` / `play` / `discovery` / `social` /
`interaction` / `asset` / `earning` / `moderation` / `replay` / `analytics` /
`admin` / `notify`

## 本地构建与测试

```bash
./gradlew test        # JUnit 5
./gradlew specCheck   # 零依赖 spec 套件（AC-4 / AC-8 等核心域断言）
./gradlew check       # 构建 + 全部校验（含 specCheck）
```

无 Gradle 的环境下，核心域可用 JDK 直接编译并运行（见 `docs/backend/MARS-1/implementation-notes.md`）：

```bash
javac --release 17 -encoding UTF-8 -d build/classes $(find src/main/java src/test/java -name '*.java')
java -cp build/classes com.mars.testkit.AllSpecs
```

## 当前增量范围

- **已完成**：`common` 错误码/响应封装、`asset` 币池与结算引擎（AC-8）、`interaction` 弹幕策略（AC-4）、
  `account` 实名/未成年限额策略、`earning` 提现校验与个税计算钩子（OP-23）、`live` 开播前置校验。
  以上均为**零依赖纯 Java**，可在本仓库内直接编译运行。
- **下一次增量**：Spring 装配层（Web / MyBatis / Redis / Kafka / 三方 SDK）、`db/migration` 的执行接入、
  其余模块的业务实现。依赖在 `build.gradle` 按需追加。
