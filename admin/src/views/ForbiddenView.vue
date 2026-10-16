<script setup lang="ts">
import { useRouter } from 'vue-router';
import { landingPathFor } from '@/domain/rbac';
import { FORBIDDEN_PAGE } from '@/domain/messages';
import { useSessionStore } from '@/stores/session';

/** 整页 403（D-ADM-000）：不泄露模块内的任何数据。 */
const session = useSessionStore();
const router = useRouter();

function back(): void {
  const role = session.role;
  void router.replace(role === null ? { name: 'login' } : landingPathFor(role));
}
</script>

<template>
  <div class="forbidden">
    <el-result icon="warning" title="403" :sub-title="FORBIDDEN_PAGE.title">
      <template #extra>
        <p class="forbidden__detail">{{ FORBIDDEN_PAGE.detail }}</p>
        <el-button type="primary" @click="back">{{ FORBIDDEN_PAGE.action }}</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.forbidden__detail {
  max-width: 520px;
  margin: 0 auto 16px;
  color: var(--n-600);
  font-size: 13px;
  line-height: 1.7;
}
</style>
