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

## 增量 4 · 审核域（AC-7）+ 回放（AC-9）+ 后台审计/口径（本次）

闭合增量 3 登记的 AC-7 / AC-9 缺口，并补齐后台 `API-ADM-20` / `API-ADM-29`。

### 4.1 审核域（**AC-7** 全链路）

| 组件 | 交付内容 | 对应 |
| --- | --- | --- |
| `moderation.ModerationQueueService` | 机审回调 **入队 + 幂等**（`event_id` 唯一，同事件重复投递只入队一次并回吐首结果）；置信度分流：`≥85 → HIGH`、`≥40 → MEDIUM`、否则 `LOW`；队列按风险升序（HIGH 在前）+ 入队时间升序 | AC-7 / 契约 §4.10、§API-CB-03/04 |
| `moderation.ModerationActionService` | 处置动作 `WARN/CUT/BAN/REPORT/MARK_OK`：`MARK_OK` **只关单不留痕**（仍写审计）；其余动作 **同事务** 落 `moderation_action` + `moderation_evidence` 证据快照 + 审计日志 + 关单；校验 `remark ≥5 字`、非 `MARK_OK` 必填 `reason_code`（否则 `40001`） | AC-7 / API-ADM-08/09 |
| `moderation.ModerationEvidence` | 证据快照：`snapshot_at`（处置时刻）、`rule_id`、`confidence`、`operator_name`、`evidence_urls`、`appeal_window_days` —— **不可变、不可删**，供申诉/举证 | AC-7 全量留痕 |
| `moderation.ReportService` | 举报：上报入队（`PENDING`）→ 受理 `ACCEPT` / 驳回 `REJECT`；**终态防重**（重复处置 → `40900`） | AC-7 / API-INT-09、API-ADM-10/11 |
| `moderation.AppealService` | 申诉：一处置**仅可申诉一次**（重复 → `40900`）；时限 **取后台配置 `appeal.window_days`（默认 7，改口径即生效，不硬编码）**；采纳 `ACCEPT` → 解除处置（`disposition_effective=false`）；驳回 `REJECT` | AC-7 / API-ADM-12/13 |
| `moderation.SysConfigService` / `SysConfigCatalog` / `PlatformConfig` | 口径目录 **17 项**（读/写 `API-ADM-20`）；未知键 → `40330`；`PlatformConfig` 从口径取 `appeal_days` / `replay_days`（缺省 7 / 30） | AC-7 / AC-9 / API-ADM-20 |
| `moderation.AuditRecorder` | 后台操作**全量留痕**（`admin_operation_log`）：操作人 / 角色 / 动作 / 目标 / 原因码 / 备注 / 证据 ID / IP / 时间；**显式调用**（非 AOP，见已知限制 ⑧） | API-ADM-29 |
| `moderation.ModerationReasonCodes` | 处置原因码目录（供 `API-ADM-07 /admin/moderation/reason-codes`） | 契约 §API-ADM-07 |
| `common.support.JsonLists` | `List<String>` ↔ 数据库 JSON/CSV 列互转（MyBatis 无内建 `List` 类型处理器，用中间行记录 + 本工具解析） | 持久化适配 |

### 4.2 回放（**AC-9**）

| 组件 | 交付内容 | 对应 |
| --- | --- | --- |
| `moderation.ReplayService` | 录制完成事件（`RecordingCompletedEvent`）→ 建回放记录，**留存取口径 `replay_retention_days`（默认 30）**；`vod_file_id` 幂等（重复事件只存一条）；播放地址 `hls` + 过期时间 | AC-9 / API-REP-01/02 |
| 过期语义 | 超留存期 → `EXPIRED`，取播放地址 → `40330`；不存在 / 已删 → `40330` | AC-9 |

### 4.3 后台 / 对外端点（与契约路径逐一对照，见 §4.4）

| 端点 | 类 | 角色 | 说明 |
| --- | --- | --- | --- |
| `POST /webhook/css/image-audit`、`/webhook/cms/callback`、`/webhook/css/record` | `ModerationWebhookController` | 无（`/webhook/**` 绕过 RBAC/幂等） | 机审回调 / 录制完成回调 |
| `GET /api/v1/admin/moderation/tasks`、`POST .../tasks/{taskId}/claim`、`POST .../actions`、`GET .../evidence/{actionId}` | `AdminModerationController` | `AUDITOR` | 审核队列 / 认领 / 处置 / 证据 |
| `GET /api/v1/admin/moderation/reason-codes` | `AdminModerationController` | `OPS`/`AUDITOR` | 原因码目录 |
| `GET /api/v1/admin/reports`、`POST .../reports/{reportId}/handle` | `AdminReportController` | `OPS`/`AUDITOR` | 举报列表 / 处置 |
| `GET /api/v1/admin/appeals`、`POST .../appeals/{appealId}/handle` | `AdminAppealController` | `OPS` | 申诉列表 / 处置 |
| `POST /api/v1/admin/rooms/{roomId}/force-stop` | `AdminRoomController` | `OPS`/`AUDITOR` | 强制关播（必填原因码，`CUT` 留痕，未知房间 `40330`） |
| `GET /api/v1/admin/sys-config`、`PUT .../sys-config/{key}` | `AdminSysConfigController` | `OPS` | 口径读（分页 size 默认 50）/ 写（单条） |
| `GET /api/v1/admin/audit-logs` | `AdminAuditLogController` | `OPS`/`FINANCE` | 审计日志（关键字过滤，分页 size 默认 50） |
| `GET /api/v1/live/rooms/{roomId}/replays`、`GET /api/v1/replays/{replayId}/play-url` | `ReplayController` | 公开（契约标 公开/用户，无 `@RequireRoles` → 匿名可访问） | 回放列表 / 播放地址 |
| `POST /api/v1/live/rooms/{roomId}/report` | `RoomReportController` | `VIEWER`/`ANCHOR` | 用户举报房间 |

### 4.4 与 S2a v1.2 契约及 @FrontendDev admin 前端路径对照（Leader 要求）

**结论：本轮实现的全部路径与 S2a v1.2 契约一致，且与 `admin/src/api/endpoints.ts`（前端 `baseURL=/api/v1`）逐条吻合；字段形状与 `admin/src/types/contract.ts` 一致。** 发现 **4 处偏差**（前端当前走 mock）：

| # | 偏差 | 前端 mock | 后端（按契约） | 处理 |
| --- | --- | --- | --- | --- |
| a | `POST .../tasks/{id}/claim` 冲突模型 | **房间级锁**（`AdminRoomRow.locked_by`，房间被别人锁则 `40900`，随后锁房） | **任务级原子认领**（`PENDING→CLAIMED`，已认领/已完结 → `40900`）；不持久化 `locked_by` | 同为 `40900`，触发实体不同；ADM-04 房间列表（增量 5）才会消费 `locked_by`，届时对齐 |
| b | `GET .../evidence/{actionId}` | mock 硬编码 `rule_id='AV-0031'` / `confidence=92` | 返回**真实快照**（强制关播 `CUT` 为 `""`/`0`；处置则取任务 `rule_id`/`confidence`） | mock 为桩，以后端真实快照为准 |
| c | `GET /admin/reports` 种子 | mock 种一条 `DANMAKU` 目标行 | 用户端 `API-INT-09` 只落 `ROOM`；后台 `target_type` 按不透明字符串存取 | 无契约违背（`target_type` 契约 union 含 `DANMAKU`，可扩展） |
| d | 未实现端点 | mock 已实现 ADM-01…04 / 14…19 / 21…28、会话登录等 | **未实现**（增量 5） | 见下方增量 5 清单 |

> 与契约一致性依据：`docs/backend/MARS-1/api-contract.md`（§API-CB-02/03/04、§API-ADM-05…13、§API-ADM-20、§API-ADM-29、§API-INT-09、§API-REP-01/02）。

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
-- specs: 63, checks: 447, failed specs: 0
RESULT: PASS (all specs green)
> Task :test                             ← JUnit: JwtTokenServiceTest 3 tests / 0 failures / 0 errors
> Task :check
> Task :bootJar
BUILD SUCCESSFUL in 24s
```

`bootJar` 产物：`build/libs/mars-live-backend-0.1.0-SNAPSHOT.jar`（39.0 MB，可执行 fat jar）。

> 增量 4 新增 `ModerationSpecs` 12 条 spec / 97 断言（审核队列分流与幂等、处置全链路留痕+证据快照、`MARK_OK` 语义、原因码与备注校验、举报受理/驳回/终态防重、申诉时限取配置与一处置一申诉、强制关播、回放留存/幂等/过期、口径目录 17 项与审计过滤）。

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
| **AC-4** 敏感词拦截 + 限流 + 房管 | ✅ | 限流/去重（增量 1）+ 敏感词词库与禁言查询（增量 3）+ **房管处置动作端点已接入**（增量 4：`/admin/moderation/*`、强制关播） |
| **AC-5** 余额/流水/收益一致、重放不重复扣费 | ✅ | `FundSpecs` 10 条 spec / 76 断言：三方一致、原子回滚、幂等重放、充值→币池→加币、IAP 幂等与退款冻结、提现锁定/驳回 |
| **AC-6** 未成年超限阻断 | ✅ | `MinorLimitPolicy`（`40320`）+ OP-21 前置（`40350`） |
| **AC-7** 违规内容处置与留痕 | ✅ | 审核域全链路（增量 4）：`ModerationSpecs` 12 条 spec / 97 断言 —— 机审回调入队分流+幂等、队列风险排序、处置全链路（留痕+证据快照+申诉时限取配置）、`MARK_OK` 语义、原因码/备注校验、举报受理/驳回/终态防重、申诉一处置一次、强制关播、审计留痕 |
| **AC-8** 收益与后台对账误差 0 | ✅ | `SettlementCalculator` 以 S1a §3.4.6 示例逐字段复算 + 5 条对账恒等 + 确定性 + 快照不变性 |
| **AC-9** 回放 30 天可播放 | ✅ | `ReplayService`（增量 4）：留存取口径 `replay_retention_days`（默认 30）、`vod_file_id` 幂等、播放地址 `hls`、过期/不存在 → `40330` |
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
8. **`API-ADM-29` 审计用显式 `AuditRecorder` 调用，非 AOP**：工程当前**未引入 `spring-boot-starter-aop`**，故未做注解切面，改由各处置/配置服务在同事务内**显式记录**（`admin_operation_log`）。契约对「写审计」是行为要求，未规定实现方式；后续若引入 AOP 可平滑收敛。
9. **审计「操作人」落 `userId`（字符串），无昵称**：`JwtClaims` **不含 `name`/`nickname` 声明**（仅 `userId`/`roles`/时间），故 `AdminActor.name()` 取 `String.valueOf(userId)`；前端 `contract.ts` 的 `operator_name` 需要真实昵称时，须在 JWT 或用户查询层补昵称来源（增量 5 与用户管理一并处理）。
10. **机审回调字段映射为占位**：`ModerationWebhookController` 解析 CSS 回调字段（`eventId`/`confidence`/`ruleId`/`snapshotUrl` 等）按 S2a §4.10 语义实现，但**未连真实 CSS 回调样本**比对（无外网沙箱）；接入联调前需以真实报文校准字段名与嵌套层级。
11. **`/webhook/**` 有意绕过 RBAC/幂等**：`RbacInterceptor` / `RequireIdempotencyKey` 只注册在 `/api/**`；回调安全由上表 §3.2 的**验签**保证（机审回调当前无验签，见 10）。
12. **增量 5 待办（与前端 mock 已实现的端点差集）**：`API-ADM-01/02/03`（用户）、`API-ADM-04`（房间列表，届时消费 `locked_by`）、`API-ADM-14/15/16`（礼物）、`API-ADM-17/18`（类目）、`API-ADM-19`（运营位）、`API-ADM-21…28`（财务：对账/提现审核/发票/收益）、实名提交真实化、渠道登录、IAP 校验端点、会话 refresh/logout。
