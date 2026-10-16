<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { moderationApi, roomsApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { ACTION_SPECS } from '@/domain/moderation';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { ErrorCodes, type AdminRoomRow } from '@/types/contract';

/**
 * 直播间管理（API-ADM-04 / 05）。
 *
 * 强制关播是**高危不可逆**动作：二次确认 + 必填原因码 + 留痕（S1b §01④）。
 */
const roomsTask = useAsync<AdminRoomRow[]>([]);
const reasonCodes = ref<{ code: string; label: string }[]>([]);

const dialogVisible = ref(false);
const target = ref<AdminRoomRow | null>(null);
const reasonCode = ref('');
const remark = ref('');
const submitting = ref(false);
const formError = ref<string | null>(null);

const forceStopSpec = computed(() => ACTION_SPECS.CUT);

async function load(): Promise<void> {
  await roomsTask.run(async () => (await roomsApi.list()).list);
  if (reasonCodes.value.length === 0) {
    reasonCodes.value = (await moderationApi.reasonCodes()).list;
  }
}

function open(room: AdminRoomRow): void {
  target.value = room;
  reasonCode.value = '';
  remark.value = '';
  formError.value = null;
  dialogVisible.value = true;
}

async function confirmForceStop(): Promise<void> {
  const room = target.value;
  if (room === null) {
    return;
  }
  if (reasonCode.value.trim().length === 0) {
    formError.value = '请选择原因码。强制关播必须留下原因码，用于审计追溯。';
    return;
  }
  if (remark.value.trim().length < 5) {
    formError.value = '备注不少于 5 字。备注用于记录判断依据，写入审计后不可编辑。';
    return;
  }
  submitting.value = true;
  try {
    await roomsApi.forceStop(room.room_id, reasonCode.value, remark.value);
    ElMessage({ type: 'success', message: '已强制关播，处置已留痕', duration: TOAST_DURATION_MS.success });
    dialogVisible.value = false;
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    formError.value = error.userMessage;
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h2 class="page-title">直播间管理</h2>

    <PageState :loading="roomsTask.loading.value" :error="roomsTask.error.value" @retry="load">
      <el-table :data="roomsTask.data.value ?? []" border>
        <el-table-column prop="room_no" label="房号" width="100" />
        <el-table-column prop="anchor_name" label="主播" width="120" />
        <el-table-column prop="category_name" label="类目" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'LIVE' ? 'success' : 'info'">
              {{ row.status === 'LIVE' ? '● 直播中' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线" width="100">
          <template #default="{ row }"><span class="adm-num">{{ row.viewer_cnt }}</span></template>
        </el-table-column>
        <el-table-column label="风险分" width="100">
          <template #default="{ row }">
            <span class="adm-num" :class="row.risk_score >= 80 ? 'text-danger' : 'text-secondary'">
              {{ row.risk_score }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="120">
          <template #default="{ row }">
            <span v-if="row.locked_by_name">🔒 {{ row.locked_by_name }}</span>
            <span v-else class="text-min">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain :disabled="row.status !== 'LIVE'" @click="open(row)">
              强制关播
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageState>

    <el-dialog v-model="dialogVisible" title="强制关播确认" width="520px" :close-on-click-modal="false">
      <el-alert
        type="error"
        show-icon
        :closable="false"
        title="高危不可逆动作"
        :description="forceStopSpec.consequence"
      />
      <el-form class="mt12" label-width="96px" label-position="left">
        <el-form-item label="原因码">
          <el-select v-model="reasonCode" placeholder="选择原因码…" style="width: 100%">
            <el-option v-for="item in reasonCodes" :key="item.code" :label="item.label" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注（≥5 字）">
          <el-input v-model="remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert v-if="formError" type="error" show-icon :closable="false" :title="formError" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmForceStop">确认断流</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.mt12 {
  margin-top: 12px;
}
</style>
