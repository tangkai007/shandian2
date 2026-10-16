# 后台实现说明（Admin Web Implementation Spec）

| 字段 | 值 |
| --- | --- |
| **文档** | MARS-1 · 后台 Web 实现说明（Vue 3 + TS + Element Plus） |
| **创建者** | FrontendDev-d2ca64f5 |
| **创建时间** | 2026-10-16 |
| **版本** | v1.0 |
| **状态** | 待评审（S3a 增量 2） |
| **JIRA / Issue** | MARS-1（`01a13ff4-b31b-78f3-b258-bbe4e9cec269`） |
| **PRD** | 《直播软件产品需求文档-v0.3.md》（Issue 附件） |
| **技术设计** | S1a v0.4 · `https://www.workbuddy.cn/space/d/jwteIofrrUXqsujm22hA7M` |
| **UI / 交互设计** | S1b **v3.2** · `https://workbuddy.link/p/jD5XLPKfhA0MeDpcW87tle`（后台帧 D-ADM-000/001/002/003） |
| **API 契约** | S2a **v1.2** · §3.9 端点清单 + §4.9 后台接口要点（`API-ADM-01…29`） |
| **deploy branch** | `release/MARS-1-live-mvp` |
| **功能分支** | `feature/MARS-1-admin-web` |
| **代码位置** | 仓库 `tangkai007/shandian2` → `admin/` |

> 平台约束：工作区未安装 `multica-platform-confluence` / `multica-platform-jira`，本说明以**仓库内文档 + Issue 评论**交付。

---

## 1. 范围

覆盖 S1b v3.2 **后台 4 帧**与契约 §3.9 的**全部 29 个 `API-ADM` 端点**：

| 帧 | 模块 | 对应 API |
| --- | --- | --- |
| **D-ADM-000** 后台框架与角色导航 | 登录 / 侧栏 / 顶栏 / 权限导航 / 口径配置 / 审计 | API-ADM-14…20、29 |
| **D-ADM-001** 审核工作台（实时巡房 + 队列） | 房间列表（风险分排序）· 实时预览 · AI 告警时间线 · 处置区 | API-ADM-04、06、07、10、11 |
| **D-ADM-002** 处置确认与留痕 | 二次确认 · 原因码 · 备注 · 通知主播 · 证据块 · 申诉 7 天 | API-ADM-05、08、09、12、13 |
| **D-ADM-003** 财务对账与提现审核 | 提现队列 · 对账阶梯 ①…⑦ · 差异冻结 · 电子发票 | API-ADM-21…28 |

**不在本轮**：主播端 / 观众端 App（已由 `app/` 分支交付）、CI/CD（@DevOps · G2.5）。

---

## 2. 页面与路由

| # | 页面 | 路由 | 模块权限 | 设计 / 需求 |
| --- | --- | --- | --- | --- |
| 1 | 登录 | `/login` | 公开 | D-ADM-000 · FR-1.5 |
| 2 | 落地跳转 | `/` | 已登录 | D-ADM-000 |
| 3 | 审核工作台 | `/moderation` | auditor | D-ADM-001 · FR-9.1/9.2/9.4 · AC-7 |
| 4 | 直播间管理 | `/rooms` | ops · auditor | API-ADM-04/05 · FR-11.1 |
| 5 | 举报处理 | `/reports` | auditor · ops | API-ADM-10/11 · FR-9.4 |
| 6 | 申诉与留痕 | `/appeals` | ops | API-ADM-12/13 · BR-8 · OP-17 |
| 7 | 用户管理 | `/users` | ops | API-ADM-01/02/03 · FR-11.1 |
| 8 | 礼物与资产 | `/gifts` | ops | API-ADM-14/15/16 · FR-5.2 |
| 9 | 类目与运营位 | `/config` | ops | API-ADM-17/18/19 · FR-11.2 · OP-7 |
| 10 | 口径配置 | `/sys-config` | ops | API-ADM-20 · S1b §11.3 |
| 11 | 财务对账与提现审核 | `/finance` | finance | D-ADM-003 · FR-11.3 · AC-8 |
| 12 | 操作审计日志 | `/audit-logs` | ops · finance | API-ADM-29 · FR-11.4 |
| 13 | 整页 403 | `/403` | 公开 | D-ADM-000 无权限态 |
| 14 | 整页 404 | `*` | 公开 | S1b §8.1 |

---

## 3. 交互与状态（六态逐格）

| 页面 | 正常 | 加载 | 空 | 错误 | 禁用 / 无权限 |
| --- | --- | --- | --- | --- | --- |
| 后台框架 | 侧栏 186px（可折叠 56px）+ 顶栏（运营主体 + 资质编号 + 非生产红色角标） | 侧栏/顶栏骨架 | 不适用 | 会话失效 → 清令牌 + 跳登录（`40100`） | 无权限模块不渲染入口；URL 直达 → 整页 403 |
| 审核台 | 三栏（1280px 及以下降为两栏） | 房间列表与时间线各自骨架；**预览失败不阻塞处置区** | 队列为空 → 「当前无待审内容」+ 上次刷新时间；**巡房列表不发空态** | 预览加载失败 → 可继续处置 / 重试 | 房间被他人锁定 → 处置按钮禁用 + 「lin 正在处理该房间」；非审核角色 → 403 |
| 处置 | 二次确认弹窗 + 证据块 | 处置中（按钮禁 3s，显示「处置中…」） | 不适用 | 提交失败 → **弹窗保留输入** + Toast 2.5s | 原因码未选 / 备注 < 5 字 → 行内错误 |
| 财务 | 提现队列 + 对账阶梯 + 差异 | 表格骨架 + 计算中 | 「本月暂无待审提现」 | 对账差异 ≠ 0 → 整块标红 + **冻结出款**；起提失败 → `40340` 行内（金额来自服务端） | 非财务角色 → 整页 403 且不显示金额 |

实现落点：`components/PageState.vue`（加载/空/错误）+ 各视图内联的分支；`stores/session.ts#can` + `router` 守卫负责无权限。

---

## 4. 组件映射

| UI 区域 | 组件 / 文件 | 复用 / 新建 |
| --- | --- | --- |
| 侧栏 + 顶栏 + 环境角标 | `layouts/AdminLayout.vue` | 新建 |
| 通用六态 | `components/PageState.vue` | 新建（跨页复用） |
| 金额（千分位 + tabular-nums） | `components/MoneyText.vue` + `domain/money.ts` | 新建（跨页复用） |
| 处置确认弹窗 | `components/DispositionDialog.vue` | 新建 |
| 房间列表 / 告警时间线 / 处置区 | `views/ModerationWorkbenchView.vue` | 新建 |
| 对账阶梯 ①…⑦ | `domain/finance.ts#settlementLines` + `views/FinanceView.vue` | 新建 |
| 设计 token | `styles/tokens.css`（1:1 落地 S1b §2，禁魔法色值） | 新建 |

---

## 5. API 对接

| 页面 / 场景 | API-ID | 错误 UX |
| --- | --- | --- |
| 会话 | 会话入口 / profile | `40100` → 清令牌 + 跳登录 |
| 用户管理 | API-ADM-01/02/03 | `40001(field=reason_code)` 行内 |
| 直播间管理 | API-ADM-04/05 | `40001` 行内 + 二次确认 |
| 审核工作台 | API-ADM-06/07/08/09 | `40900` 冲突态；`40001(field=remark)` 行内 |
| 举报处理 | API-ADM-10/11 | 驳回必填原因码 |
| 申诉与留痕 | API-ADM-12/13 | 受理才解除处置 |
| 礼物与资产 | API-ADM-14/15/16 | `40001` Toast |
| 类目与运营位 | API-ADM-17/18/19 | 受限类目开放 → `40001(field=enabled)` |
| 口径配置 | API-ADM-20 | 金额用整数分解析（`yuanTextToFen`） |
| 财务对账 | API-ADM-21/22/23/27 | 同期重复出账 → `40900`；差异 ≠ 0 → 冻结出款 |
| 提现审核 | API-ADM-24/25/26 | `40340`（金额由响应体注入文案）、`40001(field=bank_account_id)` |
| 电子发票 | API-ADM-28 | 开具后回传 `invoice_url` |
| 审计日志 | API-ADM-29 | 只读 |

**契约口径落点**：

| 口径 | 落点 |
| --- | --- |
| `④=①−②−③`、`⑤+⑤=④`、`⑦=⑤−⑥`；差异 ≠ 0 冻结出款 | `domain/finance.ts#reconcile` + `views/FinanceView.vue` |
| A 域字段名与界面标签逐字一致 | `domain/finance.ts#SETTLEMENT_LABELS` / `settlementLines` |
| 提现列随 `anchor_tax_mode` 变化（SETTLE 收起「代扣个税」列，NIT-2） | `domain/finance.ts#withdrawalColumns` / `taxModeBadge` |
| 不可逆动作二次确认 + 必填原因码 + 全量留痕 | `domain/moderation.ts` + `components/DispositionDialog.vue` |
| 备注 < 5 字 → `40001` | `domain/moderation.ts#validateDisposition` + mock |
| 申诉时限来自配置（**不硬编码 7 天**） | `domain/moderation.ts#appealDeadlineAt(days)` |
| 金额一律整数分、无浮点 | `domain/money.ts`（`fenToYuan` / `yuanTextToFen`） |
| 金额 / 阈值文案不硬编码 | `domain/messages.ts#messageFor(params)` |

---

## 6. 表单与校验

| 表单 | 规则 | 提示 | 依据 |
| --- | --- | --- | --- |
| 处置（D-ADM-002） | 动作必选；不可逆动作需二次确认；原因码必选（`MARK_OK` 除外）；备注 ≥ 5 字 | 行内错误，紧贴字段 | S1b §01④ · 契约 §4.9 |
| 强制关播 | 原因码必选 + 备注 ≥ 5 字 + 二次确认 | 行内 + 不可逆后果明示 | API-ADM-05 |
| 封禁账号 | 原因码必选 | 行内 | API-ADM-02 |
| 驳回举报 / 驳回提现 | 原因码必选 | 行内 | D-ADM-003 |
| 口径配置 · 金额 | `fen` 类型以「元」输入 → **整数解析**转分 | 「金额格式不合法」 | S1b §11.3 |
| 类目开放 | 受限类目开关禁用（电商/教育/医疗/金融） | 行内 + 文案说明 | OP-7 / BR-2 |

---

## 7. 体验与无障碍

- 文字色一律取 §2.1 的 `-700` 档；`brand-500/600`、`gold-500`、`success`、`warning`、`n-400` 不承载文字（token 内已注释）。
- 后台可点元素热区 ≥ 28px（`--adm-hit-target`）；最小正文字号 11px。
- 高危动作**颜色 + 图标 + 文案三重编码**（不断流仅靠颜色区分）。
- 金额一律 tabular-nums + 千分位 + 单位（法币 `￥`、虚拟币「币」）。
- 反馈时长：成功 1.5s / 失败 2.5s（§8.1）；失败文案三段式（发生了什么 → 为什么 → 怎么办）。
- 响应式：≤1440px 内容区自适应；1280px 下审核三栏降为两栏（`AdminLayout` 断点 + `el-col` 响应式列）。

---

## 8. Mock 策略

- 位置：`src/api/mock.ts`（内存后端 + 路由表）；`src/api/client.ts` 的 `USE_MOCK` 开关切换。
- 能力：按契约实现 40100 / 40300 / 40001(field) / 40900 / 40340 / 50000 与**配置驱动**口径（`anchor_tax_mode`、起提金额、申诉时限、税率）。
- 用途：无后端环境下验证**错误码分支**与展示口径，并作为 `tests/api.spec.ts` 的端到端夹具。
- 退出：`VITE_USE_MOCK=false`。

---

## 9. 验证计划与实跑证据

| 命令 | 结果 |
| --- | --- |
| `npm ci` | `added 129 packages in 17s` · rc=0 |
| `npm run build`（`vue-tsc --noEmit && vite build`） | `✓ built in 5.31s` · rc=0 · 0 type error |
| `npm test`（`vitest run`） | **6 files / 65 tests passed** · rc=0 |

用例 ↔ AC 追溯：

| 测试文件 | 用例数 | 覆盖 |
| --- | --- | --- |
| `tests/api.spec.ts` | 21 | 鉴权与 RBAC（40100/40300）、处置链路（40001/40900 + 审计留痕）、财务（40340/40001/对账冻结/月结幂等）、配置驱动口径（SETTLE↔WITHDRAW、受限类目）、`request()` 的 `ApiError` 文案占位符 |
| `tests/finance.spec.ts` | 14 | **AC-8** 阶梯/标签/三条等式/渠道闭合、差异 → 冻结、提现列随模式变化（NIT-2）、驳回必填原因码、起提校验 |
| `tests/moderation.spec.ts` | 11 | **AC-7** 不可逆动作、备注 ≥ 5 字、原因码必选、二次确认摘要、风险分排序、冲突态、申诉截止随配置 |
| `tests/rbac.spec.ts` | 7 | 三角色权限矩阵、导航只含有权限模块、落地页不撞 403、模块↔API-ID 追溯 |
| `tests/messages.spec.ts` | 7 | 40100/40300 三段式文案、40340 金额不硬编码、未收录码兜底、反馈时长 |
| `tests/money.spec.ts` | 5 | 整数分格式化、千分位、元→分整数解析 |

---

## 10. 已知问题 / 风险

1. **`index` chunk 1.15 MB（gzip 380 KB）** —— Element Plus 全量引入所致。构建有 >500KB 警告但不失败；后续可用 `unplugin-vue-components` 按需引入优化（不阻塞功能）。
2. **登录为角色入口（Mock）** —— 真实环境需接企业 SSO / RBAC 网关注入会话；本项目内角色矩阵已实现，令牌来源待接入。
3. **Mock 的处置原因码为演示字典** —— 真实原因码应由后台字典维护（本实现已走 `GET /admin/moderation/reason-codes`，不硬编码在前端）。
4. **`D-ADM-002` 通知渠道**实际投递能力依赖后端站内信 / Push / 短信通道，前端仅提交渠道选择。
5. **导出 CSV 为前端生成** —— 大数据量下（>1 万行）应由后端出文件；当前按一页队列导出。
6. **`app/`（Flutter）侧仍缺编译级证据** —— 见 `docs/frontend/MARS-1/impl-spec.md` §10，环境缺 Flutter SDK 未变。

---

## 11. 修订记录

| 日期 | 版本 | 作者 | 变更 |
| --- | --- | --- | --- |
| 2026-10-16 | v1.0 | FrontendDev-d2ca64f5 | 首版：S3a 增量 2 —— 后台 Web（Vue 3 + TS + Element Plus）。14 个路由 / 12 个模块视图 + 领域层（RBAC / 审核处置 / 财务对账 / 金额 / 文案）+ 内存 mock 后端 + 6 个测试文件 65 用例。实跑 `npm ci` / `vue-tsc --noEmit` / `vite build` / `vitest run` 全绿。 |
