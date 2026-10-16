# MARS-1 · 后端实现说明（S3b 增量 1：P0 脚手架 + 核心域逻辑）

> 事实来源：PRD **v0.3** + 技术设计 **S1a v0.4** + 后端 API 契约 **S2a v1.2**。
> 分支：`release/MARS-1-live-mvp`（自 `main` 拉出）。

## 1. 本增量交付范围

| 域 | 模块 | 交付内容 | 对应 |
| --- | --- | --- | --- |
| 通用 | `com.mars.common` | `ErrorCode`（契约 §2.7 全表：码值 ↔ HTTP ↔ 语义）、`ApiResponse`、`PageResult`、`BizException`、`IdempotencyRegistry`、`SlidingWindowRateLimiter` | 契约 §2 |
| 通用 | `com.mars.common.money` | `MoneyMath`（整数分、HALF_UP / FLOOR 取整策略）、`ProportionalAllocator`（最大余数法，Σ 恒等于总量） | S1a §3.4.5 |
| 资金 | `com.mars.asset.pool` | `CoinPoolState` / `PoolChannel`：滚动累计币池（加权平均成本法、渠道费率快照、退款冲减不为负） | S1a §3.4.2 / §3.4.7 |
| 结算 | `com.mars.earning.settlement` | `SettlementCalculator`：先扣后分 + 税费分层 + 渠道明细 + `rounding_diff_fen` 吸收取整差；`SettlementBill` / `SettlementBillChannel`（字段名逐字冻结） | S1a §3.4.4–3.4.6 / **AC-8** |
| 个税 | `com.mars.earning.tax` | `FlatTaxCalculator`（`PENDING-OP-23` 占位费率 + 钩子）、`TaxCalculator` 接口 | S1a §3.4.4 / OP-23 |
| 互动 | `com.mars.interaction` | `DanmakuPolicy`：**1 条/秒 · ≤50 字 · 5s 去重** + 手机号门槛 + 禁言 + 敏感词（复用 `40001`） | S1a §7.5 / **AC-4** |
| 账号 | `com.mars.account` | `AgePolicy`（18 岁生日边界）、`MinorLimitPolicy`（单笔/单日限额 + OP-21 实名前置） | OP-6 / BR-5 / **AC-6** / OP-21 |
| 收益 | `com.mars.earning` | `WithdrawalPolicy`（仅个人主播、起提 10,000 分、卡名一致）、`TaxMode` 两模式到账口径 | BR-6 / OP-16 / OP-23 |
| 开播 | `com.mars.live` | `PrecheckPolicy`：实名 / 类目白名单 / 标题机审 / 封面机审逐项返回 | FR-2.5 / **AC-1** / BR-2 |
| 建库 | `db/migration/V1__init.sql` | 已实现域的表结构 + `sys_config` 全量口径种子 + 4 类目 + 币池单例 + 未成年默认档 + 最小礼物目录 | S1a P0.4 / §6.2 |

**目录布局**：`backend/`（后端）、`db/migration/`（Flyway 版本化 SQL）、`deploy/`（镜像）。三端单仓多包（`app/`、`admin-web/` 由前端增量落地）。

## 2. 可复跑命令与结果（本地实测）

环境：JDK 21（Temurin，`javac 21.0.12`），`--release 17` 编译。**本工作区无 Gradle / Maven 与依赖缓存**，故核心域以 JDK 直编直跑（见下）；CI 侧用 `deploy/backend.Dockerfile` 的 Gradle 镜像跑 `gradle check`。

```bash
cd backend
javac --release 17 -encoding UTF-8 -d build/classes \
  $(find src/main/java src/test/java -name '*.java')
java -cp build/classes com.mars.testkit.AllSpecs
```

**结果：`specs: 29, checks: 208, failed specs: 0` → `RESULT: PASS (all specs green)`，退出码 0。**

> 测试形态说明：为在无 Gradle / 无依赖缓存的环境里**真正跑起来**，spec 采用仓库内零依赖断言器
> （`com.mars.testkit`），断言语义与 JUnit 5 一致；`build.gradle` 已同时接入 JUnit 5，迁移时逐条映射即可，
> 且 `specCheck` 已挂到 `check`，Gradle 与直编两条路径跑的是同一套断言。

## 3. 关键口径落实点（可逐条核对）

| 口径 | 实现落点 | 证据 |
| --- | --- | --- |
| **AC-8 误差 0** | `SettlementCalculator` | §3.4.6 示例逐字段断言：`400000 → 24450 → 22533 → 353017 → 176508 / 176509 → 10590 → 165918`；渠道 `Σfee + rounding_diff == channel_fee`；5 条对账恒等 |
| **先扣后分 + 税费分层** | `distributable = gross − channel_fee − platform_tax_fee`；`anchor_tax` 在分成之后扣 | `anchor_net + anchor_tax == anchor_gross`；`platform_net` 不含个税 |
| **费率/税率按订单快照** | `PoolChannel.feeRate`（下单快照） | 后续把税率/分成率改值，11 月账单字段不变，仅新账期重算 |
| **弹幕唯一口径** | `DanmakuPolicy` 常量 1 / 1000ms / 50 / 5000ms | 直接断言常量值，防「第二套数值」 |
| **40100 与 40325 边界** | `ErrorCode` 语义 + 弹幕手机号门槛 | 未登录 → 40100；已登录未绑手机号 → 40325；断言两者不互串 |
| **OP-21 开关** | `MinorLimitConfig.requiresRealname` | 开关开且未实名 → `40350`；关则不拦 |
| **OP-23 只落钩子** | `TaxCalculator` + `FlatTaxCalculator.PENDING_OP_23` | 费率可替换实现，账单结构不变 |

## 4. 已知限制 / 风险（如实登记）

1. **Spring 装配层未落地**（下次增量）：Web / MyBatis / Redis / Kafka / 腾讯云 SDK 未接入，`build.gradle` 仅经 BOM 固定版本；`deploy/backend.Dockerfile` 的运行阶段已注释待启用。
2. **本地未执行 Gradle 构建**：工作区无 Gradle/Maven 及依赖缓存，无法拉取 Spring/JUnit 依赖 → 已改用 JDK 直编直跑核心域（全部绿）。**CI 需在 `deploy/backend.Dockerfile` 的 Gradle 镜像内完成首次 `gradle check`**，以验证依赖坐标与构建脚本。
3. **`V1__init.sql` 未在真实 MySQL 上执行**：工作区无 MySQL 实例。DDL 按 MySQL 8 语法编写，未做执行验证；CI/本地起库时需先跑一次。
4. **个税为占位实现**（`PENDING-OP-23`），非税务结论，上线前必须替换。
5. **未实现域**：`play`（播放地址签发/心跳）、`discovery`（热度 ZSet）、`social`、`moderation`、`replay`、`analytics`、`admin`、`notify` 的表与业务随各自增量落地。
6. **回放 30 天清理、空闲 30 分钟断流**等定时任务未实现（依赖调度与 CSS 回调）。

## 5. AC 对照

| AC | 本增量覆盖 | 说明 |
| --- | --- | --- |
| AC-1 未实名拦截开播 | ✅ `PrecheckPolicy` | 未实名 → 主错误码 `40310`，逐项返回 |
| AC-4 弹幕敏感词/限流/房管 | ✅ `DanmakuPolicy` | 1 条/秒 · ≤50 字 · 5s 去重 + 敏感词 `40001` + 禁言 `40300` |
| AC-6 未成年限额阻断 | ✅ `MinorLimitPolicy` | 单笔/单日超限 → `40320`；OP-21 前置 → `40350` |
| AC-8 收益与后台对账误差 0 | ✅ `SettlementCalculator` | §3.4.6 复算 + 5 条恒等 + 确定性 + 快照不变性 |
| AC-2/3/5/7/9/10 | ◐ 部分 | 结算字段与错误码基础已就位；推流/播放/礼物扣费事务/审核/回放/越权拦截待后续增量 |
