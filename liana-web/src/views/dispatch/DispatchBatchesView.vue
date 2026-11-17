<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">批次管理</h3>
          <p class="mt-1 text-sm text-gray-500">装包获 OMS 确认后按本段路线编批。预告确认后申请封单，封单固定清单版本，获确认后才能交接；刷新可查看进度。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="refresh">刷新</button>
          <button class="btn btn-primary" :disabled="loading || !selectedBagNos.length" @click="createBatch">生成批次与路单</button>
        </div>
      </div>
    </div>

    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h4 class="text-sm font-semibold text-gray-900">待编批总包</h4>
        <span class="text-xs text-gray-500">已选 {{ selectedBagNos.length }} 件</span>
      </div>
      <div class="mt-3 max-h-72 overflow-auto">
        <table class="table">
          <thead><tr><th></th><th>总包号</th><th>起点</th><th>终点</th><th>路由</th><th>邮件数</th><th>状态</th></tr></thead>
          <tbody>
            <tr v-for="bag in availableBags" :key="bag.bagNo">
              <td><input v-model="selectedBagNos" type="checkbox" :value="bag.bagNo" /></td>
              <td class="font-medium">{{ bag.bagNo }}</td><td>{{ bag.originFacilityCode }}</td><td>{{ bag.destinationFacilityCode }}</td>
              <td>{{ bag.routeCode || '-' }}</td><td>{{ bag.mailCount }}</td><td>{{ bag.status }}</td>
            </tr>
            <tr v-if="!availableBags.length"><td colspan="7" class="text-center text-sm text-gray-500">暂无可编批总包</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #actions="{ row }">
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading || row.status !== 'PENDING'" @click="approve(row)">审批通过</button>
          <button class="btn btn-primary" :disabled="loading || row.manifestSyncStatus !== 'SENT' || row.manifestSealStatus !== 'NOT_REQUESTED' || !['PENDING', 'APPROVED'].includes(row.status)" @click="seal(row)">申请封单</button>
          <button class="btn btn-secondary" :disabled="loading || !canCancel(row)" @click="openCancel(row)">作废路单</button>
        </div>
      </template>
    </GenericListView>
    <DetailDrawer :open="Boolean(cancelRow)" title="作废路单" :subtitle="cancelRow?.manifestNo" @close="cancelRow = null">
      <div class="space-y-4">
        <p class="text-sm text-gray-600">仅支持未交接、未关联运输任务的路单。接收端确认作废后释放本次占用，原清单和封单记录保留。直封转运总包请从接收勾核页面重新编制下一段。</p>
        <label class="block text-sm font-medium" for="cancel-reason">作废原因</label>
        <textarea id="cancel-reason" v-model="cancelReason" class="input w-full" rows="4" maxlength="512" placeholder="例如：本次配发安排撤销" />
        <button class="btn btn-primary" :disabled="loading || !cancelReason.trim()" @click="cancel">提交作废申请</button>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { dispatchApi } from '../../lib/api';
import { notifyApiError } from '../../lib/apiErrorEvents';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const bags = ref([]);
const selectedBagNos = ref([]);
const loading = ref(false);
const cancelRow = ref(null);
const cancelReason = ref('');
const statusOptions = ['PENDING', 'APPROVED', 'HANDED_OFF', 'CANCELLING', 'CANCELLED'];
const columns = [
  { key: 'batchNo', label: '批次号' },
  { key: 'manifestNo', label: '路单号' },
  { key: 'sourceOrgCode', label: '本段交出机构' },
  { key: 'destinationOrgCode', label: '本段接收机构' },
  { key: 'manifestSyncLabel', label: '预告状态' },
  { key: 'manifestSealLabel', label: '封单状态' },
  { key: 'manifestSealError', label: '封单说明' },
  { key: 'manifestCancelLabel', label: '作废状态' },
  { key: 'manifestCancelReason', label: '作废原因' },
  { key: 'manifestCancelError', label: '作废说明' },
  { key: 'bagNo', label: '邮袋号' },
  { key: 'bagNos', label: '总包清单' },
  { key: 'routeCode', label: '路线' },
  { key: 'status', label: '状态' },
  { key: 'approvedBy', label: '审批人' },
  { key: 'approvedAt', label: '审批时间' },
  { key: 'actions', label: '操作' },
];
const selectedBags = computed(() => bags.value.filter((bag) => selectedBagNos.value.includes(bag.bagNo)));
const batchedBagNos = computed(() => new Set(bags.value.filter(bag => bag.manifestNo).map(bag => bag.bagNo)));
const availableBags = computed(() => {
  const unbatched = bags.value.filter((bag) => bag.packingSyncStatus === 'CONFIRMED' && !batchedBagNos.value.has(bag.bagNo));
  if (!selectedBags.value.length) return unbatched.filter((bag) => ['CREATED', 'REVIEWED'].includes(bag.status));
  const first = selectedBags.value[0];
  return unbatched.filter((bag) => ['CREATED', 'REVIEWED'].includes(bag.status)
    && bag.originFacilityCode === first.originFacilityCode
    && bag.destinationFacilityCode === first.destinationFacilityCode
    && bag.routeCode === first.routeCode);
});

watch(availableBags, (next) => {
  const available = new Set(next.map((bag) => bag.bagNo));
  const selected = selectedBagNos.value.filter((bagNo) => available.has(bagNo));
  if (selected.length !== selectedBagNos.value.length) selectedBagNos.value = selected;
});

async function refresh() {
  loading.value = true;
  try {
    const [batchRows, bagRows] = await Promise.all([
      dispatchApi.listBatches(session.token),
      dispatchApi.listBags(session.token),
    ]);
    const syncLabels = { SENT: '接收端已确认', PENDING: '待发送', RETRY: '等待重试', SENDING: '发送中', NOT_CREATED: '未创建预告' };
    const sealLabels = { SENT: '已封单', PENDING: '待确认', RETRY: '等待重试', SENDING: '确认中', CONFLICT: '封单冲突', NOT_REQUESTED: '未申请' };
    const cancelLabels = { SENT: '已作废', PENDING: '待确认', RETRY: '等待重试', SENDING: '确认中', CONFLICT: '作废冲突（保持阻止交接）', NOT_REQUESTED: '未申请' };
    rows.value = batchRows.map((row) => ({ ...row,
      manifestSyncLabel: syncLabels[row.manifestSyncStatus] || row.manifestSyncStatus || '未知',
      manifestSealLabel: sealLabels[row.manifestSealStatus] || row.manifestSealStatus || '未知',
      manifestCancelLabel: cancelLabels[row.manifestCancelStatus] || row.manifestCancelStatus || '未知',
    }));
    bags.value = bagRows;
  } finally {
    loading.value = false;
  }
}

async function createBatch() {
  loading.value = true;
  try {
    await dispatchApi.createBatch({ bagNos: selectedBagNos.value }, session.token);
    selectedBagNos.value = [];
    await refresh();
  } catch (error) {
    notifyApiError(error);
  } finally {
    loading.value = false;
  }
}

async function approve(row) {
  await dispatchApi.approveBatch(row.batchNo, { approvedBy: session.user?.userId || null }, session.token);
  await refresh();
}

async function seal(row) {
  loading.value = true;
  try {
    await dispatchApi.sealBatch(row.batchNo, session.token);
    await refresh();
  } catch (error) { notifyApiError(error); }
  finally { loading.value = false; }
}

function canCancel(row) {
  if (!['PENDING', 'APPROVED'].includes(row.status) || row.manifestSyncStatus !== 'SENT'
    || !['NOT_REQUESTED', 'SENT'].includes(row.manifestSealStatus)) return false;
  const batchBags = (row.bagNos || [row.bagNo]).map((no) => bags.value.find((bag) => bag.bagNo === no));
  return batchBags.length > 0 && batchBags.every((bag) => bag && !bag.transportTaskCode
    && ['CREATED', 'REVIEWED'].includes(bag.status)
    && (session.user?.role === 'ADMIN' || bag.originFacilityCode === session.user?.facilityCode));
}

function openCancel(row) { cancelRow.value = row; cancelReason.value = ''; }

async function cancel() {
  loading.value = true;
  try {
    await dispatchApi.cancelBatch(cancelRow.value.batchNo, { reason: cancelReason.value.trim() }, session.token);
    cancelRow.value = null;
    await refresh();
  } catch (error) { notifyApiError(error); }
  finally { loading.value = false; }
}

onMounted(refresh);
</script>
