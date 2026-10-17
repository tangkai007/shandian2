import { test, expect } from '../fixtures/test';

/**
 * 最小 smoke：仅验证「登录页可达」。
 * 全量 e2e（1 功能用例 = 1 e2e）在 T3 按 T1/T2 用例补写，本文件不承载业务断言。
 */
test('smoke: 登录页可达', async ({ loginPage, page }) => {
  const response = await loginPage.open();

  // 页面必须返回响应且不是 4xx/5xx
  expect(response, '首页应返回 HTTP 响应').not.toBeNull();
  expect(response!.status(), '首页状态码应 < 400').toBeLessThan(400);

  // body 可见即视为「可达」（不校验具体 DOM，避免与真实页面耦合）
  await expect(loginPage.body).toBeVisible();
  expect(page.url()).toContain('http');
});
