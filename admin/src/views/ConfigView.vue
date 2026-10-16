<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { bannersApi, categoriesApi } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { TOAST_DURATION_MS } from '@/domain/messages';
import {
  ErrorCodes,
  type BannerRow,
  type CategoryRow,
} from '@/types/contract';

/**
 * 类目与运营位配置（API-ADM-17 / 18 / 19 · FR-11.2 · OP-7 · D-ADM-000）。
 *
 * 一期类目固定 4 类（才艺 / 游戏 / 闲聊 / 生活）；受限类目不得开放（BR-2）。
 * 首页推荐一期为「热度 + 关注」，**无个性化推荐** —— 因此运营位只做栏目/房间位，不做「猜你喜欢」类模块（OP-12 / S1b）。
 */
const tab = ref<'categories' | 'banners'>('categories');

const categoriesTask = useAsync<CategoryRow[]>([]);
const bannersTask = useAsync<BannerRow[]>([]);

const createVisible = ref(false);
const submitting = ref(false);
const formError = ref<string | null>(null);
const form = reactive<Omit<BannerRow, 'id'>>({
  title: '',
  target_type: 'CATEGORY',
  target_value: '',
  start_at: '',
  end_at: '',
  enabled: true,
  sort: 1,
});

async function load(): Promise<void> {
  await categoriesTask.run(async () => (await categoriesApi.list()).list);
  await bannersTask.run(async () => (await bannersApi.list()).list);
}

async function patchCategory(row: CategoryRow, body: Partial<Pick<CategoryRow, 'enabled' | 'sort'>>): Promise<void> {
  try {
    await categoriesApi.update(row.id, body);
    ElMessage({ type: 'success', message: '类目已更新', duration: TOAST_DURATION_MS.success });
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
    await load();
  }
}

async function patchBanner(row: BannerRow, body: Partial<Omit<BannerRow, 'id'>>): Promise<void> {
  try {
    await bannersApi.update(row.id, body);
    ElMessage({ type: 'success', message: '运营位已更新', duration: TOAST_DURATION_MS.success });
    await load();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  }
}

async function submitBanner(): Promise<void> {
  if (form.title.trim().length === 0) {
    formError.value = '请填写运营位标题。';
    return;
  }
  submitting.value = true;
  try {
    await bannersApi.create({ ...form });
    ElMessage({ type: 'success', message: '运营位已创建', duration: TOAST_DURATION_MS.success });
    createVisible.value = false;
    form.title = '';
    form.target_value = '';
    form.start_at = '';
    form.end_at = '';
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
    <h2 class="page-title">类目与运营位</h2>

    <el-tabs v-model="tab">
      <el-tab-pane label="直播类目" name="categories">
        <p class="text-min">一期固定 4 类（OP-7）；受限类目（电商 / 教育 / 医疗 / 金融）不得开放（BR-2）。</p>
        <PageState :loading="categoriesTask.loading.value" :error="categoriesTask.error.value" @retry="load">
          <el-table :data="categoriesTask.data.value ?? []" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="类目" width="140" />
            <el-table-column label="需资质" width="100">
              <template #default="{ row }">{{ row.require_qualification ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="受限" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.restricted ? 'danger' : 'info'">
                  {{ row.restricted ? '受限' : '常规' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="160">
              <template #default="{ row }">
                <el-input-number
                  :model-value="row.sort"
                  :min="1"
                  size="small"
                  @change="(value: number | undefined) => patchCategory(row, { sort: value ?? row.sort })"
                />
              </template>
            </el-table-column>
            <el-table-column label="开放" width="180">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled"
                  :disabled="row.restricted"
                  @change="(value: boolean) => patchCategory(row, { enabled: value })"
                />
                <span v-if="row.restricted" class="text-min"> 一期不开放</span>
              </template>
            </el-table-column>
          </el-table>
        </PageState>
      </el-tab-pane>

      <el-tab-pane label="运营位" name="banners">
        <p class="text-min">一期推荐为「热度 + 关注」，无个性化推荐（OP-12）：运营位仅做栏目 / 房间位。</p>
        <div class="toolbar">
          <el-button type="primary" @click="createVisible = true">新建运营位</el-button>
        </div>
        <PageState :loading="bannersTask.loading.value" :error="bannersTask.error.value" empty-text="暂无运营位" @retry="load">
          <el-table :data="bannersTask.data.value ?? []" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" width="200" />
            <el-table-column prop="target_type" label="指向" width="120" />
            <el-table-column prop="target_value" label="目标值" width="180" />
            <el-table-column label="投放期" width="220">
              <template #default="{ row }">{{ row.start_at }} ~ {{ row.end_at }}</template>
            </el-table-column>
            <el-table-column label="启用" width="120">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled"
                  @change="(value: boolean) => patchBanner(row, { enabled: value })"
                />
              </template>
            </el-table-column>
          </el-table>
        </PageState>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="createVisible" title="新建运营位" width="520px">
      <el-form label-width="90px" label-position="left">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="40" />
        </el-form-item>
        <el-form-item label="指向类型">
          <el-select v-model="form.target_type" style="width: 100%">
            <el-option label="类目" value="CATEGORY" />
            <el-option label="直播间" value="ROOM" />
            <el-option label="外链" value="URL" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值">
          <el-input v-model="form.target_value" placeholder="类目 ID / 房间 ID / URL" />
        </el-form-item>
        <el-form-item label="投放期">
          <el-input v-model="form.start_at" placeholder="开始（YYYY-MM-DD）" />
          <el-input v-model="form.end_at" class="mt8" placeholder="结束（YYYY-MM-DD）" />
        </el-form-item>
      </el-form>
      <el-alert v-if="formError" type="error" show-icon :closable="false" :title="formError" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBanner">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.toolbar {
  margin-bottom: 8px;
}

.mt8 {
  margin-top: 8px;
}
</style>
