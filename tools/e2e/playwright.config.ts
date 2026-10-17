import { defineConfig, devices } from '@playwright/test';

/**
 * MARS-1 · UI E2E 骨架（T2 入库，执行属 T3）
 *
 * 边界：
 *  - 本目录只放「配置 + 最小 smoke」，全量场景在 T3 按 T1/T2 用例补写。
 *  - 目标环境地址一律取环境变量 E2E_BASE_URL，不硬编码部署地址。
 *  - T3 前不得对生产环境执行。
 */
export default defineConfig({
  testDir: './specs',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  retries: 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
