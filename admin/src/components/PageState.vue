<script setup lang="ts">
/**
 * 通用页面状态（S1b §07 六态：正常 / 加载 / 空 / 错误 / 禁用·无权限）。
 *
 * 失败必带可理解的结论（§8.1），因此错误态直接渲染 `ApiError.userMessage`
 * （文案取 S1b §09 三段式，不在页面里自行编写）。
 */
defineProps<{
  loading?: boolean;
  error?: { userMessage: string } | null;
  empty?: boolean;
  emptyText?: string;
}>();

defineEmits<{ retry: [] }>();
</script>

<template>
  <div v-if="loading" class="adm-state">
    <el-skeleton :rows="6" animated />
  </div>

  <div v-else-if="error" class="adm-state">
    <el-alert type="error" show-icon :closable="false" title="操作未成功" :description="error.userMessage" />
    <el-button class="adm-state__action" type="primary" plain @click="$emit('retry')">重试</el-button>
  </div>

  <el-empty v-else-if="empty" :description="emptyText ?? '暂无数据'" />

  <slot v-else />
</template>

<style scoped>
.adm-state {
  padding: 8px 0;
}

.adm-state__action {
  margin-top: 12px;
}
</style>
