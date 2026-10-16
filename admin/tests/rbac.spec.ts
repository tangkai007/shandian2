import { describe, expect, it } from 'vitest';
import { ADMIN_MODULES, canAccess, landingPathFor, moduleByKey, navFor } from '@/domain/rbac';
import type { AdminRole } from '@/types/contract';

describe('后台 RBAC（D-ADM-000 / 契约 §3.9 角色列）', () => {
  it('审核员：可进审核台与直播间，不可进财务 / 用户管理 / 配置', () => {
    expect(canAccess('auditor', 'moderation')).toBe(true);
    expect(canAccess('auditor', 'rooms')).toBe(true);
    expect(canAccess('auditor', 'reports')).toBe(true);
    expect(canAccess('auditor', 'finance')).toBe(false);
    expect(canAccess('auditor', 'users')).toBe(false);
    expect(canAccess('auditor', 'sysconfig')).toBe(false);
  });

  it('财务：只进财务与审计，不进审核台', () => {
    expect(canAccess('finance', 'finance')).toBe(true);
    expect(canAccess('finance', 'audit')).toBe(true);
    expect(canAccess('finance', 'moderation')).toBe(false);
    expect(canAccess('finance', 'users')).toBe(false);
  });

  it('运营：用户 / 类目 / 口径 / 申诉 / 审计，不进审核台与财务', () => {
    expect(canAccess('ops', 'users')).toBe(true);
    expect(canAccess('ops', 'categories')).toBe(true);
    expect(canAccess('ops', 'sysconfig')).toBe(true);
    expect(canAccess('ops', 'appeals')).toBe(true);
    expect(canAccess('ops', 'audit')).toBe(true);
    expect(canAccess('ops', 'moderation')).toBe(false);
    expect(canAccess('ops', 'finance')).toBe(false);
  });

  it('导航只包含有权限模块（无权限模块不渲染入口）', () => {
    for (const role of ['auditor', 'ops', 'finance'] as AdminRole[]) {
      const items = navFor(role).flatMap((group) => group.items);
      expect(items.length).toBeGreaterThan(0);
      expect(items.every((item) => item.roles.includes(role))).toBe(true);
      expect(items.every((item) => canAccess(role, item.key))).toBe(true);
    }
  });

  it('落地页必定是当前角色有权限的模块（避免刚登录即撞 403）', () => {
    for (const role of ['auditor', 'ops', 'finance'] as AdminRole[]) {
      const path = landingPathFor(role);
      expect(path).not.toBe('/403');
      const owner = ADMIN_MODULES.find((item) => item.path === path);
      expect(owner).toBeDefined();
      expect(owner?.roles.includes(role)).toBe(true);
    }
  });

  it('每个模块都标注了对应 API-ID（可追溯到契约）', () => {
    for (const item of ADMIN_MODULES) {
      expect(item.apiIds.length).toBeGreaterThan(0);
      expect(item.apiIds.every((id) => id.startsWith('API-ADM-'))).toBe(true);
    }
  });

  it('未知模块抛出明确错误', () => {
    // @ts-expect-error 故意传入非法 key，验证运行时防护
    expect(() => moduleByKey('nope')).toThrowError(/未知后台模块/);
  });
});
