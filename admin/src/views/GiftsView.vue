<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { giftsApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { ErrorCodes, type GiftRow } from '@/types/contract';

/** 礼物与资产（API-ADM-14 / 15 / 16 · FR-5.2 · D-ADM-000）。 */
const giftsTask = useAsync<GiftRow[]>([]);
const savingId = ref<number | null>(null);

async function load(): Promise<void> {
  await giftsTask.run(async () => (await giftsApi.list()).list);
}

async function patch(row: GiftRow, body: Partial<Pick<GiftRow, 'name' | 'price_coin' | 'status'>>): Promise<void> {
  savingId.value = row.id;
  try {
    await giftsApi.update(row.id, body);
    ElMessage({ type: 'success', message: '已保存', duration: TOAST_DURATION_MS.success });
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  } finally {
    savingId.value = null;
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h2 class="page-title">礼物与资产</h2>
    <p class="text-min">单价以虚拟币计（币）；上下架即时生效，客户端目录随接口下发（FR-5.2）。</p>

    <PageState :loading="giftsTask.loading.value" :error="giftsTask.error.value" empty-text="暂无礼物" @retry="load">
      <el-table :data="giftsTask.data.value ?? []" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column label="单价（币）" width="150">
          <template #default="{ row }">
            <el-input-number
              :model-value="row.price_coin"
              :min="1"
              size="small"
              @change="(value: number | undefined) => patch(row, { price_coin: value ?? row.price_coin })"
            />
          </template>
        </el-table-column>
        <el-table-column prop="effect_type" label="特效" width="150" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'ON'"
              :loading="savingId === row.id"
              active-text="上架"
              inactive-text="下架"
              @change="(value: boolean) => patch(row, { status: value ? 'ON' : 'OFF' })"
            />
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
