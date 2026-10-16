<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { moderationApi, usersApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { ErrorCodes, type AdminUserRow } from '@/types/contract';

/**
 * 用户管理（API-ADM-01 / 02 / 03 · FR-11.1 · D-ADM-002）。
 *
 * 封禁 / 解封均写入审计；封禁必须原因码（不可逆高危动作同口径）。
 */
const usersTask = useAsync<AdminUserRow[]>([]);
const reasonCodes = ref<{ code: string; label: string }[]>([]);
const keyword = ref('');

const banVisible = ref(false);
const target = ref<AdminUserRow | null>(null);
const reasonCode = ref('');
const remark = ref('');
const submitting = ref(false);
const formError = ref<string | null>(null);

async function load(): Promise<void> {
  await usersTask.run(async () => (await usersApi.list(keyword.value)).list);
  if (reasonCodes.value.length === 0) {
    reasonCodes.value = (await moderationApi.reasonCodes()).list;
  }
}

function openBan(row: AdminUserRow): void {
  target.value = row;
  reasonCode.value = '';
  remark.value = '';
  formError.value = null;
  banVisible.value = true;
}

async function confirmBan(): Promise<void> {
  const row = target.value;
  if (row === null) {
    return;
  }
  if (reasonCode.value.trim().length === 0) {
    formError.value = '请选择原因码。封禁必须留下原因码，用于审计追溯。';
    return;
  }
  submitting.value = true;
  try {
    await usersApi.ban(row.user_id, reasonCode.value, remark.value);
    ElMessage({ type: 'success', message: '已封禁该账号', duration: TOAST_DURATION_MS.success });
    banVisible.value = false;
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    formError.value = error.userMessage;
  } finally {
    submitting.value = false;
  }
}

async function unban(row: AdminUserRow): Promise<void> {
  try {
    await usersApi.unban(row.user_id);
    ElMessage({ type: 'success', message: '已解封', duration: TOAST_DURATION_MS.success });
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  }
}

onMounted(load);
</script>

<template>
  <div>
    <div class="head">
      <h2 class="page-title">用户管理</h2>
      <el-input
        v-model="keyword"
        class="head__search"
        placeholder="按昵称 / 手机号检索"
        clearable
        @keyup.enter="load"
      />
      <el-button @click="load">检索</el-button>
    </div>

    <PageState :loading="usersTask.loading.value" :error="usersTask.error.value" empty-text="没有匹配的用户" @retry="load">
      <el-table :data="usersTask.data.value ?? []" border>
        <el-table-column prop="user_id" label="用户 ID" width="90" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="phone_masked" label="手机号" width="150" />
        <el-table-column label="实名" width="90">
          <template #default="{ row }">{{ row.realname_verified ? '已实名' : '未实名' }}</template>
        </el-table-column>
        <el-table-column label="未成年" width="90">
          <template #default="{ row }">{{ row.is_minor ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'BANNED' ? 'danger' : 'success'">
              {{ row.status === 'BANNED' ? '已封禁' : '正常' }}
            </el-tag>
            <span v-if="row.ban_reason" class="text-min"> · {{ row.ban_reason }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="registered_at" label="注册时间" width="190" />
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button v-if="row.status === 'NORMAL'" size="small" type="danger" plain @click="openBan(row)">
              封禁
            </el-button>
            <el-button v-else size="small" @click="unban(row)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageState>

    <el-dialog v-model="banVisible" title="封禁账号确认" width="480px" :close-on-click-modal="false">
      <el-alert
        type="error"
        show-icon
        :closable="false"
        title="高危动作"
        description="封禁将立即禁止该账号开播，动作不可撤销，记录永久留痕。"
      />
      <el-form class="mt12" label-width="80px" label-position="left">
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
        <el-button @click="banVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmBan">确认封禁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
  font-size: 16px;
}

.head__search {
  max-width: 280px;
  margin-left: auto;
}

.mt12 {
  margin-top: 12px;
}
</style>
