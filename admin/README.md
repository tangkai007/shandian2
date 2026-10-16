# admin · 瞬间直播管理后台（MARS-1 一期）

Vue 3 + TypeScript + Element Plus + Vite。覆盖 S1b v3.2 的 4 个后台帧与契约 §3.9 的
`API-ADM-01 … 29`。

## 命令

```bash
npm ci          # 或 npm install（首次）
npm run typecheck   # vue-tsc --noEmit
npm run build       # vue-tsc --noEmit && vite build
npm test            # vitest run
npm run dev         # 本地开发（默认 http://localhost:5174）
```

## 目录

```
src/
  types/contract.ts   契约 DTO 与错误码（字段名取契约原文，不另立别名）
  domain/             rbac · moderation · finance · money · messages（口径与文案的唯一定义处）
  api/                endpoints（API-ADM-xx 映射）· client（统一响应/鉴权）· mock（内存后端）· admin（接口封装）
  stores/session.ts   会话与角色（登录态、权限判定）
  router/             路由 + RBAC 守卫（无权限 → 整页 403）
  layouts/            后台框架与角色导航（D-ADM-000）
  components/         PageState（六态）· MoneyText · DispositionDialog（D-ADM-002）
  views/              审核工作台 / 直播间 / 举报 / 申诉 / 用户 / 礼物 / 类目运营位 / 口径配置 / 财务 / 审计
tests/                65 个用例（domain + mock 后端端到端）
```

## 后端对接

默认走内存 mock（`src/api/mock.ts`），它按契约实现关键校验，可在无后端时验证错误码分支。
切真实网关：

```bash
VITE_USE_MOCK=false VITE_API_BASE=https://<host>/api/v1 npm run build
```

`mock` 与真实网关返回同一形态的统一响应封装，因此错误码分支、会话清除、文案取值只有一份实现。

## 角色与入口

| 角色 | 可见模块 |
| --- | --- |
| 内容审核员 `auditor` | 审核工作台 · 直播间管理 · 举报处理 |
| 运营 `ops` | 直播间管理 · 举报处理 · 申诉与留痕 · 用户管理 · 礼物与资产 · 类目与运营位 · 口径配置 · 操作审计日志 |
| 财务 `finance` | 财务对账与提现审核 · 操作审计日志 |

无权限模块**不渲染侧栏入口**；URL 直达 → 整页 403（不泄露模块内任何数据）。

## 不硬编码清单

分成率 / 渠道费率 / 税率与扣减顺序 / 起提金额 / 结算周期 / 未成年限额 / 类目 / 支付渠道与 IAP 档位 /
申诉时限 / 断流阈值 / 回放留存 —— 全部读 `GET /admin/sys-config`（`口径配置` 页可改，改口径不发版）。
