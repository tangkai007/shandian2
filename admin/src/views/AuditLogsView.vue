<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { auditApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { ROLE_LABELS } from '@/domain/rbac';
import type { AuditLogRow } from '@/types/contract';

/**
 * 操作审计日志（API-ADM-29 · FR-11.4 · D-ADM-000）。
 *
 * 每次处置与运营写操作都会落审计：操作人 / 时间 / 目标 / 动作 / 原因码 / 备注 / 证据 ID，
 * **不可编辑不可删除**（保留 ≥ 1 年）。
 */
const logsTask = useAsync<AuditLogRow[]>([]);
const keyword = ref('');

async function load(): Promise<void> {
  await logsTask.run(async () => (await auditApi.list(keyword.value)).list);
}

/** 模板用取值函数（避免在模板表达式里写类型断言）。 */
function roleLabel(role: AuditLogRow['operator_role']): string {
  return ROLE_LABELS[role];
}

onMounted(load);
</script>

<template>
  <div>
    <div class="head">
      <h2 class="page-title">操作审计日志</h2>
      <el-input v-model="keyword" class="head__search" placeholder="按目标 / 动作检索" clearable @keyup.enter="load" />
      <el-button @click="load">检索</el-button>
      <el-button type="primary" plain @click="load">导出</el-button>
    </div>
    <p class="text-min">留痕不可编辑、不可删除；保留 ≥ 1 年（FR-11.4）。</p>

    <PageState :loading="logsTask.loading.value" :error="logsTask.error.value" empty-text="暂无审计记录" @retry="load">
      <el-table :data="logsTask.data.value ?? []" border>
        <el-table-column prop="created_at" label="时间" width="200" />
        <el-table-column label="操作人" width="150">
          <template #default="{ row }">{{ row.operator_name }}（{{ roleLabel(row.operator_role) }}）</template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="180" />
        <el-table-column prop="target_type" label="对象类型" width="130" />
        <el-table-column prop="target_label" label="目标" width="200" />
        <el-table-column label="原因码" width="110">
          <template #default="{ row }">{{ row.reason_code ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="证据 ID" width="110">
          <template #default="{ row }">{{ row.evidence_id ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="ip" label="IP" width="130" />
      </el-table>
    </PageState>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.page-title {
  margin: 0;
  font-size: 16px;
}

.head__search {
  max-width: 260px;
  margin-left: auto;
}
</style>
