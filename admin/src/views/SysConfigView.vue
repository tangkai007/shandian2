<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { sysConfigApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { fenToYuan, yuanTextToFen } from '@/domain/money';
import { ErrorCodes, type SysConfigRow } from '@/types/contract';

/**
 * 口径配置（API-ADM-20 · S1b §11.3「必须配置化下发的口径」）。
 *
 * 这张表就是「金额 / 费率 / 限额 / 时限 / 类目 / 档位」的唯一来源 —— 改口径不发版。
 * 因此前端**任何页面都不得硬编码这些数值**，只能读本表。
 */
const configTask = useAsync<SysConfigRow[]>([]);
const editingKey = ref<string | null>(null);
const draft = ref<string>('');
const saving = ref(false);

async function load(): Promise<void> {
  await configTask.run(async () => (await sysConfigApi.list()).list);
}

function displayValue(row: SysConfigRow): string {
  if (row.value_type === 'fen' && typeof row.value === 'number') {
    return fenToYuan(row.value);
  }
  if (row.value_type === 'percent' && typeof row.value === 'number') {
    return `${row.value}%`;
  }
  if (row.value_type === 'bool') {
    return row.value === true ? '开' : '关';
  }
  if (Array.isArray(row.value)) {
    return row.value.join(' / ');
  }
  return String(row.value);
}

function beginEdit(row: SysConfigRow): void {
  editingKey.value = row.key;
  draft.value =
    row.value_type === 'fen' && typeof row.value === 'number'
      ? fenToYuan(row.value, { withSymbol: false })
      : Array.isArray(row.value)
        ? row.value.join(',')
        : String(row.value);
}

function cancelEdit(): void {
  editingKey.value = null;
  draft.value = '';
}

async function save(row: SysConfigRow): Promise<void> {
  let nextValue: SysConfigRow['value'] = draft.value;

  if (row.value_type === 'fen') {
    const fen = yuanTextToFen(draft.value);
    if (fen === null) {
      ElMessage({ type: 'error', message: '金额格式不合法。请输入形如 100 或 100.00 的金额。', duration: TOAST_DURATION_MS.failure });
      return;
    }
    nextValue = fen;
  } else if (row.value_type === 'percent' || row.value_type === 'int' || row.value_type === 'days') {
    const parsed = Number.parseInt(draft.value, 10);
    if (Number.isNaN(parsed)) {
      ElMessage({ type: 'error', message: '请输入整数。', duration: TOAST_DURATION_MS.failure });
      return;
    }
    nextValue = parsed;
  } else if (row.value_type === 'bool') {
    nextValue = draft.value === 'true' || draft.value === '开';
  } else if (row.value_type === 'list') {
    nextValue = draft.value
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  }

  saving.value = true;
  try {
    await sysConfigApi.update(row.key, nextValue);
    ElMessage({ type: 'success', message: '口径已更新，客户端下次读取即生效', duration: TOAST_DURATION_MS.success });
    cancelEdit();
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h2 class="page-title">口径配置</h2>
    <p class="text-min">
      分成率 / 渠道费率 / 税率与扣减顺序 / 起提金额 / 结算周期 / 未成年限额 / 类目 / 支付渠道 / 申诉时限 / 断流阈值 /
      回放留存 —— 全部由后台下发，客户端不参与计算、不硬编码。
    </p>

    <PageState :loading="configTask.loading.value" :error="configTask.error.value" @retry="load">
      <el-table :data="configTask.data.value ?? []" border>
        <el-table-column prop="key" label="配置项" width="260" />
        <el-table-column prop="label" label="含义" width="260" />
        <el-table-column prop="value_type" label="类型" width="90" />
        <el-table-column label="当前值">
          <template #default="{ row }">
            <template v-if="editingKey === row.key">
              <el-input v-model="draft" size="small" style="max-width: 240px" />
            </template>
            <span v-else class="adm-num">{{ displayValue(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="依据" width="200" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <template v-if="editingKey === row.key">
              <el-button size="small" type="primary" :loading="saving" @click="save(row)">保存</el-button>
              <el-button size="small" @click="cancelEdit">取消</el-button>
            </template>
            <el-button v-else size="small" @click="beginEdit(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageState>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 4px;
  font-size: 16px;
}
</style>
