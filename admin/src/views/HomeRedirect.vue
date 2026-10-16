<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { landingPathFor } from '@/domain/rbac';
import { useSessionStore } from '@/stores/session';

/** 登录后落地页：跳到当前角色**首个有权限模块**（避免直接撞 403）。 */
const session = useSessionStore();
const router = useRouter();

onMounted(() => {
  const role = session.role;
  void router.replace(role === null ? { name: 'login' } : landingPathFor(role));
});
</script>

<template>
  <el-skeleton :rows="4" animated />
</template>
