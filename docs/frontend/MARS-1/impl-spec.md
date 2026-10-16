# 前端实现说明（Frontend Implementation Spec）

| 字段 | 值 |
| --- | --- |
| **文档** | MARS-1 · 前端实现说明（瞬间直播一期 MVP 客户端） |
| **创建者** | FrontendDev-d2ca64f5 |
| **创建时间** | 2026-10-16 |
| **版本** | v1.0 |
| **状态** | 待评审（S3a 首次落地） |
| **JIRA / Issue** | MARS-1（`01a13ff4-b31b-78f3-b258-bbe4e9cec269`） |
| **PRD** | 《直播软件产品需求文档-v0.3.md》（Issue 附件） |
| **技术设计** | S1a v0.4 · `https://www.workbuddy.cn/space/d/jwteIofrrUXqsujm22hA7M` |
| **UI / 交互设计** | S1b **v3.2** · `https://workbuddy.link/p/jD5XLPKfhA0MeDpcW87tle`（同时以 Issue 附件 `design-spec.html` 落盘） |
| **API 契约** | S2a **v1.2** · Issue 附件 `api-contract.md` / `openapi.json` |
| **deploy branch** | `release/MARS-1-live-mvp` |
| **功能分支** | `feature/MARS-1-frontend-mvp` |
| **代码位置** | 仓库 `tangkai007/shandian2` → `app/`（Flutter 单 App 双角色） |

> 落地平台约束：工作区未安装 `multica-platform-confluence` / `multica-platform-jira`，
> 本说明以**仓库内文档 + Issue 评论**交付，平台 skill 补齐后可原样发布（不改内容）。

---

## 1. 范围与边界

- 覆盖 S1b v3.2 的**移动端双角色**（主播端 / 观众端）主流程：账号登录与补绑、实名、开播、观看、弹幕互动、礼物打赏、充值、主播收益与结算。
- **不覆盖**：运营 / 审核 / 财务后台（S1a 决策为 `Vue 3 + TS + Element Plus`，属独立前端，本分支不含）。
- **不改需求、不改契约**：任何契约问题回 @BackendDev；本说明只描述「怎么落地」。
- **技术基线**（S1a v0.4 关键技术决策）：`Flutter 单 App 双角色` + 腾讯云 MLVB 原生 SDK 桥接（`TXLivePusher` / `TXLivePlayer`）。

---

## 2. 页面与路由

| # | 页面 | 路由 | 入口 | 对应设计 / 需求 |
| --- | --- | --- | --- | --- |
| 1 | 首页 · 发现流 | `/`（`HomePage`） | App 启动 | D-VIEW-000 · FR-7.2 / OP-12 |
| 2 | 登录 | `/login` | 首页「登录」/ 失败态「去登录」 | D-HOST-000 · FR-1.1 / OP-4 |
| 3 | 补绑手机号 | `/bind-phone` | 登录响 `need_bind_phone=true` / `40325` | D-HOST-000 · DECISION-3/4 |
| 4 | 实名认证 | `/realname` | 开播 `40310` 引导 | D-HOST-001 · FR-1.2 / AC-1 |
| 5 | 开播设置 | `/host` | 首页「主播中心」 | D-HOST-003/005 · FR-2 / AC-1 / AC-2 |
| 6 | 直播间 | `/room/{roomId}` | 首页房间卡片 | D-VIEW-001 · FR-3 / FR-4 / FR-5 |
| 7 | 我的钱包 / 充值 | `/recharge` | 首页「钱包」/ 直播间礼物余额不足 | D-VIEW-003 · FR-5.1 / OP-11 |
| 8 | 主播收益与结算 | `/earnings` | 首页「收益」 | D-HOST-007 · FR-10.2 / AC-8 |

路由注册见 `app/lib/main.dart`（`routes` + `onGenerateRoute` 处理带参 `roomId`）。

---

## 3. 交互与状态（默认 / 加载 / 空 / 异常 / 无权限）

统一由 `AsyncState<T>`（`core/async_state.dart`）+ `AsyncStateView`（`widgets/common.dart`）渲染五态：

| 状态 | 呈现 | 实现 |
| --- | --- | --- |
| 默认 / 加载 | 居中 `CircularProgressIndicator` | `AsyncIdle` / `AsyncLoading` |
| 空 | 文案 + 「刷新」 | `AsyncData` 且集合为空 → `EmptyView` |
| 异常 | 文案 + 「重试」+ 按 **code** 的主操作 | `AsyncFailure` → `FailureView` |
| 无权限 | 同上，主操作 = 跳登录 / 补绑 / 实名 | `ErrorCodeMapper.actionOf(code)` |

关键交互：

- **弹幕双段渲染**：本地先回显（`pending` 灰显 + 转圈）→ 服务端确认后落定；服务端拒绝则**回滚本地回显**并按呈现位置报错。
- **防重复提交**：所有写操作以 `_busy` 禁用按钮；弹幕本地预检（1s/50 字）即时反馈，**服务端仍是唯一事实源**。
- **礼物二次确认**：`40320` 时**替换**为阻断态弹窗，确认按钮不渲染，仅「前往监护人验证」/「取消」（S1b §09 呈现位置）。
- **提现**：低于起提额时本地禁用提交（阈值取服务端 `withdrawable_min_amount`），服务端 `40340` 时行内渲染（金额来自服务端参数）。

---

## 4. 组件映射

| UI 区域 | 组件 | 复用 / 新建 |
| --- | --- | --- |
| 发现流 · 排序/来源切换 | `SegmentedButton` + `DropdownButton`（类目） | 新建（`HomePage`） |
| 直播间 · 播放器 | `_PlayerSurface`（占位） | 新建；真实实现 = `TXLivePlayer` 原生桥接（RISK-7） |
| 直播间 · 公屏 | `ListView` + `DanmakuEntry` | 新建 |
| 直播间 · 礼物面板 | `showModalBottomSheet` + `GiftItem` | 新建 |
| 通用五态 | `AsyncStateView` / `FailureView` / `EmptyView` / `LoadingView` | 新建（跨页复用） |
| 行内错误 | `InlineErrorText` | 新建（跨页复用） |
| Toast | `showErrorSnackBar`（2.5s） | 新建（对应 `50000` / 未收录码兜底） |
| 品牌色 | `colorSchemeSeed = brand-700 #C81E45`（S1b §2.1 文字色） | 新建 |

---

## 5. API 对接

调用链：`页面 → Repository → ApiClient → Transport`。
`MockTransport` 与 `DioTransport` 共用同一条 `ApiClient` 代码路径（mock → 真实只换实现）。

| 页面 / 场景 | API-ID | 调用时机 | 错误 UX |
| --- | --- | --- | --- |
| 登录 | API-AUTH-01/02/03/04 | 点击登录 / 获取验证码 | `40001` 行内；`42900` 行内 |
| 补绑手机号 | API-AUTH-01（`scene=BIND`）/ 08 | 进入补绑页 / 提交 | `40001` 行内 |
| 实名 | API-AUTH-10/11 | 提交 / 查询 | `ID_MISMATCH` 详情展示、不参与分支 |
| 首页发现流 | API-DISC-01/02 | 首屏 / 切 tab / 切类目 | **匿名 → `40100` 跳登录；已登录未绑 → `40325` 引导补绑** |
| 关注 | API-SOC-01/02 | 点关注 | `40100` / `40325` |
| 开播设置 | API-LIVE-01/02/03/04 | 保存并校验 → 开始直播 | `40310` 行内 + 禁用按钮；`40001` 行内 |
| 直播间 | API-PLAY-01/02/03/04 | 进入 / 心跳 | `40330` 整页空态 + 返回 |
| 弹幕 | API-INT-01/02 | 发送 | `42900` 行内；`40001`（超长 / 敏感词）行内；`40325` 引导补绑；`deduped` 静默回滚 |
| 点赞 | API-INT-03 | 点击 | Toast |
| 礼物 | API-ASSET-01/02 | 打开面板 / 确认赠送 | `40320` 阻断弹窗；`40350` 行内；`40001(balance)` 行内 |
| 充值（非 iOS） | API-ASSET-03/05/06 | 选择套餐 + 渠道 | `40001` 行内 |
| 充值（iOS） | API-ASSET-07/08 | 购买 | `40345` Toast / 行内 |
| 收益与结算 | API-EARN-03/05/06 | 进入 / 提交提现 | `40340` 行内 + 禁用提交 |

**契约口径落点（逐条）**：

| 口径 | 落点 |
| --- | --- |
| 弹幕 **1 条/秒 · ≤50 字 · 5s 去重** | `core/danmaku_limits.dart` + `data/live_repository.dart#sendDanmaku` + `MockTransport#_danmaku` |
| `40100` / `40325` 边界 | `core/error_codes.dart#ErrorCodeMapper` + `core/auth_gate.dart` |
| **iOS 仅 IAP** | `core/payment_policy.dart` + `data/asset_repository.dart#createRechargeOrder`（iOS 直接拒绝） |
| **三渠道强制补绑手机号** | `core/auth_gate.dart#afterLogin` + `features/auth/bind_phone_page.dart` |
| 结算字段名以契约 v1.2 为准 | `models/earning.dart#SettlementBill` / `SettlementLabels` |
| 金额一律整数分、禁止浮点 | `models/*`（`*_fen` / `*_amount` 全为 `int`）+ `core/formatters.dart` |
| 幂等（资金类） | `core/idempotency.dart` + `ApiClient` 注入 `Idempotency-Key` |

---

## 6. 表单与校验

| 表单 | 规则 | 提示文案 / 来源 | 对应 |
| --- | --- | --- | --- |
| 手机号 / 验证码 | 手机号必填；验证码 6 位数字 | 服务端 `40001` 行内 | FR-1.1 |
| 补绑手机号 | **不可跳过**（无返回按钮） | 「需绑定手机号后使用该功能」（`40325`） | BR-1 / OP-14 |
| 实名 | 姓名 + 证件号 + 出生日期（`YYYY-MM-DD`） | 服务端 `ID_MISMATCH` 详情展示 | FR-1.2 / AC-1 |
| 开播标题 | ≤30 字 | 服务端 `TITLE_AUDIT` 结果 | FR-2.2 |
| 开播类目 | 一期 4 类白名单（才艺/游戏/闲聊/生活） | 服务端 `40001(field=category_id)` | OP-7 / BR-2 |
| 弹幕内容 | ≤50 字（`maxLength` 取自服务端限流配置） | 本地即时 + 服务端 `40001(field=content)` | FR-4.1 / AC-4 |
| 提现金额 | ≥ `withdrawable_min_amount` 且 ≤ `available_amount`；**元输入 → 分换算**只此一处 | `40340` 行内，金额来自服务端参数 | BR-6 / OP-16 / AC-8 |

> **不硬编码金额与阈值**：所有涉及金额的文案使用占位符，由运行期参数填充
> （`core/ui_messages.dart#UiMessages.of(params: ...)`）。

---

## 7. 体验与无障碍

- 全站字号最小 11px；文字色对比度按 S1b §2.1 的 `★` 文字色档（`brand-700` 等），装饰色不承载文本。
- 主流程键盘可操作：表单 `TextField` + `onSubmitted` 提交弹幕；按钮均为原生可聚焦控件。
- 失败态 Toast 时长 2.5s（`50000` 兜底）。
- 触控目标使用 Material 3 默认尺寸（≥48dp）。

---

## 8. Mock 策略

- **位置**：`app/lib/data/mock_transport.dart`（`MockTransport implements Transport`）。
- **切换条件**：`AppServices(transport: ...)`；默认注入 `MockTransport`，接入真实网关时改注入 `DioTransport(baseUrl: ...)`。
- **不是"永远返回成功"的桩**：它按契约实现关键校验，使错误码分支与限流口径在无后端时即可验证 —— 匿名 `40100`、未绑 `40325`、超长 `40001(field=content)`、超频 `42900`、5s `deduped`、敏感词 `40001(reason=敏感词)`、iOS 现金下单 `40001`、未成年超限 `40320`、提现下限 `40340`、IAP 验签 `40345`。
- **时钟可注入**：`MockTransport(clock: ...)` → 限流 / 去重窗口可被测试确定性驱动。
- **未 mock 的原生能力**：`TXLivePusher` / `TXLivePlayer` / StoreKit 2 —— 见「已知问题」。

---

## 9. 验证计划

| 层 | 命令 | 覆盖 |
| --- | --- | --- |
| 静态分析 | `flutter analyze` | 全量 `lib/` + `test/` |
| 单测 / 组件测试 | `flutter test` | 错误码分支、文案、登录门禁、支付策略、弹幕全链路、结算字段、AC-8 基线、IAP、格式化、组件冒烟 |

用例 ↔ AC 追溯：

| 测试文件 | 覆盖 |
| --- | --- |
| `test/error_code_branching_test.dart` | `40100`/`40325` 边界、全部错误码落点（防漂移） |
| `test/auth_gate_test.dart` | 三渠道统一强制补绑（DECISION-3/4） |
| `test/danmaku_flow_test.dart` | **AC-4** 弹幕 1 条/秒 · ≤50 字 · 5s 去重 · 敏感词 |
| `test/settlement_and_earning_test.dart` | **AC-8** 结算字段与示例值、渠道闭合、提现 `40340` / 两种 `anchor_tax_mode` |
| `test/asset_iap_test.dart` | **AC-5** 三方一致、**AC-6** `40320`、iOS 仅 IAP、`40345` |
| `test/api_client_test.dart` | 统一封装、鉴权、`40100` 静默刷新轮换、幂等头 |
| `test/payment_policy_test.dart` | iOS 仅 IAP |
| `test/formatters_test.dart` | 整数分资金展示（无浮点） |
| `test/widget_smoke_test.dart` | 首页 / 补绑页渲染、匿名关注流失败态主操作 |

---

## 10. 已知问题 / 风险

1. **本运行时无 Flutter / Dart 工具链** —— `flutter analyze` 与 `flutter test` **未在本机执行**，`lib/` 与 `test/` 均为**未跑通编译与用例的源码**。需在具备 Flutter SDK 的 CI（G2.5）或本地环境复跑，输出为准。**这是本轮最大交付风险，不属「检查通过」**。
2. **RISK-7（Flutter × 腾讯云 MLVB 桥接）** —— `TXLivePusher` / `TXLivePlayer` / StoreKit 2 原生桥接未接入；播放器与推流为占位实现 + mock 播放地址。P0 真机 spike 未通过前不引入原生依赖（否则脚手架无法构建）。
3. **令牌持久化** —— `TokenStore` 目前为内存态；`flutter_secure_storage` 落盘随 P0 spike 一并补齐。
4. **弹幕「本地预检」的定位** —— 前端不缓存、不另立限流参数；`DanmakuLimits.contractDefaults` 仅为服务端配置未到达时的首屏兜底展示，**每次发送仍以服务端响应为准**。
5. **后台（运营/审核/财务）前端不在本分支** —— S1a 决策为 `Vue 3 + TS + Element Plus`，需独立派单。
6. **`openapi.json` 未全量定型 `data`**（契约 §9-2 已定结论：以 `api-contract.md` §4 为响应体权威）—— 本实现按 §4 取 payload。

---

## 11. 修订记录

| 日期 | 版本 | 作者 | 变更 |
| --- | --- | --- | --- |
| 2026-10-16 | v1.0 | FrontendDev-d2ca64f5 | 首版：S3a 前端实现说明。Flutter 单 App 双角色脚手架 + 8 个页面/路由 + 统一 API 层（mock 先行）+ 契约口径逐条落地（弹幕限流、`40100`/`40325` 边界、iOS 仅 IAP、三渠道强制补绑、结算字段名）+ 9 个测试文件。**注明运行时无 Flutter 工具链，未跑通编译与用例**。 |
