import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { sessionApi } from '@/api/admin';
import { clearAccessToken, getAccessToken, setAccessToken } from '@/api/authToken';
import { canAccess, type AdminModuleKey } from '@/domain/rbac';
import type { AdminRole, AdminSession } from '@/types/contract';

/**
 * 会话 store（D-ADM-000）。
 *
 * 无权限模块**不渲染侧栏入口**且 URL 直达被守卫拦截为整页 403，
 * 因此权限判定收敛到 `can()` 一处。
 */
export const useSessionStore = defineStore('session', () => {
  const session = ref<AdminSession | null>(null);
  const loading = ref(false);
  const restoring = ref(false);

  const role = computed<AdminRole | null>(() => session.value?.role ?? null);
  const operatorName = computed(() => session.value?.nickname ?? '');
  const operatorEntity = computed(() => session.value?.operator_entity ?? '');

  async function login(nextRole: AdminRole): Promise<void> {
    loading.value = true;
    try {
      const result = await sessionApi.login(nextRole);
      setAccessToken(result.access_token);
      session.value = result.session;
    } finally {
      loading.value = false;
    }
  }

  async function restore(): Promise<boolean> {
    if (session.value !== null) {
      return true;
    }
    if (getAccessToken() === null) {
      return false;
    }
    restoring.value = true;
    try {
      session.value = await sessionApi.profile();
      return true;
    } catch {
      clearAccessToken();
      session.value = null;
      return false;
    } finally {
      restoring.value = false;
    }
  }

  function logout(): void {
    clearAccessToken();
    session.value = null;
  }

  function can(key: AdminModuleKey): boolean {
    return session.value !== null && canAccess(session.value.role, key);
  }

  return { session, loading, restoring, role, operatorName, operatorEntity, login, restore, logout, can };
});
