import type { AdminRole } from '@/types/contract';

/**
 * 后台模块与 RBAC（D-ADM-000 / 契约 §3.9「角色」列）。
 *
 * D-ADM-000 的无权限态是硬规则：**无权限模块不渲染侧栏入口；URL 直达 → 整页 403**。
 * 因此导航由本表的角色矩阵派生，而不是写死一份菜单。
 */

export type AdminModuleKey =
  | 'moderation'
  | 'rooms'
  | 'users'
  | 'reports'
  | 'appeals'
  | 'gifts'
  | 'categories'
  | 'banners'
  | 'sysconfig'
  | 'finance'
  | 'audit';

export interface AdminModuleSpec {
  key: AdminModuleKey;
  /** 侧栏分组（D-ADM-000 角色导航）。 */
  group: 'content' | 'operation' | 'finance';
  groupLabel: string;
  label: string;
  icon: string;
  path: string;
  /** 允许访问的角色；来源 = 契约 §3.9 各 API-ADM 的「角色」列。 */
  roles: AdminRole[];
  /** 对应的 API-ID，便于追溯。 */
  apiIds: string[];
}

export const ADMIN_MODULES: readonly AdminModuleSpec[] = [
  {
    key: 'moderation',
    group: 'content',
    groupLabel: '内容审核',
    label: '审核工作台',
    icon: '🛡',
    path: '/moderation',
    roles: ['auditor'],
    apiIds: ['API-ADM-06', 'API-ADM-07', 'API-ADM-08', 'API-ADM-09'],
  },
  {
    key: 'rooms',
    group: 'content',
    groupLabel: '内容审核',
    label: '直播间管理',
    icon: '📺',
    path: '/rooms',
    roles: ['ops', 'auditor'],
    apiIds: ['API-ADM-04', 'API-ADM-05'],
  },
  {
    key: 'reports',
    group: 'content',
    groupLabel: '内容审核',
    label: '举报处理',
    icon: '⚑',
    path: '/reports',
    roles: ['auditor', 'ops'],
    apiIds: ['API-ADM-10', 'API-ADM-11'],
  },
  {
    key: 'appeals',
    group: 'content',
    groupLabel: '内容审核',
    label: '申诉与留痕',
    icon: '📜',
    path: '/appeals',
    roles: ['ops'],
    apiIds: ['API-ADM-12', 'API-ADM-13'],
  },
  {
    key: 'users',
    group: 'operation',
    groupLabel: '用户与运营',
    label: '用户管理',
    icon: '👤',
    path: '/users',
    roles: ['ops'],
    apiIds: ['API-ADM-01', 'API-ADM-02', 'API-ADM-03'],
  },
  {
    key: 'gifts',
    group: 'operation',
    groupLabel: '用户与运营',
    label: '礼物与资产',
    icon: '🎁',
    path: '/gifts',
    roles: ['ops'],
    apiIds: ['API-ADM-14', 'API-ADM-15', 'API-ADM-16'],
  },
  {
    key: 'categories',
    group: 'operation',
    groupLabel: '用户与运营',
    label: '类目与运营位',
    icon: '🗂',
    path: '/config',
    roles: ['ops'],
    apiIds: ['API-ADM-17', 'API-ADM-18', 'API-ADM-19'],
  },
  {
    key: 'sysconfig',
    group: 'operation',
    groupLabel: '用户与运营',
    label: '口径配置',
    icon: '⚙',
    path: '/sys-config',
    roles: ['ops'],
    apiIds: ['API-ADM-20'],
  },
  {
    key: 'finance',
    group: 'finance',
    groupLabel: '财务',
    label: '财务对账与提现审核',
    icon: '💰',
    path: '/finance',
    roles: ['finance'],
    apiIds: [
      'API-ADM-21',
      'API-ADM-22',
      'API-ADM-23',
      'API-ADM-24',
      'API-ADM-25',
      'API-ADM-26',
      'API-ADM-27',
      'API-ADM-28',
    ],
  },
  {
    key: 'audit',
    group: 'finance',
    groupLabel: '财务',
    label: '操作审计日志',
    icon: '📋',
    path: '/audit-logs',
    roles: ['ops', 'finance'],
    apiIds: ['API-ADM-29'],
  },
];

export function moduleByKey(key: AdminModuleKey): AdminModuleSpec {
  const found = ADMIN_MODULES.find((item) => item.key === key);
  if (found === undefined) {
    throw new Error(`未知后台模块：${key}`);
  }
  return found;
}

export function canAccess(role: AdminRole, key: AdminModuleKey): boolean {
  return moduleByKey(key).roles.includes(role);
}

export interface NavGroup {
  group: AdminModuleSpec['group'];
  groupLabel: string;
  items: AdminModuleSpec[];
}

/** 角色可见导航：**无权限模块不渲染入口**（D-ADM-000 禁用/无权限态）。 */
export function navFor(role: AdminRole): NavGroup[] {
  const groups: NavGroup[] = [];
  for (const module of ADMIN_MODULES) {
    if (!module.roles.includes(role)) {
      continue;
    }
    const existing = groups.find((group) => group.group === module.group);
    if (existing === undefined) {
      groups.push({ group: module.group, groupLabel: module.groupLabel, items: [module] });
    } else {
      existing.items.push(module);
    }
  }
  return groups;
}

/** 该角色的默认落地页（登录后跳转）。 */
export function landingPathFor(role: AdminRole): string {
  const nav = navFor(role);
  const first = nav[0]?.items[0];
  return first === undefined ? '/403' : first.path;
}

export const ROLE_LABELS: Record<AdminRole, string> = {
  auditor: '内容审核员',
  ops: '运营',
  finance: '财务',
};
