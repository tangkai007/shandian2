<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { DispositionEvidence, ModerationAction, NotifyChannel } from '@/types/contract';
import {
  ACTION_SPECS,
  CONFIRM_LOCK_MS,
  emptyDraft,
  errorFor,
  NOTIFY_CHANNEL_LABELS,
  REMARK_MIN_LENGTH,
  validateDisposition,
  type DispositionDraft,
} from '@/domain/moderation';
import { confidence } from '@/domain/money';

/**
 * 处置确认与留痕（D-ADM-002）。
 *
 * 硬口径：
 * - 不可逆动作（断流 / 封禁）**二次确认**，弹窗内明示不可逆后果；
 * - 原因码必选、备注 ≥ 5 字（不足 → 行内错误，服务端同判 `40001`）；
 * - 证据块**自动附带、不可编辑**；
 * - 确认按钮点击后 3s 内禁用（显示「处置中…」），防重复提交；
 * - 高危动作颜色 + 图标 + 文案三重编码。
 */
const props = defineProps<{
  modelValue: boolean;
  action: ModerationAction | null;
  roomNo: number;
  anchorName: string;
  reasonCodes: { code: string; label: string }[];
  operatorName: string;
  appealWindowDays: number;
  evidence: Pick<DispositionEvidence, 'snapshot_at' | 'rule_id' | 'confidence'>;
  submitting: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  confirm: [draft: DispositionDraft];
}>();

const draft = reactive<DispositionDraft>(emptyDraft());
const touched = ref(false);
const locked = ref(false);
const notifyChannels: NotifyChannel[] = ['INBOX', 'PUSH', 'SMS'];

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const spec = computed(() => (props.action === null ? null : ACTION_SPECS[props.action]));

const errors = computed(() => (touched.value ? validateDisposition({ ...draft, action: props.action }) : []));

const confirmLabel = computed(() => {
  if (props.submitting || locked.value) {
    return '处置中…';
  }
  return spec.value?.buttonLabel ?? '确认';
});

/** 二次确认摘要：即将对「小雅」(房号 10241) 执行：断流。 */
const summary = computed(() =>
  props.action === null || !spec.value
    ? ''
    : `即将对「${props.anchorName}」(房号 ${props.roomNo}) 执行：${spec.value.confirmActionName}`,
);

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      draft.action = props.action;
      draft.reasonCode = '';
      draft.remark = '';
      draft.notifyChannels = ['INBOX'];
      draft.evidenceUrls = [];
      touched.value = false;
      locked.value = false;
    }
  },
);

function onConfirm(): void {
  touched.value = true;
  const list = validateDisposition({ ...draft, action: props.action });
  if (list.length > 0) {
    return;
  }
  locked.value = true;
  globalThis.setTimeout(() => {
    locked.value = false;
  }, CONFIRM_LOCK_MS);
  emit('confirm', { ...draft, action: props.action });
}
</script>

<template>
  <el-dialog v-model="visible" title="处置确认" width="560px" :close-on-click-modal="false">
    <div class="disp">
      <el-alert
        v-if="spec?.danger"
        class="disp__danger"
        type="error"
        show-icon
        :closable="false"
        :title="`⛔ ${summary}`"
        :description="spec.consequence"
      />
      <p v-else class="disp__normal">
        <strong>{{ summary }}</strong>
        <br />
        <span class="text-secondary">{{ spec?.consequence }}</span>
      </p>

      <el-form label-width="96px" label-position="left">
        <el-form-item label="原因码" :error="errorFor(errors, 'reasonCode')">
          <el-select v-model="draft.reasonCode" placeholder="选择原因码…" style="width: 100%">
            <el-option
              v-for="item in reasonCodes"
              :key="item.code"
              :label="item.label"
              :value="item.code"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="`备注（≥${REMARK_MIN_LENGTH} 字）`" :error="errorFor(errors, 'remark')">
          <el-input
            v-model="draft.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="记录判断依据…"
          />
        </el-form-item>

        <el-form-item label="通知主播">
          <el-checkbox-group v-model="draft.notifyChannels">
            <el-checkbox v-for="channel in notifyChannels" :key="channel" :value="channel">
              {{ NOTIFY_CHANNEL_LABELS[channel] }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <el-card shadow="never" class="disp__evidence">
        <template #header>
          <span>证据留存（自动附带，不可编辑）</span>
        </template>
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="快照时间">{{ evidence.snapshot_at || '—' }}</el-descriptions-item>
          <el-descriptions-item label="规则">{{ evidence.rule_id || '—' }}</el-descriptions-item>
          <el-descriptions-item label="置信度">
            {{ confidence(evidence.confidence) }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ operatorName }}（审核员）
          </el-descriptions-item>
          <el-descriptions-item label="申诉时限" :span="2">
            {{ appealWindowDays }} 天（OP-17 已定 · 来自后台配置）
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        :type="spec?.danger ? 'danger' : 'primary'"
        :loading="submitting || locked"
        @click="onConfirm"
      >
        {{ confirmLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.disp__danger {
  margin-bottom: 16px;
}

.disp__normal {
  margin: 0 0 16px;
  line-height: 1.7;
}

.disp__evidence {
  margin-top: 8px;
}
</style>
