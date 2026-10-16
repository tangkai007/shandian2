# MARS-1 · 后端实现说明

> 事实来源：PRD **v0.3** + 技术设计 **S1a v0.4** + 后端 API 契约 **S2a v1.2**。
> 分支：`release/MARS-1-live-mvp`（自 `main` 拉出）。

## 增量 1 · P0 脚手架 + 核心域逻辑（已 PASS）

| 域 | 模块 | 交付内容 | 对应 |
| --- | --- | --- | --- |
| 通用 | `common` | `ErrorCode`（契约 §2.7 全表）、`ApiResponse`、`PageResult`、`BizException`、幂等登记、滑动窗口限流、金额与按权重分配 | 契约 §2 |
| 资金 | `asset.pool` | 滚动累计币池（加权平均成本、费率快照、冲减不为负） | S1a §3.4.2 / §3.4.7 |
| 结算 | `earning.settlement` | 先扣后分 + 税费分层 + 渠道明细 + `rounding_diff_fen` 吸收取整差；字段名逐字冻结 | S1a §3.4.4–3.4.6 / **AC-8** |
| 个税 | `earning.tax` | `TaxCalculator` + `FlatTaxCalculator`（`PENDING-OP-23` 占位） | OP-23 |
| 互动 | `interaction` | 弹幕策略 **1 条/秒 · ≤50 字 · 5s 去重** + 手机号门槛 + 禁言 + 敏感词（复用 `40001`） | **AC-4** |
| 账号 | `account` | 年龄判定（18 岁生日边界）、未成年单笔/单日限额 + OP-21 实名前置 | OP-6 / BR-5 / **AC-6** / OP-21 |
| 收益 | `earning` | 提现校验（仅个人、起提 10,000 分、卡名一致）、两税模式到账 | BR-6 / OP-16 |
| 开播 | `live` | 开播前置校验逐项返回 | **AC-1** / BR-2 |
| 建库 | `db/migration/V1__init.sql` | 已实现域 DDL + `sys_config` 全量口径 + 4 类目 + 币池单例 + 未成年默认档 + 最小礼物目录 | S1a P0.4 |

## 增量 2 · 资金/礼物事务 + Spring 装配层 + 持久化（本次）

### 2.1 资金链路领域实现（**AC-5**，零依赖、本地可跑）

| 组件 | 交付内容 | 对应 |
| --- | --- | --- |
| `common.tx` | `UnitOfWork` / `Snapshotable` / `InMemoryUnitOfWork`（异常整体回滚）/ `SpringUnitOfWork`（Spring 事务实现） | S1a §6.3 |
| `asset.ledger` | `WalletAccount`（不可变 + 乐观锁 + 冻结位）、`WalletLedgerEntry`、`EarningAccount`（`total_earned/available/withdrawing`）、`EarningLedgerEntry` | 契约 §5.3 |
| `asset.gift.GiftSendService` | 同一事务：扣币 → 钱包流水 → 主播收益 → 收益流水 → 礼物订单；`idempotency_key` 唯一；重放回吐首次结果 | **AC-5** / 契约 §2.6、§4.6 |
| `asset.recharge.RechargeService` | 下单**费率快照** → 支付回调同事务：置单 PAID + 加币 + 流水 + **币池增量**；回调幂等 | **AC-5** / §3.4.2 |
| `asset.iap.IapVerifyService` | 同一 `transaction_id` 只加币一次；退款/撤销逆向冲减（余额转负 → 冻结打赏，不静默吞掉） | OP-11 / §3.4.7 |
| `earning.WithdrawalService` | 申请（校验 + 可提现 → 提现中）→ 打款（流水 OUT）/ 驳回（退回）；两税模式到账 | BR-6 / OP-16 |
| 测试替身 | `InMemoryStores`（含唯一约束与乐观锁语义）、`FundFixture` | — |

### 2.2 Spring 装配层与持久化

| 组件 | 交付内容 |
| --- | --- |
| `app.MarsLiveApplication` | `@SpringBootApplication` + `@MapperScan` + `@EnableTransactionManagement` |
| `common.web` | `TraceIdFilter`（`X-Trace-Id` 贯穿 + MDC）、`ApiResponseAdvice`（业务异常 → `code` 且 HTTP 状态严格取 `ErrorCode`）、`RequireIdempotencyKey` 拦截器 |
| `common.security` | `JwtTokenService`（HS256，access 2h，无额外依赖）、`JwtAuthFilter`、`AuthContext`、`@RequireRoles` + `RbacInterceptor`（未登录 `40100` / 角色不足 `40300`） |
| `app.config` | `WebConfig`（拦截器/时钟/JWT）、`DomainConfig`（领域服务 → 事务与仓储绑定） |
| 持久化 | MyBatis 注解 Mapper：`wallet_account` / `wallet_ledger` / `earning_account` / `earning_ledger` / `gift` / `gift_order` / `recharge_order` / `coin_pool_*`（乐观锁 `WHERE version=?`、`biz_no`/`idempotency_key` 唯一、币池 upsert） |
| 对外端点 | `POST /api/v1/auth/sms/send`、`POST /api/v1/auth/login/sms`、`GET /api/v1/auth/realname`；`GET /api/v1/wallet`、`GET /api/v1/wallet/ledger`、`POST /api/v1/wallet/recharge`、`GET /api/v1/wallet/orders/{orderNo}`；`GET /api/v1/gifts`、`POST /api/v1/gifts/send`；`POST /api/v1/live/rooms/{id}/danmaku`；`POST /api/v1/live/rooms/{id}/precheck`、`/start`；`POST /webhook/pay/wechat`、`/webhook/pay/alipay` |
| 配置 | `application.yml` + `application-local.yml` + `application-prod.yml`（密钥走环境变量，不入仓；生产 profile 下占位 bean 不装配 → 缺失即启动失败） |
| 镜像 | `deploy/backend.Dockerfile`：构建阶段 `gradle check bootJar`，运行阶段 JRE 17 + 随镜像迁移脚本 |

## 增量 3 · 合规前置：敏感词/禁言接入 + 三方回调验签（本次）

闭合增量 2 登记的两个**上线阻断**缺口。

### 3.1 弹幕敏感词与禁言（AC-4）

| 组件 | 交付内容 |
| --- | --- |
| `interaction.service.SensitiveWordFilter` / `SensitiveWordTrie` | **零依赖前缀树**；归一化 = 小写 + 剔除所有非字母/数字 → 抗「插空格/标点/改大小写」绕过；`firstHit` 返回归一化命中词（仅服务端留痕） |
| `interaction.service.SensitiveWordSource` / `ResourceSensitiveWordSource` / `CompositeSensitiveWordSource` | 多源词表：内置 `sensitive-words.txt` + 运营可配置（`sensitive_word` 表）合并 |
| `interaction.service.SensitiveWordCatalog` | 词表热重建（默认 300s TTL，可配），运营改词**无需发版** |
| `interaction.repo.RoomMuteRepository` + `interaction.mapper.RoomMuteMapper` | 查 `room_mute`（`expire_at IS NULL` = 永久禁言），返回 `40300` |
| `interaction.service.DanmakuGuard` | 把「禁言 / 敏感词 / 手机号」等**外部事实**与 `DanmakuPolicy` 的**业务规则**分层：规则层保持零依赖可纯单测，事实层可独立替换 |
| `db/migration/V2__interaction_sensitive_word.sql` | `sensitive_word` 表 + 基线种子 |

### 3.2 三方回调验签（资金安全）

| 组件 | 交付内容 |
| --- | --- |
| `asset.pay.PemSupport` | 零依赖 PEM 解析（X.509 证书 / RSA 公钥） |
| `asset.pay.wechat.WechatPaySignatureVerifier` | V3 平台证书 **SHA256withRSA** 验签（待签串 `timestamp\nnonce\nbody\n`）+ **5 分钟时间窗防重放** + `resource` **AES-256-GCM 解密** |
| `asset.pay.wechat.WechatPayNotification` | 解密后取 `out_trade_no` / `amount.total` / `trade_state`；**缺字段即拒绝**（不默认 0 元） |
| `asset.pay.alipay.AlipaySignatureVerifier` | **RSA2** 规范串验签（剔除 `sign`/`sign_type`/空值 + 参数名升序）+ `total_amount`（元）→ 分换算 |
| `asset.pay.PayConfig` | 凭据仅经环境变量注入；**未配置时 fail-closed**（能启动，但任何回调一律拒绝） |
| `asset.web.PaymentCallbackController` | 时序改为 **验签 → 状态判定 → 解密/取参 → 金额复核 → 幂等加币**；支付宝端点按规范返回纯文本 `success` |

## 可复跑命令与结果

### A. 完整构建与校验（Gradle Wrapper，推荐 —— 已实测通过）

**仓库已含 Gradle Wrapper**（`backend/gradlew` + `gradle/wrapper/`），因此**无需预先安装 Gradle**：
有 JDK 17+ 即可构建，wrapper 会自行拉取 Gradle 8.10 发行版。

```bash
cd backend
./gradlew check        # compile + JUnit 5 + specCheck
./gradlew bootJar      # 可执行 boot jar
```

> 需要经代理访问外网时，代理通过 Gradle JVM 系统属性传入（**不写入仓库配置**）：
> `./gradlew -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808 check`
> 首次会自动下载 Gradle 发行版与依赖；可用 `GRADLE_USER_HOME` 指定缓存目录。

实测（Gradle 8.10 / JDK 21，`--release 17`）：

```
> Task :compileJava                     ← Spring 装配层与纯领域代码一并编译通过
-- specs: 51, checks: 350, failed specs: 0
RESULT: PASS (all specs green)
> Task :test                             ← JUnit: JwtTokenServiceTest 3 tests / 0 failures / 0 errors
> Task :check
> Task :bootJar
BUILD SUCCESSFUL in 21s
```

`bootJar` 产物：`build/libs/mars-live-backend-0.1.0-SNAPSHOT.jar`（38.9 MB，可执行 fat jar）。

### B. 零依赖直编直跑（无 Gradle 环境；仅覆盖**不依赖 Spring** 的领域核心）

领域核心（含 AC-5 全部 spec）是零依赖纯 Java，`com.mars.testkit` 套件不引用任何三方库。
但自增量 2 起装配层引入 Spring/MyBatis 注解，**`find src/main/java` 全量直编需要三方 jar**；
无 Gradle 时应显式排除装配层文件，或直接使用路径 A。

```bash
cd backend
# 仅领域核心（排除 web / mapper / config / security-filter / app 等装配层）
javac --release 17 -encoding UTF-8 -d build/classes \
  $(find src/main/java/com/mars/{common/money,common/ratelimit,common/idempotency,common/tx,common/support,asset,earning,interaction,account,live} -name '*.java' \
    | grep -v '/web/' | grep -v '/mapper/') \
  $(find src/test/java/com/mars/{testkit,spec,support} -name '*.java')
java -cp build/classes com.mars.testkit.AllSpecs
```

## AC 对照

| AC | 状态 | 落点 / 证据 |
| --- | --- | --- |
| **AC-1** 未实名拦截开播 | ✅ | `PrecheckPolicy` + `LiveController#precheck`（未实名 → `40310`） |
| **AC-2** 推流 5s 内可见 | ⛔ 未实现 | 需 CSS 推流地址签发（密钥）+ 前端推流；`LiveController#start` 明确返回待接入 |
| **AC-3** 首帧 ≤2s / 弱网降清 | ⛔ 未实现 | 播放器侧（前端 + 云厂商） |
| **AC-4** 敏感词拦截 + 限流 + 房管 | ✅ | 限流/去重（增量 1）+ **敏感词词库与禁言查询已接入**（增量 3）；房管处置动作端点仍待增量 4 |
| **AC-5** 余额/流水/收益一致、重放不重复扣费 | ✅ | `FundSpecs` 10 条 spec / 76 断言：三方一致、原子回滚、幂等重放、充值→币池→加币、IAP 幂等与退款冻结、提现锁定/驳回 |
| **AC-6** 未成年超限阻断 | ✅ | `MinorLimitPolicy`（`40320`）+ OP-21 前置（`40350`） |
| **AC-7** 违规内容处置与留痕 | ⛔ 未实现 | 审核域（机审回调 / 审核队列 / 处置留痕）下个增量 |
| **AC-8** 收益与后台对账误差 0 | ✅ | `SettlementCalculator` 以 S1a §3.4.6 示例逐字段复算 + 5 条对账恒等 + 确定性 + 快照不变性 |
| **AC-9** 回放 30 天可播放 | ⛔ 未实现 | `replay_retention_days=30` 已入 `sys_config` |
| **AC-10** 加密传输存储、越权拒绝 | ◐ 部分 | `JwtAuthFilter` + `@RequireRoles` + `RbacInterceptor` 已装配（`40100`/`40300`）；手机号加密列已建；**TLS 与 Redis 令牌黑名单未接入** |

## 已知限制 / 缺口（如实登记，未静默）

1. **~~合规前置缺口（上线阻断）~~ 已于增量 3 闭合**：敏感词词库与禁言查询已接入（`DanmakuGuard`）、三方回调验签已实现（微信 V3 RSA-SHA256 + 时间窗；支付宝 RSA2 + 金额复核）。
   **剩余上线前置项**：① 线上合规词库需由运营导入（内置词表为基线占位）；② 生产必须注入 `MARS_WECHAT_PLATFORM_CERT_PEM` / `MARS_WECHAT_API_V3_KEY` / `MARS_ALIPAY_PUBLIC_KEY_PEM`（缺失即启动失败）；③ 微信回调未做**平台证书序列号轮换**（多证书 `Wechatpay-Serial` 选择）与**对称密钥轮换**；④ 回调未做「同一事件的 `id` 去重表」（当前靠订单状态机幂等，已足够，但事件级去重更稳）。
2. **占位实现（生产 profile 下不装配，缺失即启动失败）**：`DevSmsCodeVerifier`（开发验证码）、`DefaultRechargePackageCatalog`（默认充值档位）、`DefaultViewerRiskProfile`（风控画像，**fail-safe 返回「未实名」**，故打赏会先被 `40350` 拦住）。
3. **端点覆盖**：本轮装配 15 个端点（认证 3 / 钱包 4 / 礼物 2 / 弹幕 1 / 开播 2 / 回调 2），覆盖 **AC-1/4/5/6/8** 的资金与开播主链路；契约其余端点（实名提交、渠道登录、IAP 校验、发票、收益、审核后台、回放等）按同一装配模式在后续增量落地。
4. **未执行运行时验证**：本工作区无 MySQL / Redis 实例，**未启动应用进程**（`bootJar` 产物与 Spring 上下文装配已编译验证，但 `SpringApplication.run` 未实跑）；`V1__init.sql` 未在真实 MySQL 执行；MyBatis Mapper 的 SQL 未连库执行。
5. **多实例状态外移**：`RoomDanmakuPolicies`、`GiftResultCache` 为单机实现；多实例部署时须把限流/去重窗口与幂等结果移到 Redis（契约 §2.6/§2.8）。
6. **增量 2 已修正增量 1 的一处设计问题**：`WithdrawalService` 原先在内存里保存提现单，现改为经 `WithdrawalOrderRepository` 持久化。
7. **个税为占位实现**（`PENDING-OP-23`），非税务结论，上线前必须替换。
