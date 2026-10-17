# MARS-1 · UI E2E（骨架）

> 由 T2 入库，**执行属 T3**（须等 G2.5 PASS 拿到部署地址）。

## 边界

- 本目录当前只含**配置 + 一次最小 smoke**（登录页可达），不含全量业务场景。
- 目标地址一律走环境变量，**不硬编码**：
  ```bash
  E2E_BASE_URL=http://<deploy-host>:<port> npm test
  ```
- T3 补全规则：**1 条功能用例 = 1 个 e2e 测试**，路径沿用本目录结构。

## 结构

```
tools/e2e/
├── playwright.config.ts   # baseURL=E2E_BASE_URL，chromium，失败留 trace/截图
├── fixtures/test.ts       # 扩展 fixtures（注入 Page Object）
├── pages/login.page.ts    # 登录页 Page Object（最小）
├── specs/smoke.spec.ts    # 最小 smoke：登录页可达
├── package.json
└── tsconfig.json
```

## 自查（离线，不启动浏览器）

```bash
npm run list
```
