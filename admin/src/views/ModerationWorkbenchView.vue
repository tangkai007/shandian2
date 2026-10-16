<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { moderationApi, roomsApi } from '@/api/admin';
import DispositionDialog from '@/components/DispositionDialog.vue';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { confidence } from '@/domain/money';
import {
  ACTION_ORDER,
  ACTION_SPECS,
  ALERT_TYPE_LABELS,
  isRoomLockedByOther,
  lockNotice,
  sortAlerts,
  sortRoomsByRisk,
  type DispositionDraft,
} from '@/domain/moderation';
import { TOAST_DURATION_MS } from '@/domain/messages';
import { useSessionStore } from '@/stores/session';
import { ErrorCodes, type AdminRoomRow, type ModerationAction, type ModerationTaskRow } from '@/types/contract';

/**
 * 审核工作台（D-ADM-001 · FR-9.1 / FR-9.2 / FR-9.4 · AC-7）。
 *
 * 三栏：直播中房间（按风险分排序）· 实时预览 + AI 告警时间线 · 处置区。
 * 状态口径：
 * - 空态：队列为空 → 「当前无待审内容」+ 上次刷新时间；**高危区始终保留房间列表**（巡房不发空态）；
 * - 加载态：房间列表与时间线各自骨架，预览加载失败**不阻塞**处置区；
 * - 冲突态：房间被他人锁定 → 处置按钮禁用 + 「ada 正在处理该房间」；
 * - 自动刷新：每 5s。
 */
const session = useSessionStore();

const roomsTask = useAsync<AdminRoomRow[]>([]);
const tasksTask = useAsync<ModerationTaskRow[]>([]);
const reasonCodesTask = useAsync<{ list: { code: string; label: string }[]; appeal_window_days: number }>();

const selectedRoomId = ref<string | null>(null);
const lastRefreshedAt = ref<string>('');
const previewFailed = ref(false);
const claimError = ref<ApiError | null>(null);
const submitting = ref(false);

const dialogVisible = ref(false);
const pendingAction = ref<ModerationAction | null>(null);

let timer: number | null = null;

const rooms = computed(() => sortRoomsByRisk(roomsTask.data.value ?? []));
const tasks = computed(() => tasksTask.data.value ?? []);
const reasonCodes = computed(() => reasonCodesTask.data.value?.list ?? []);
const appealWindowDays = computed(() => reasonCodesTask.data.value?.appeal_window_days ?? 0);

const selectedRoom = computed<AdminRoomRow | null>(
  () => rooms.value.find((room) => room.room_id === selectedRoomId.value) ?? rooms.value[0] ?? null,
);

const highRiskCount = computed(
  () => rooms.value.filter((room) => room.alerts.some((alert) => alert.confidence >= 80)).length,
);

const selectedTask = computed<ModerationTaskRow | null>(
  () =>
    tasks.value.find(
      (task) => selectedRoom.value !== null && task.room_no === selectedRoom.value.room_no,
    ) ?? null,
);

const lockedByOther = computed(
  () =>
    selectedRoom.value !== null &&
    isRoomLockedByOther(selectedRoom.value, session.session?.operator_id ?? ''),
);

const lockMessage = computed(() =>
  selectedRoom.value === null ? null : lockNotice(selectedRoom.value, session.session?.operator_id ?? ''),
);

const alerts = computed(() => (selectedRoom.value === null ? [] : sortAlerts(selectedRoom.value.alerts)));

const evidenceFor = computed(() => {
  const first = alerts.value[0];
  return {
    snapshot_at: first?.occurred_at ?? '',
    rule_id: first?.rule_id ?? '',
    confidence: first?.confidence ?? 0,
  };
});

async function refresh(): Promise<void> {
  await Promise.all([
    roomsTask.run(async () => (await roomsApi.list()).list),
    tasksTask.run(async () => (await moderationApi.tasks()).list),
  ]);
  if (reasonCodesTask.data.value === null) {
    await reasonCodesTask.run(() => moderationApi.reasonCodes());
  }
  lastRefreshedAt.value = new Date().toLocaleTimeString('zh-CN');
  if (selectedRoomId.value === null && rooms.value.length > 0) {
    selectedRoomId.value = rooms.value[0].room_id;
  }
}

function openDisposition(action: ModerationAction): void {
  pendingAction.value = action;
  dialogVisible.value = true;
}

async function onConfirm(draft: DispositionDraft): Promise<void> {
  const room = selectedRoom.value;
  if (room === null) {
    return;
  }
  submitting.value = true;
  try {
    const result = await moderationApi.submitAction(draft, {
      taskId: selectedTask.value?.task_id ?? '',
      roomId: room.room_id,
      sessionId: `session_${room.room_id}`,
    });
    ElMessage({ type: 'success', message: `处置已提交并写入审计（${result.action_id}）`, duration: TOAST_DURATION_MS.success });
    dialogVisible.value = false;
    await refresh();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    // 提交失败：**保留输入**（弹窗不关闭），错误按行内/Toast 呈现。
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  } finally {
    submitting.value = false;
  }
}

async function claim(task: ModerationTaskRow): Promise<void> {
  claimError.value = null;
  try {
    await moderationApi.claim(task.task_id);
    ElMessage({ type: 'success', message: '已认领该任务', duration: TOAST_DURATION_MS.success });
    await refresh();
  } catch (caught) {
    claimError.value = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
  }
}

onMounted(async () => {
  await refresh();
  timer = globalThis.setInterval(() => {
    void refresh();
  }, 5000);
});

onBeforeUnmount(() => {
  if (timer !== null) {
    globalThis.clearInterval(timer);
  }
});
</script>

<template>
  <div class="wb">
    <div class="wb__head">
      <h2 class="wb__title">内容审核工作台</h2>
      <el-tag type="danger" effect="dark">高危告警 {{ highRiskCount }}</el-tag>
      <el-tag type="warning">待审队列 {{ tasks.length }}</el-tag>
      <span class="text-min">自动刷新 · 每 5s · 上次刷新 {{ lastRefreshedAt || '—' }}</span>
      <el-button class="wb__refresh" size="small" @click="refresh">立即刷新</el-button>
    </div>

    <el-row :gutter="16">
      <!-- 左栏：实时巡房（不发空态） -->
      <el-col :xs="24" :md="7">
        <el-card shadow="never" class="wb__card">
          <template #header>
            <div class="wb__cardhead">
              <span>直播中房间 · 按风险分排序</span>
              <span class="text-min">{{ rooms.length }} 间</span>
            </div>
          </template>
          <PageState :loading="roomsTask.loading.value && rooms.length === 0" :error="roomsTask.error.value" @retry="refresh">
            <ul class="rooms">
              <li
                v-for="room in rooms"
                :key="room.room_id"
                class="rooms__item"
                :class="{ 'rooms__item--active': selectedRoom?.room_id === room.room_id }"
                @click="selectedRoomId = room.room_id"
              >
                <div class="rooms__row">
                  <strong>{{ room.anchor_name }} · {{ room.category_name }}</strong>
                  <span class="adm-num" :class="room.risk_score >= 80 ? 'text-danger' : 'text-secondary'">
                    {{ room.risk_score }}
                  </span>
                </div>
                <div class="text-min">
                  房号 {{ room.room_no }} · 👁{{ room.viewer_cnt }}
                  <template v-if="room.locked_by !== null"> · 🔒 {{ room.locked_by_name }}</template>
                </div>
              </li>
            </ul>
          </PageState>
        </el-card>
      </el-col>

      <!-- 中栏：预览 + 告警时间线 -->
      <el-col :xs="24" :md="9">
        <el-card v-if="selectedRoom" shadow="never" class="wb__card">
          <template #header>
            <div class="wb__cardhead">
              <span>房间 {{ selectedRoom.room_no }} · {{ selectedRoom.anchor_name }}</span>
              <el-tag size="small" :type="selectedRoom.status === 'LIVE' ? 'success' : 'info'">
                {{ selectedRoom.status === 'LIVE' ? '● 直播中' : selectedRoom.status }}
              </el-tag>
            </div>
          </template>

          <div class="preview">
            <template v-if="previewFailed">
              <!-- 预览加载失败：处置区仍可用（D-ADM-001 错误态） -->
              <span>预览加载失败。可继续处置，或点击重试拉取画面。</span>
              <el-button size="small" @click="previewFailed = false">重试</el-button>
            </template>
            <template v-else>
              <span>实时预览（含弹幕浮层）</span>
              <el-button size="small" text @click="previewFailed = true">模拟加载失败</el-button>
            </template>
          </div>

          <el-divider content-position="left">AI 告警时间线</el-divider>
          <el-empty v-if="alerts.length === 0" description="该房间暂无告警" :image-size="48" />
          <ul v-else class="alerts" aria-live="polite">
            <li v-for="alert in alerts" :key="alert.alert_id" class="alerts__item">
              <el-tag size="small" effect="plain">{{ ALERT_TYPE_LABELS[alert.type] }}</el-tag>
              <div class="alerts__body">
                <div>{{ alert.summary }}</div>
                <div class="text-min">
                  {{ alert.occurred_at }} · 规则 {{ alert.rule_id }} · 置信度 {{ confidence(alert.confidence) }}
                </div>
              </div>
              <el-button size="small" text>查看快照</el-button>
            </li>
          </ul>
        </el-card>
      </el-col>

      <!-- 右栏：处置区（独立接口，预览失败不影响） -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="wb__card">
          <template #header>
            <div class="wb__cardhead">
              <span>处置</span>
              <span class="text-min">不可逆动作需二次确认</span>
            </div>
          </template>

          <el-alert
            v-if="lockMessage"
            class="wb__lock"
            type="warning"
            show-icon
            :closable="false"
            :title="lockMessage"
          />

          <el-alert
            v-if="claimError"
            class="wb__lock"
            type="error"
            show-icon
            :closable="false"
            title="认领未成功"
            :description="claimError.userMessage"
          />

          <div v-if="selectedTask" class="wb__claim">
            <span class="text-min">待审任务 {{ selectedTask.task_id }} · {{ selectedTask.risk_level }}</span>
            <el-button size="small" :disabled="lockedByOther" @click="claim(selectedTask)">认领任务</el-button>
          </div>

          <div class="actions">
            <el-button
              v-for="action in ACTION_ORDER"
              :key="action"
              class="actions__btn"
              :type="ACTION_SPECS[action].danger ? 'danger' : 'default'"
              :plain="!ACTION_SPECS[action].danger"
              :disabled="lockedByOther || selectedRoom === null"
              @click="openDisposition(action)"
            >
              {{ ACTION_SPECS[action].label }}
            </el-button>
          </div>

          <p class="text-min">⚠ 断流 / 封禁将同步通知主播并写入审计，处置记录不可物理删除（AC-7）。</p>

          <el-divider content-position="left">待审队列</el-divider>
          <el-empty
            v-if="tasks.length === 0"
            :image-size="48"
            :description="`当前无待审内容（上次刷新 ${lastRefreshedAt || '—'}）`"
          />
          <ul v-else class="queue">
            <li v-for="task in tasks" :key="task.task_id" class="queue__item">
              <div>
                <strong>{{ task.anchor_name }}</strong>
                <span class="text-min"> · 房号 {{ task.room_no }} · {{ task.risk_level }}</span>
              </div>
              <div class="text-min">{{ task.reason_summary }}</div>
            </li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <DispositionDialog
      v-model="dialogVisible"
      :action="pendingAction"
      :room-no="selectedRoom?.room_no ?? 0"
      :anchor-name="selectedRoom?.anchor_name ?? ''"
      :reason-codes="reasonCodes"
      :operator-name="session.operatorName"
      :appeal-window-days="appealWindowDays"
      :evidence="evidenceFor"
      :submitting="submitting"
      @confirm="onConfirm"
    />
  </div>
</template>

<style scoped>
.wb__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.wb__title {
  margin: 0 8px 0 0;
  font-size: 16px;
}

.wb__refresh {
  margin-left: auto;
}

.wb__card {
  margin-bottom: 16px;
}

.wb__cardhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wb__lock {
  margin-bottom: 12px;
}

.wb__claim {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rooms {
  margin: 0;
  padding: 0;
  list-style: none;
}

.rooms__item {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
}

.rooms__item--active {
  background: var(--brand-050);
}

.rooms__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 148px;
  border: 1px solid var(--n-200);
  border-radius: 6px;
  background: #111827;
  color: #d1d5db;
  font-size: 12px;
  text-align: center;
}

.alerts {
  margin: 0;
  padding: 0;
  list-style: none;
}

.alerts__item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--n-200);
}

.alerts__body {
  flex: 1;
  font-size: 12px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.actions__btn {
  height: var(--adm-hit-target);
}

.queue {
  margin: 0;
  padding: 0;
  list-style: none;
}

.queue__item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--n-200);
  font-size: 12px;
}
</style>
