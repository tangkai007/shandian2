<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError } from '@/api/client';
import { landingPathFor, ROLE_LABELS } from '@/domain/rbac';
import { useSessionStore } from '@/stores/session';
import type { AdminRole } from '@/types/contract';

/**
 * 后台登录（D-ADM-000 错误态：会话失效 → 重新登录）。
 *
 * 一期为角色入口（真实环境接企业 SSO）；登录后按角色落地到其**首个有权限模块**，
 * 不落地到无权模块，避免刚进门就撞 403。
 */
const session = useSessionStore();
const router = useRouter();
const route = useRoute();

const roles: AdminRole[] = ['auditor', 'ops', 'finance'];
const selected = ref<AdminRole>('auditor');
const error = ref<ApiError | null>(null);

onMounted(() => {
  if (route.query.expired === '1') {
    error.value = new ApiError(40100, 'session expired');
  }
});

async function onSubmit(): Promise<void> {
  try {
    await session.login(selected.value);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null;
    await router.replace(redirect ?? landingPathFor(selected.value));
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught : new ApiError(50000, String(caught));
  }
}
</script>

<template>
  <div class="login">
    <el-card class="login__card">
      <h1 class="login__title">瞬间直播 · 管理后台</h1>
      <p class="text-min">MARS-1 一期 · 后台仅对授权角色开放（RBAC）</p>

      <div class="login__roles">
        <el-radio-group v-model="selected">
          <el-radio-button v-for="role in roles" :key="role" :value="role">
            {{ ROLE_LABELS[role] }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-alert
        v-if="error"
        class="login__alert"
        type="error"
        show-icon
        :closable="false"
        title="登录未成功"
        :description="error.userMessage"
      />

      <el-button class="login__submit" type="primary" :loading="session.loading" @click="onSubmit">
        进入后台
      </el-button>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.login__card {
  width: 420px;
}

.login__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.login__roles {
  margin: 20px 0 12px;
}

.login__alert {
  margin-bottom: 12px;
}

.login__submit {
  width: 100%;
}
</style>
