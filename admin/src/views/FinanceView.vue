<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ApiError } from '@/api/client';
import { financeApi } from '@/api/admin';
import MoneyText from '@/components/MoneyText.vue';
import PageState from '@/components/PageState.vue';
import { useAsync } from '@/composables/useAsync';
import { TOAST_DURATION_MS } from '@/domain/messages';
import {
  belowMinimum,
  reconcile,
  settlementLines,
  SETTLEMENT_LABELS,
  taxModeBadge,
  validateWithdrawalReview,
  withdrawalColumns,
  WITHDRAWAL_STATUS_LABELS,
  type WithdrawalColumnKey,
} from '@/domain/finance';
import { fenToYuan } from '@/domain/money';
import {
  ErrorCodes,
  type AnchorTaxMode,
  type InvoiceRow,
  type SettlementBill,
  type WithdrawalRow,
} from '@/types/contract';

/**
 * 财务对账与提现审核（D-ADM-003 · FR-11.3 / FR-5.4 · BR-4 / BR-6 · AC-8）。
 *
 * 硬口径：
 * - 结算阶梯 ① … ⑦ 与主播端**逐字同名**（§11.2 A 域）；
 * - 扣减顺序「渠道费 / 平台流转税先扣后分」：`④=①−②−③`、`⑤+⑤=④`、`⑦=⑤−⑥`；
 * - **对账差异 ≠ 0 → 冻结出款**；
 * - 提现列随 `anchor_tax_mode` 变化：`SETTLE` 收起「代扣个税」列；
 * - 非财务角色 → 整页 403（由路由守卫拦截），**不显示金额**。
 */
const settlementsTask = useAsync<SettlementBill[]>([]);
const billTask = useAsync<SettlementBill>();
const withdrawalsTask = useAsync<WithdrawalRow[]>([]);
const invoicesTask = useAsync<InvoiceRow[]>([]);

const period = ref<string>('');
const taxMode = ref<AnchorTaxMode>('SETTLE');
const minimumFen = ref<number>(0);

const reviewVisible = ref(false);
const reviewTarget = ref<WithdrawalRow | null>(null);
const reviewDecision = ref<'APPROVE' | 'REJECT'>('APPROVE');
const reviewReason = ref('');
const reviewRemark = ref('');
const reviewError = ref<string | null>(null);
const submitting = ref(false);

const columns = computed(() => withdrawalColumns(taxMode.value));
function label(key: WithdrawalColumnKey): string {
  return columns.value.find((column) => column.key === key)?.label ?? '';
}
function has(key: WithdrawalColumnKey): boolean {
  return columns.value.some((column) => column.key === key);
}

const bill = computed(() => billTask.data.value ?? null);
const lines = computed(() => (bill.value === null ? [] : settlementLines(bill.value)));
const checks = computed(() => (bill.value === null ? null : reconcile(bill.value)));
const frozen = computed(() => checks.value !== null && checks.value.frozen);

const pendingCount = computed(
  () => (withdrawalsTask.data.value ?? []).filter((row) => row.status === 'PENDING').length,
);

/** 状态标签（避免在模板里写类型断言）。 */
function statusLabel(status: WithdrawalRow['status']): string {
  return WITHDRAWAL_STATUS_LABELS[status];
}

function statusTagType(status: WithdrawalRow['status']): 'warning' | 'success' | 'danger' {
  if (status === 'PENDING') {
    return 'warning';
  }
  return status === 'PAID' ? 'success' : 'danger';
}

async function loadWithdrawals(): Promise<void> {
  await withdrawalsTask.run(async () => {
    const page = await financeApi.withdrawals(period.value.length === 0 ? undefined : period.value);
    taxMode.value = page.anchor_tax_mode;
    minimumFen.value = page.minimum_fen;
    return page.list;
  });
}

async function loadAll(): Promise<void> {
  await settlementsTask.run(async () => (await financeApi.settlements()).list);
  const list = settlementsTask.data.value ?? [];
  if (list.length > 0 && period.value.length === 0) {
    period.value = list[0].period;
  }
  if (period.value.length > 0) {
    await billTask.run(() => financeApi.bill(period.value));
  }
  await Promise.all([
    loadWithdrawals(),
    invoicesTask.run(async () => (await financeApi.invoices()).list),
  ]);
}

async function onPeriodChange(): Promise<void> {
  if (period.value.length === 0) {
    return;
  }
  await billTask.run(() => financeApi.bill(period.value));
  await loadWithdrawals();
}

function openReview(row: WithdrawalRow, decision: 'APPROVE' | 'REJECT'): void {
  reviewTarget.value = row;
  reviewDecision.value = decision;
  reviewReason.value = '';
  reviewRemark.value = '';
  reviewError.value = null;
  reviewVisible.value = true;
}

async function submitReview(): Promise<void> {
  const row = reviewTarget.value;
  if (row === null) {
    return;
  }
  const errors = validateWithdrawalReview({ decision: reviewDecision.value, reasonCode: reviewReason.value });
  if (errors.length > 0) {
    reviewError.value = errors[0].message;
    return;
  }
  if (reviewDecision.value === 'APPROVE' && belowMinimum(row.pre_tax_amount, minimumFen.value)) {
    reviewError.value = `本次提现金额未达最低限额。平台最低提现 ${fenToYuan(minimumFen.value)}（OP-16）。当前可提现 ${fenToYuan(row.pre_tax_amount)}，请调整金额。`;
    return;
  }
  submitting.value = true;
  try {
    await financeApi.review(row.order_no, reviewDecision.value, reviewReason.value, reviewRemark.value, {
      minimum: fenToYuan(minimumFen.value, { withSymbol: false }),
      available: fenToYuan(row.pre_tax_amount, { withSymbol: false }),
    });
    ElMessage({
      type: 'success',
      message: reviewDecision.value === 'APPROVE' ? '已通过并出款' : '已驳回并通知主播端',
      duration: TOAST_DURATION_MS.success,
    });
    reviewVisible.value = false;
    await loadWithdrawals();
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    reviewError.value = error.userMessage;
  } finally {
    submitting.value = false;
  }
}

async function generate(): Promise<void> {
  try {
    await financeApi.generate(period.value === '' ? '' : period.value);
    ElMessage({ type: 'success', message: '已触发月结（幂等：同期不可重复出账）', duration: TOAST_DURATION_MS.success });
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  }
}

/** 导出 CSV：列集同样随模式变化（SETTLE 不含代扣个税列）。 */
function exportCsv(): void {
  const rows = withdrawalsTask.data.value ?? [];
  const header = columns.value
    .filter((column) => column.key !== 'actions')
    .map((column) => column.label)
    .join(',');
  const body = rows.map((row) => {
    const cells: string[] = [];
    for (const column of columns.value) {
      if (column.key === 'actions') {
        continue;
      }
      switch (column.key) {
        case 'order_no':
          cells.push(row.order_no);
          break;
        case 'period':
          cells.push(row.period);
          break;
        case 'anchor':
          cells.push(`${row.anchor_name}（${row.real_name}）`);
          break;
        case 'pre_tax_amount':
          cells.push(fenToYuan(row.pre_tax_amount, { withSymbol: false }));
          break;
        case 'tax_withheld':
          cells.push(fenToYuan(row.tax_withheld, { withSymbol: false }));
          break;
        case 'net_amount':
          cells.push(fenToYuan(row.net_amount, { withSymbol: false }));
          break;
        case 'bank':
          cells.push(row.bank_account_masked);
          break;
        case 'status':
          cells.push(WITHDRAWAL_STATUS_LABELS[row.status]);
          break;
        default:
          cells.push('');
      }
    }
    return cells.join(',');
  });
  const csv = [header, ...body].join('\n');
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `withdrawals-${period.value || 'all'}-${taxMode.value}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}

async function issueInvoice(row: InvoiceRow): Promise<void> {
  try {
    await financeApi.issueInvoice(row.invoice_id);
    ElMessage({ type: 'success', message: '发票已开具', duration: TOAST_DURATION_MS.success });
    await invoicesTask.run(async () => (await financeApi.invoices()).list);
  } catch (caught) {
    const error = caught instanceof ApiError ? caught : new ApiError(ErrorCodes.systemError, String(caught));
    ElMessage({ type: 'error', message: error.userMessage, duration: TOAST_DURATION_MS.failure });
  }
}

onMounted(loadAll);
</script>

<template>
  <div>
    <div class="head">
      <h2 class="page-title">提现审核</h2>
      <el-tag type="warning">待审 {{ pendingCount }}</el-tag>
      <el-tag effect="plain">{{ taxModeBadge(taxMode) }}</el-tag>
      <el-select v-model="period" class="head__period" placeholder="结算月" @change="onPeriodChange">
        <el-option v-for="item in settlementsTask.data.value ?? []" :key="item.bill_id" :label="`结算月 ${item.period}`" :value="item.period" />
      </el-select>
      <el-button @click="generate">触发月结</el-button>
      <el-button type="primary" plain @click="exportCsv">导出 CSV</el-button>
    </div>

    <!-- 对账校验（AC-8）-->
    <el-card shadow="never" class="recon">
      <template #header>
        <div class="recon__head">
          <span>对账校验（AC-8 · 误差为 0）· 结算月 {{ period || '—' }}</span>
          <el-tag size="small" effect="plain">{{ taxModeBadge(taxMode) }}</el-tag>
        </div>
      </template>

      <PageState :loading="billTask.loading.value" :error="billTask.error.value" @retry="onPeriodChange">
        <div v-if="frozen" class="recon__frozen">
          <el-alert
            type="error"
            show-icon
            :closable="false"
            title="对账存在差异 → 已冻结出款"
            :description="`差异（含分摊尾差）${fenToYuan(checks?.diff ?? 0)}。④＝①−②−③、⑤＋⑤＝④、⑦＝⑤−⑥ 任一不等即判差异，本期不得出款。`"
          />
        </div>

        <div class="recon__grid">
          <div v-for="line in lines" :key="`${line.no}-${line.key}`" class="recon__line">
            <span class="recon__no">{{ line.no }}</span>
            <span class="recon__label">{{ line.label }}</span>
            <span class="recon__value">
              <template v-if="line.deduction">−</template>
              <MoneyText :value="line.value" />
            </span>
          </div>
        </div>

        <div class="recon__diff">
          <span>差异（含分摊尾差）</span>
          <strong class="adm-num" :class="frozen ? 'text-danger' : 'text-success'">
            {{ fenToYuan(checks?.diff ?? 0) }} {{ frozen ? '✗' : '✓' }}
          </strong>
        </div>

        <el-descriptions class="recon__equalities" :column="2" size="small" border>
          <el-descriptions-item label="④ − (①−②−③)">{{ checks?.distributableResidual ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="(⑤+平台留存) − ④">{{ checks?.splitResidual ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="⑦ − (⑤−⑥)">{{ checks?.netResidual ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="Σ渠道费+尾差 − ②">{{ checks?.channelResidual ?? 0 }}</el-descriptions-item>
        </el-descriptions>

        <p class="text-min">
          7 项与主播端 D-HOST-007 逐字同名；键名以 S1a v0.4 `settlement_bill` 为准
          （{{ Object.values(SETTLEMENT_LABELS).join(' / ') }}）。扣减顺序固定为「渠道费 / 平台流转税先扣后分」（OP-15）。
        </p>
      </PageState>
    </el-card>

    <!-- 提现申请队列 -->
    <el-card shadow="never" class="queues">
      <template #header>提现申请队列</template>
      <PageState
        :loading="withdrawalsTask.loading.value"
        :error="withdrawalsTask.error.value"
        empty-text="本月暂无待审提现"
        @retry="loadWithdrawals"
      >
        <el-table :data="withdrawalsTask.data.value ?? []" border>
          <el-table-column prop="order_no" :label="label('order_no')" width="180" />
          <el-table-column prop="period" :label="label('period')" width="100" />
          <el-table-column :label="label('anchor')" width="160">
            <template #default="{ row }">{{ row.anchor_name }}（{{ row.real_name }}）</template>
          </el-table-column>
          <el-table-column :label="label('pre_tax_amount')" width="140">
            <template #default="{ row }"><MoneyText :value="row.pre_tax_amount" /></template>
          </el-table-column>
          <el-table-column v-if="has('tax_withheld')" :label="label('tax_withheld')" width="130">
            <template #default="{ row }"><MoneyText :value="row.tax_withheld" /></template>
          </el-table-column>
          <el-table-column :label="label('net_amount')" width="130">
            <template #default="{ row }"><MoneyText :value="row.net_amount" /></template>
          </el-table-column>
          <el-table-column :label="label('bank')" width="180">
            <template #default="{ row }">
              {{ row.bank_account_masked }}
              <el-tag v-if="!row.bank_name_match" size="small" type="danger">姓名与实名不一致</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="label('status')" width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
              <span v-if="row.status_note" class="text-min"> · {{ row.status_note }}</span>
              <span v-if="row.paid_at" class="text-min"> · {{ row.paid_at }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="label('actions')" width="170">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="row.status !== 'PENDING' || frozen"
                @click="openReview(row, 'APPROVE')"
              >
                通过
              </el-button>
              <el-button
                size="small"
                :disabled="row.status !== 'PENDING'"
                @click="openReview(row, 'REJECT')"
              >
                驳回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageState>
    </el-card>

    <!-- 电子发票（OP-11 / OP-22）-->
    <el-card shadow="never" class="queues">
      <template #header>电子发票</template>
      <PageState :loading="invoicesTask.loading.value" :error="invoicesTask.error.value" empty-text="暂无发票申请" @retry="loadAll">
        <el-table :data="invoicesTask.data.value ?? []" border>
          <el-table-column prop="invoice_id" label="发票 ID" width="120" />
          <el-table-column prop="order_no" label="订单号" width="160" />
          <el-table-column prop="buyer" label="抬头" width="140" />
          <el-table-column label="金额" width="130">
            <template #default="{ row }"><MoneyText :value="row.amount_fen" /></template>
          </el-table-column>
          <el-table-column prop="channel" label="渠道" width="110" />
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ISSUED' ? 'success' : 'warning'">
                {{ row.status === 'ISSUED' ? '已开具' : '待开具' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="{ row }">
              <el-link v-if="row.invoice_url" :href="row.invoice_url" target="_blank" type="primary">下载</el-link>
              <el-button v-else size="small" type="primary" plain @click="issueInvoice(row)">开具</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageState>
    </el-card>

    <el-dialog
      v-model="reviewVisible"
      :title="reviewDecision === 'APPROVE' ? '通过提现并出款' : '驳回提现申请'"
      width="520px"
    >
      <el-alert
        v-if="frozen"
        type="error"
        show-icon
        :closable="false"
        title="本期对账存在差异，已冻结出款"
        description="差异未归零前不得出款（AC-8）。请先核对渠道明细与分摊尾差。"
      />
      <el-form class="mt12" label-width="90px" label-position="left">
        <el-form-item label="申请单号">{{ reviewTarget?.order_no }}</el-form-item>
        <el-form-item label="申请金额"><MoneyText :value="reviewTarget?.pre_tax_amount ?? 0" /></el-form-item>
        <el-form-item v-if="reviewDecision === 'REJECT'" label="原因码">
          <el-select v-model="reviewReason" placeholder="选择原因码…" style="width: 100%">
            <el-option label="W-1001 未达起提金额（OP-16）" value="W-1001" />
            <el-option label="W-1002 银行卡姓名与实名不一致（BR-6）" value="W-1002" />
            <el-option label="W-1003 对账存在差异，暂缓出款（AC-8）" value="W-1003" />
            <el-option label="W-9001 其它" value="W-9001" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reviewRemark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert v-if="reviewError" type="error" show-icon :closable="false" :title="reviewError" />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          :type="reviewDecision === 'APPROVE' ? 'primary' : 'danger'"
          :loading="submitting"
          :disabled="reviewDecision === 'APPROVE' && frozen"
          @click="submitReview"
        >
          {{ reviewDecision === 'APPROVE' ? '确认出款' : '确认驳回' }}
        </el-button>
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
  margin: 0 8px 0 0;
  font-size: 16px;
}

.head__period {
  width: 180px;
  margin-left: auto;
}

.recon {
  margin-bottom: 16px;
}

.recon__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recon__frozen {
  margin-bottom: 12px;
}

.recon__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 24px;
}

.recon__line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--n-200);
  font-size: 13px;
}

.recon__no {
  width: 20px;
  color: var(--n-500);
}

.recon__label {
  flex: 1;
  color: var(--n-600);
}

.recon__value {
  font-variant-numeric: tabular-nums;
}

.recon__diff {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 0;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--n-050);
}

.recon__equalities {
  margin-bottom: 8px;
}

.queues {
  margin-bottom: 16px;
}

.mt12 {
  margin-top: 12px;
}
</style>
