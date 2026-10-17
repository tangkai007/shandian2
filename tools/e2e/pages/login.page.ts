import type { Page, Locator } from '@playwright/test';

/**
 * 登录页 Page Object（最小实现）。
 * 入口路径与选择器在 T3 对照实际部署页面补全；当前只保证「可达 + body 可见」。
 */
export class LoginPage {
  constructor(private readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/');
  }

  get body(): Locator {
    return this.page.locator('body');
  }

  get url(): string {
    return this.page.url();
  }
}
