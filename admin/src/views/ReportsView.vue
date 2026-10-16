<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { moderationApi, reportsApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { ErrorCodes, type ReportRow } from '@/types/contract';

/** 举报处理（API-ADM-10 / 11 · FR-9.4 · D-ADM-001）。 */
const reportsTask = useAsync<ReportRow[]>([]);
const reasonCodes = ref<{ code: string; label: string }[]>([]);

const dialogVisible = ref(false);
const target = ref<ReportRow | null>(null);
const decision = ref<'HANDLE' | 'REJECT'>('HANDLE');
const reasonCode = ref('');
const remark = ref('');
const submitting = ref(false);
const formError = ref<string | null>(null);

async function load(): Promise<void> {
  await reportsTask.run(async () => (await reportsApi.list()).list);
  if (reasonCodes.value.length === 0) {
    reasonCodes.value = (await moderationApi.reasonCodes()).list;
  }
}

function open(row: ReportRow, nextDecision: 'HANDLE' | 'REJECT'): void {
  target.value = row;
  decision.value = nextDecision;
  reasonCode.value = '';
  remark.value = '';
  formError.value = null;
  dialogVisible.value = true;
}

async function submit(): Promise<void> {
  const row = target.value;
  if (row === null) {
    return;
  }
  if (decision.value === 'REJECT' && reasonCode.value.trim().length === 0) {
    formError.value = '驳回必须选择原因码。原因码会同步给举报人。';
    return;
  }
  submitting.value = true;
  try {
    await reportsApi.handle(row.report_id, decision.value, reasonCode.value, remark.value);
    ElMessage({ type: 'success', message: '举报已处置', duration: TOAST_DURATION_MS.success });
    dialogVisible.value = false;
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    formError.value = error.userMessage;
  } finally {
    submitting.value = false;
  }
}

const STATUS_LABELS: Record<ReportRow['status'], string> = {
  PENDING: '待处理',
  HANDLED: '已处理',
  REJECTED: '已驳回',
};

/** 模板用取值函数（避免在模板表达式里写类型断言）。 */
function statusLabel(status: ReportRow['status']): string {
  return STATUS_LABELS[status];
}

function statusTagType(status: ReportRow['status']): 'warning' | 'info' {
  return status === 'PENDING' ? 'warning' : 'info';
}

onMounted(load);
</script>

<template>
  <div>
    <h2 class="page-title">举报处理</h2>

    <PageState :loading="reportsTask.loading.value" :error="reportsTask.error.value" empty-text="暂无待处理举报" @retry="load">
      <el-table :data="reportsTask.data.value ?? []" border>
        <el-table-column prop="report_id" label="举报单号" width="120" />
        <el-table-column label="对象" width="200">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.target_type }}</el-tag>
            {{ row.target_label }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="理由" width="160" />
        <el-table-column prop="reporter_masked" label="举报人" width="180" />
        <el-table-column prop="created_at" label="时间" width="200" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.status !== 'PENDING'" @click="open(row, 'HANDLE')">
              处置
            </el-button>
            <el-button size="small" :disabled="row.status !== 'PENDING'" @click="open(row, 'REJECT')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageState>

    <el-dialog v-model="dialogVisible" :title="decision === 'HANDLE' ? '处置举报' : '驳回举报'" width="480px">
      <p class="text-min">对象：{{ target?.target_label }}</p>
      <el-form label-width="80px" label-position="left">
        <el-form-item label="原因码">
          <el-select v-model="reasonCode" placeholder="选择原因码…" style="width: 100%">
            <el-option v-for="item in reasonCodes" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert v-if="formError" type="error" show-icon :closable="false" :title="formError" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 12px;
  font-size: 16px;
}
</style>
