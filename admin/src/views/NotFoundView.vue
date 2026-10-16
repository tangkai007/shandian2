<script setup lang="ts">
import { useRouter } from 'vue-router';
import { landingPathFor } from '@/domain/rbac';
import { useSessionStore } from '@/stores/session';

/** 404（D-ADM-000 全局规则：整页 404）。 */
const session = useSessionStore();
const router = useRouter();

function back(): void {
  const role = session.role ?? 'ops';
  void router.replace(landingPathFor(role));
}
</script>

<template>
  <div class="notfound">
    <el-result icon="info" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="back">返回后台首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.notfound {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
