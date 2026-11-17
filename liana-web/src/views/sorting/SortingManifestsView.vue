<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">路单管理</h3>
          <p class="mt-1 text-sm text-gray-500">展示上游预告路单，支持查看预报总包与内件明细。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="refresh">刷新</button>
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows" id-key="manifestNo">
        <template #actions="{ row }">
          <button class="btn btn-primary" @click="open(row)">详情</button>
        </template>
      </DataTable>
    </div>

    <DetailDrawer :open="drawerOpen" title="路单详情" :subtitle="current?.manifestNo" @close="drawerOpen = false">
      <div v-if="detail" class="space-y-4">
        <div class="grid gap-3 md:grid-cols-2 text-sm">
          <div>路单号：{{ detail.manifestNo }}</div>
          <div>类型：{{ detail.manifestType }}</div>
          <div>来源机构：{{ detail.sourceOrgCode }}</div>
          <div>目的机构：{{ detail.destinationOrgCode }}</div>
          <div>状态：{{ detail.manifestStatus }}</div>
          <div>清单版本：{{ detail.versionNo }}</div>
          <div>预告：{{ detail.prealertFlag }}</div>
          <div>预期总包数：{{ detail.expectedPackageQty }}</div>
          <div>预期邮件数：{{ detail.expectedItemQty }}</div>
        </div>
        <section class="space-y-3 rounded-lg border border-gray-200 p-3 text-sm">
          <h4 class="font-semibold">总包勾核与路单关闭</h4>
          <p v-if="closure?.check">已结束勾核：实收 {{ closure.check.receivedBagNos.length }} 包，缺失 {{ closure.check.missingBagNos.length }} 包 · {{ businessTime(closure.check.completedAt) }}（UTC+12）</p>
          <p v-else class="text-gray-500">完成实际总包接收后，明确结束勾核。届时未接收的预期总包登记为缺袋，结束后不再接收新总包。</p>
          <button v-if="canFinishCheck" class="btn btn-secondary" :disabled="!!processing" @click="processManifest('check')">{{ processing === 'check' ? '提交中...' : '确认结束总包勾核' }}</button>
          <p v-if="closure?.check" class="text-gray-500">差异审核完成、实收双端确认，且每个实收包已完成开拆或下一段勾核后，由管理员关闭路单。</p>
          <button v-if="isAdmin && closure?.check && !closure.command" class="btn btn-primary" :disabled="!!processing || openDiscrepancies.length > 0" @click="processManifest('close')">{{ processing === 'close' ? '提交中...' : '申请关闭路单' }}</button>
          <p v-if="closure?.command">关闭状态：{{ closure.status }}</p>
          <div v-for="delivery in closure?.deliveries || []" :key="delivery.destination">
            {{ delivery.destination === 'DISPATCH' ? '配发端' : '运输端' }}：{{ receiptStatus(delivery.status) }} · {{ delivery.attempts }} 次
            <p v-if="delivery.lastError" class="text-amber-700">{{ delivery.lastError }}</p>
          </div>
          <button v-if="closure?.command" class="btn btn-secondary" @click="open(current)">查询关闭确认</button>
        </section>
        <div v-if="versions.length" class="rounded-lg border border-gray-200 p-3 text-sm">
          <p v-if="cancelReceipt" class="mb-2 text-amber-800">本路单已作废 · {{ businessTime(cancelReceipt.cancelledAt) }}（海岛时间 UTC+12） · 操作人 {{ cancelReceipt.actorId }} · 原因：{{ cancelReceipt.reason }}</p>
          <p v-if="sealReceipt" class="mb-2 text-gray-700">第 {{ sealReceipt.versionNo }} 版已封单 · {{ businessTime(sealReceipt.sealedAt) }}（海岛时间 UTC+12） · 操作人 {{ sealReceipt.actorId }}</p>
          <h4 class="mb-2 font-semibold">历史预告清单</h4>
          <p class="mb-2 text-gray-500">以下内容记录各版预期，不代表已实际接收。</p>
          <select v-model="selectedVersion" class="select w-full">
            <option v-for="version in versions" :key="version.versionNo" :value="version.versionNo">
              第 {{ version.versionNo }} 版 · {{ version.expectedPackageQty }} 包 / {{ version.expectedItemQty }} 封 · {{ businessTime(version.recordedAt) }}
            </option>
          </select>
          <div v-for="pack in selectedSnapshot?.totalPackages || []" :key="pack.packageNo" class="mt-2 rounded bg-slate-50 p-3">
            <div class="font-medium">{{ pack.packageNo }} · {{ pack.packageWeight == null ? '重量未提供' : `${pack.packageWeight} 克` }}</div>
            <div class="mt-1 break-all text-gray-600">{{ pack.items?.map((item) => item.itemNo).join('、') || '未提供邮件明细' }}</div>
          </div>
        </div>
        <div class="space-y-2 text-sm">
          <h4 class="font-semibold">实际总包接收与同步</h4>
          <p v-if="!bagReceipts.length" class="text-gray-400">尚无实际接收记录</p>
          <div v-for="receipt in bagReceipts" :key="`${receipt.event_no}-${receipt.destination}`" class="rounded-lg border p-3">
            <div>{{ receipt.bag_no }} · {{ receipt.facility_code }} · {{ ({ OPENED: '已开拆', SEALED: '未开拆', MISSING: '后续运输缺失，已冻结' })[receipt.container_status] || receipt.container_status }}</div>
            <div>实收：{{ businessTime(receipt.received_at) }}（UTC+12） · 任务 {{ receipt.task_code }}</div>
            <div>{{ receipt.destination === 'TRANSPORT' ? '运输端' : '配发端' }}：{{ receiptStatus(receipt.status) }} · 已尝试 {{ receipt.attempt_count }} 次</div>
            <p v-if="receipt.last_error" class="text-amber-700">{{ receipt.last_error }}</p>
          </div>
        </div>
        <section class="space-y-3">
          <div class="flex items-center justify-between gap-3">
            <h4 class="text-sm font-semibold">总包与开拆差异</h4>
            <span class="text-xs text-gray-500">未结 {{ openDiscrepancies.length }}</span>
          </div>
          <p v-if="!manifestDiscrepancies.length" class="text-sm text-gray-400">暂无差异</p>
          <article v-for="item in manifestDiscrepancies" :key="item.verificationNo" class="rounded-lg border border-gray-200 p-3 text-sm">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <strong>{{ item.discrepancyType === 'MISSING' ? (item.itemNo ? '预期邮件未扫到' : '预期总包未接收') : item.discrepancyType === 'SURPLUS' ? '扫到预期外邮件' : item.discrepancyType }}</strong>
              <span>{{ item.status }} · {{ item.packageNo }} · {{ item.itemNo }}</span>
            </div>
            <div class="mt-1 text-gray-500">预期 {{ item.expectedQty }} / 实际 {{ item.actualQty }} · {{ item.remark || '' }}</div>
            <div v-if="isAdmin && item.status === 'OPEN' && item.discrepancySource === 'MANIFEST' && ['MISSING','SURPLUS'].includes(item.discrepancyType)" class="mt-3 grid gap-2 md:grid-cols-[1fr_auto]">
              <input v-model="reviewReasons[item.verificationNo]" :disabled="!!reviewRequests[item.verificationNo]" class="input" placeholder="填写审核依据（至少 8 字）" />
              <button class="btn btn-secondary" :disabled="reviewing === item.verificationNo || (reviewReasons[item.verificationNo] || '').trim().length < 8" @click="review(item)">
                {{ reviewing === item.verificationNo ? '提交中...' : item.discrepancyType === 'MISSING' ? '确认未收到' : '隔离盈余' }}
              </button>
            </div>
          </article>
        </section>
        <div>
          <h4 class="mb-2 text-sm font-semibold">预期邮件明细</h4>
          <div class="space-y-2 text-sm text-gray-700">
            <div v-for="item in detail.items" :key="item.id" class="rounded-lg border border-gray-200 p-3">
              <div>邮件号：{{ item.itemNo }}</div>
              <div>预报总包：{{ item.expectedPackageNo }}</div>
              <div>路由：{{ item.expectedRouteCode }}</div>
            </div>
            <div v-if="!detail.items?.length" class="text-gray-400">暂无明细</div>
          </div>
        </div>
      </div>
      <pre v-else class="whitespace-pre-wrap text-sm text-gray-700">{{ current }}</pre>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';
import { notifyApiError } from '../../lib/apiErrorEvents';

const session = useSessionStore();
const loading = ref(false);
const rows = ref([]);
const drawerOpen = ref(false);
const current = ref(null);
const detail = ref(null);
const versions = ref([]);
const sealReceipt = ref(null);
const cancelReceipt = ref(null);
const bagReceipts = ref([]);
const closure = ref(null);
const processing = ref('');
const manifestRequests = ref({});
const manifestStorageKey = `sorting-manifest-finish:${session.user?.userId || session.user?.username || ''}`;
const canFinishCheck = computed(() => detail.value && !closure.value?.check && ['SEALED','RECEIVING','CHECKED','DISCREPANCY'].includes(detail.value.manifestStatus)
  && (isAdmin.value || session.user?.facilityCode === detail.value.destinationOrgCode));
const manifestDiscrepancies = ref([]);
const reviewReasons = ref({});
const reviewRequests = ref({});
const reviewing = ref('');
const isAdmin = computed(() => String(session.user?.role || '').toUpperCase() === 'ADMIN');
const openDiscrepancies = computed(() => manifestDiscrepancies.value.filter((item) => item.status === 'OPEN'));
const reviewStorageKey = `sorting-discrepancy-review:${session.user?.userId || session.user?.username || ''}`;
const receiptStatus = (status) => ({ PENDING: '待同步', SENDING: '同步中', RETRY: '等待重试', SENT: '已确认', CONFLICT: '冲突待处理' }[status] || status);
const selectedVersion = ref(null);
const selectedSnapshot = computed(() => versions.value.find((version) => version.versionNo === selectedVersion.value)?.snapshot);
const businessTime = (value) => value ? new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Etc/GMT-12', dateStyle: 'short', timeStyle: 'short',
}).format(new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)) : '-';

const columns = [
  { key: 'manifestNo', label: '路单号' },
  { key: 'manifestType', label: '类型' },
  { key: 'sourceOrgCode', label: '来源机构' },
  { key: 'destinationOrgCode', label: '目的机构' },
  { key: 'manifestStatus', label: '状态' },
  { key: 'versionNo', label: '清单版本' },
  { key: 'expectedPackageQty', label: '预期总包' },
  { key: 'expectedItemQty', label: '预期邮件' },
  { key: 'actions', label: '操作' },
];

async function refresh() {
  loading.value = true;
  try {
    rows.value = await sortingApi.listManifests(session.token, { receiveCandidate: false });
  } finally {
    loading.value = false;
  }
}

async function open(row) {
  current.value = row;
  drawerOpen.value = true;
  detail.value = null;
  versions.value = [];
  sealReceipt.value = null;
  cancelReceipt.value = null;
  bagReceipts.value = [];
  closure.value = null;
  manifestDiscrepancies.value = [];
  selectedVersion.value = null;
  try {
    const loaded = await sortingApi.getManifest(row.manifestNo, session.token);
    if (current.value?.manifestNo !== row.manifestNo) return;
    detail.value = loaded;
    const [history, receipt, cancellation, received, allDiscrepancies, closed] = await Promise.all([
      sortingApi.getManifestVersions(row.manifestNo, session.token),
      sortingApi.getManifestSeal(row.manifestNo, session.token),
      sortingApi.getManifestCancel(row.manifestNo, session.token),
      sortingApi.getBagReceipts(row.manifestNo, session.token),
      sortingApi.listDiscrepancies(undefined, session.token),
      sortingApi.getManifestClosure(row.manifestNo, session.token),
    ]);
    if (current.value?.manifestNo !== row.manifestNo) return;
    versions.value = history;
    sealReceipt.value = receipt;
    cancelReceipt.value = cancellation;
    bagReceipts.value = received;
    closure.value = closed;
    manifestDiscrepancies.value = allDiscrepancies.filter((item) => item.manifestNo === row.manifestNo);
    selectedVersion.value = loaded.versionNo;
  } catch (error) { notifyApiError(error); }
}

async function processManifest(action) {
  if (processing.value || !detail.value) return;
  const no = detail.value.manifestNo;
  const key = `${no}:${action}`;
  const payload = manifestRequests.value[key] || {
    requestNo: `MANIFEST-${action}-${crypto.randomUUID()}`.toUpperCase(),
    facilityCode: detail.value.destinationOrgCode,
  };
  manifestRequests.value = { ...manifestRequests.value, [key]: payload };
  sessionStorage.setItem(manifestStorageKey, JSON.stringify(manifestRequests.value));
  processing.value = action;
  try {
    if (action === 'check') await sortingApi.finishManifestCheck(no, payload, session.token);
    else await sortingApi.closeManifest(no, payload, session.token);
    await open(current.value);
    await refresh();
  } catch (error) { notifyApiError(error); }
  finally { processing.value = ''; }
}

function saveReviewRequests() {
  sessionStorage.setItem(reviewStorageKey, JSON.stringify(reviewRequests.value));
}

async function review(item) {
  if (!isAdmin.value || reviewing.value) return;
  const reason = (reviewReasons.value[item.verificationNo] || '').trim();
  if (reason.length < 8) return;
  const saved = reviewRequests.value[item.verificationNo] || {
    idempotencyKey: `DISCREPANCY-${crypto.randomUUID()}`.toUpperCase(),
    disposition: item.discrepancyType === 'MISSING' ? 'CONFIRMED_NOT_RECEIVED' : 'QUARANTINED',
    reason,
  };
  reviewRequests.value = { ...reviewRequests.value, [item.verificationNo]: saved };
  saveReviewRequests();
  reviewing.value = item.verificationNo;
  try {
    await sortingApi.disposeDiscrepancy(item.verificationNo, reviewRequests.value[item.verificationNo], session.token);
    reviewRequests.value = { ...reviewRequests.value };
    delete reviewRequests.value[item.verificationNo];
    saveReviewRequests();
    await open(current.value);
  } catch (error) { notifyApiError(error); }
  finally { reviewing.value = ''; }
}

onMounted(async () => {
  try { manifestRequests.value = JSON.parse(sessionStorage.getItem(manifestStorageKey) || '{}'); }
  catch { sessionStorage.removeItem(manifestStorageKey); }
  try { reviewRequests.value = JSON.parse(sessionStorage.getItem(reviewStorageKey) || '{}'); }
  catch { sessionStorage.removeItem(reviewStorageKey); }
  for (const [no, request] of Object.entries(reviewRequests.value)) reviewReasons.value[no] = request.reason;
  await refresh();
});
</script>
