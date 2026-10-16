import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import type { AdminModuleKey } from '@/domain/rbac';
import { useSessionStore } from '@/stores/session';

/**
 * 路由（D-ADM-000：无权限模块不渲染入口，URL 直达 → 整页 403）。
 *
 * 权限判定收敛到路由 `meta.moduleKey` + 导航守卫一处，页面自身不做权限分支。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/HomeRedirect.vue') },
      {
        path: 'moderation',
        name: 'moderation',
        component: () => import('@/views/ModerationWorkbenchView.vue'),
        meta: { moduleKey: 'moderation' satisfies AdminModuleKey },
      },
      {
        path: 'rooms',
        name: 'rooms',
        component: () => import('@/views/RoomsView.vue'),
        meta: { moduleKey: 'rooms' satisfies AdminModuleKey },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/ReportsView.vue'),
        meta: { moduleKey: 'reports' satisfies AdminModuleKey },
      },
      {
        path: 'appeals',
        name: 'appeals',
        component: () => import('@/views/AppealsView.vue'),
        meta: { moduleKey: 'appeals' satisfies AdminModuleKey },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { moduleKey: 'users' satisfies AdminModuleKey },
      },
      {
        path: 'gifts',
        name: 'gifts',
        component: () => import('@/views/GiftsView.vue'),
        meta: { moduleKey: 'gifts' satisfies AdminModuleKey },
      },
      {
        path: 'config',
        name: 'config',
        component: () => import('@/views/ConfigView.vue'),
        meta: { moduleKey: 'categories' satisfies AdminModuleKey },
      },
      {
        path: 'sys-config',
        name: 'sys-config',
        component: () => import('@/views/SysConfigView.vue'),
        meta: { moduleKey: 'sysconfig' satisfies AdminModuleKey },
      },
      {
        path: 'finance',
        name: 'finance',
        component: () => import('@/views/FinanceView.vue'),
        meta: { moduleKey: 'finance' satisfies AdminModuleKey },
      },
      {
        path: 'audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/AuditLogsView.vue'),
        meta: { moduleKey: 'audit' satisfies AdminModuleKey },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true },
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  if (to.meta.public === true) {
    return true;
  }
  const session = useSessionStore();
  const restored = await session.restore();
  if (!restored) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  const moduleKey = to.meta.moduleKey as AdminModuleKey | undefined;
  if (moduleKey !== undefined && !session.can(moduleKey)) {
    // URL 直达无权限模块 → 整页 403，不泄露模块内任何数据。
    return { name: 'forbidden' };
  }
  return true;
});
