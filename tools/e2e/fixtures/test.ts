import { test as base, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';

/** 扩展 fixtures：后续页面对象统一从这里注入，避免各 spec 自己 new。 */
export const test = base.extend<{ loginPage: LoginPage }>({
  loginPage: async ({ page }, use) => {
    await use(new LoginPage(page));
  },
});

export { expect };
