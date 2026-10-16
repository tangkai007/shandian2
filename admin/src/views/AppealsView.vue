<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { appealsApi, sysConfigApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { appealDeadlineAt, ACTION_SPECS } from '@/domain/moderation';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { ErrorCodes, type AppealRow } from '@/types/contract';

/**
 * 申诉与留痕（API-ADM-12 / 13 · BR-8 / OP-17 · D-ADM-002）。
 *
 * 口径：`deadline_at = action_time + appeal_window_days`（天数来自后台配置）；
 * **申诉期间处置不解除**（受理后才解除）。
 */
const appealsTask = useAsync<AppealRow[]>([]);
const appealWindowDays = ref<number>(0);

const dialogVisible = ref(false);
const target = ref<AppealRow | null>(null);
const decision = ref<'ACCEPT' | 'REJECT'>('ACCEPT');
const remark = ref('');
const submitting = ref(false);
const formError = ref<string | null>(null);

const deadlineHint = computed(() =>
  appealWindowDays.value === 0 ? '申诉时限来自后台配置' : `申诉时限 ${appealWindowDays.value} 天（后台配置 · OP-17）`,
);

/** 用配置天数复算截止时间，核验后端下发值一致（BR-8 可核对性）。 */
function recomputedDeadline(row: AppealRow): string {
  return appealWindowDays.value === 0 ? row.deadline_at : appealDeadlineAt(row.action_time, appealWindowDays.value);
}

async function load(): Promise<void> {
  await appealsTask.run(async () => (await appealsApi.list()).list);
  if (appealWindowDays.value === 0) {
    const config = await sysConfigApi.list();
    const row = config.list.find((item) => item.key === 'appeal.window_days');
    appealWindowDays.value = typeof row?.value === 'number' ? row.value : 0;
  }
}

function open(row: AppealRow, nextDecision: 'ACCEPT' | 'REJECT'): void {
  target.value = row;
  decision.value = nextDecision;
  remark.value = '';
  formError.value = null;
  dialogVisible.value = true;
}

async function submit(): Promise<void> {
  const row = target.value;
  if (row === null) {
    return;
  }
  submitting.value = true;
  try {
    await appealsApi.handle(row.appeal_id, decision.value, remark.value);
    ElMessage({ type: 'success', message: decision.value === 'ACCEPT' ? '已受理申诉' : '已驳回申诉', duration: TOAST_DURATION_MS.success });
    dialogVisible.value = false;
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    formError.value = error.userMessage;
  } finally {
    submitting.value = false;
  }
}

const STATUS_LABELS: Record<AppealRow['status'], string> = {
  PENDING: '待处理',
  ACCEPTED: '已受理',
  REJECTED: '已驳回',
};

/** 模板用取值函数（避免在模板表达式里写类型断言）。 */
function actionName(type: AppealRow['action_type']): string {
  return ACTION_SPECS[type].confirmActionName;
}

function statusLabel(status: AppealRow['status']): string {
  return STATUS_LABELS[status];
}

function statusTagType(status: AppealRow['status']): 'warning' | 'info' {
  return status === 'PENDING' ? 'warning' : 'info';
}

onMounted(load);
</script>

<template>
  <div>
    <h2 class="page-title">申诉与留痕</h2>
    <p class="text-min">
      {{ deadlineHint }} · 申诉期间处置不解除（BR-8）；处置记录不可编辑、不可删除（AC-7）。
    </p>

    <PageState :loading="appealsTask.loading.value" :error="appealsTask.error.value" empty-text="暂无申诉" @retry="load">
      <el-table :data="appealsTask.data.value ?? []" border>
        <el-table-column prop="appeal_id" label="申诉单号" width="110" />
        <el-table-column label="处置动作" width="120">
          <template #default="{ row }">{{ actionName(row.action_type) }}</template>
        </el-table-column>
        <el-table-column prop="target_label" label="目标" width="180" />
        <el-table-column prop="reason_code" label="原因码" width="110" />
        <el-table-column prop="action_time" label="处置时间" width="190" />
        <el-table-column label="申诉截止" width="190">
          <template #default="{ row }">
            <span class="adm-num">{{ recomputedDeadline(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处置是否生效" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.disposition_effective ? 'danger' : 'success'">
              {{ row.disposition_effective ? '仍生效' : '已解除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :disabled="row.status !== 'PENDING'" @click="open(row, 'ACCEPT')">
              受理
            </el-button>
            <el-button size="small" :disabled="row.status !== 'PENDING'" @click="open(row, 'REJECT')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageState>

    <el-dialog v-model="dialogVisible" :title="decision === 'ACCEPT' ? '受理申诉' : '驳回申诉'" width="480px">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="申诉期间处置不解除"
        description="仅当申诉被受理时，原处置才会解除；驳回后处置继续生效。"
      />
      <el-form class="mt12" label-width="60px" label-position="left">
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
  margin: 0 0 4px;
  font-size: 16px;
}

.mt12 {
  margin-top: 12px;
}
</style>
