<template>
  <div class="grid-two">
    <div class="card p-5">
      <h3 class="card-title">开拆作业</h3>
      <p class="mt-2 text-sm text-gray-500">总包点一下切换一个，邮件点一下确认一个，按队列连续推进。</p>

      <form class="mt-4 space-y-4" @submit.prevent="submit">
        <div class="rounded-xl border border-gray-200 bg-slate-50 p-4 text-sm text-gray-700">
          <div>操作员：<span class="font-medium text-gray-900">{{ form.operatorId || '-' }}</span></div>
          <div class="mt-1">当前总包：<span class="font-medium text-gray-900">{{ form.packageNo || '-' }}</span></div>
        </div>

        <section class="rounded-2xl border border-dashed border-blue-300 bg-blue-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-blue-900">总包扫描</h4>
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="!!pendingSubmission || submitting || loadingPackages || packageScanning || !remainingPackages.length"
              @click="scanPackage"
            >
              {{ packageScanning ? '扫描中...' : '扫描下一个总包' }}
            </button>
          </div>
          <div class="mt-3 flex gap-2">
            <input
              :value="packageScanPreview"
              class="input flex-1"
              :class="{ 'scanner-flash': packageScanning }"
              placeholder="扫描下一个总包"
              readonly
            />
          </div>
          <div class="mt-2 text-xs text-blue-800">
            待开拆 {{ remainingPackages.length }} / {{ packageQueue.length }}
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <span
              v-for="item in remainingPackages.slice(0, 8)"
              :key="item.packageNo"
              class="rounded-full border border-blue-200 bg-white px-3 py-1 text-xs text-blue-900"
            >
              {{ item.packageNo }}
            </span>
            <span v-if="!remainingPackages.length" class="text-sm text-blue-700">暂无待开拆总包</span>
          </div>
        </section>

        <section class="rounded-2xl border border-dashed border-emerald-300 bg-emerald-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-emerald-900">邮件扫描</h4>
            <span class="text-xs text-emerald-800">{{ itemNos.length }} 件</span>
          </div>
          <div class="mt-3 flex gap-2">
            <input
              v-model="itemScanInput"
              :disabled="!!pendingSubmission || submitting"
              class="input flex-1"
              :class="{ 'scanner-flash': itemScanning }"
              placeholder="扫描或输入邮件条码"
              @keyup.enter.prevent="scanItem"
            />
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!!pendingSubmission || submitting || itemScanning || !form.packageNo"
              @click="scanItem"
            >
              {{ itemScanning ? '扫描中...' : '扫' }}
            </button>
          </div>
          <div class="mt-2 text-xs text-emerald-800">
            候选邮件 {{ remainingItems.length }} / {{ pendingItems.length }}
          </div>
        </section>

        <div class="grid gap-3 md:grid-cols-2">
          <input v-model="form.manifestNo" class="input" placeholder="电子路单号" readonly />
          <select v-model="form.scanMode" class="select" disabled>
            <option value="PREALERT">有预告</option>
          </select>
        </div>

        <section class="rounded-2xl border border-gray-200 bg-white p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-gray-900">本次开拆清单</h4>
            <span class="text-xs text-gray-500">{{ itemNos.length }} 项</span>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="item in itemNos"
              :key="item"
              type="button"
              class="rounded-full border border-gray-300 bg-gray-50 px-3 py-1 text-xs text-gray-700"
              :disabled="!!pendingSubmission || submitting"
              @click="removeItem(item)"
            >
              {{ item }} ×
            </button>
            <span v-if="!itemNos.length" class="text-sm text-gray-400">尚未扫描</span>
          </div>
        </section>

        <label v-if="!itemNos.length" class="flex gap-2 text-sm text-gray-700">
          <input v-model="confirmEmpty" type="checkbox" :disabled="!!pendingSubmission || submitting" />
          确认本次未扫到任何邮件，结束开拆并登记全部缺件
        </label>
        <p v-if="pendingSubmission" class="text-sm text-amber-700">提交内容已保存。网络异常后请重试原请求，刷新页面也可恢复。</p>
        <button class="btn btn-primary w-full" :disabled="submitting || itemScanning || packageScanning || !canSubmit">
          {{ submitting ? '提交中...' : pendingSubmission ? '重试原开拆请求' : '完成开拆' }}
        </button>
      </form>
    </div>

    <div class="card p-5">
      <h3 class="card-title">结果</h3>
      <div v-if="lastRequestNo" class="mt-3 text-sm text-gray-600">
        <div>开拆请求：{{ lastRequestNo }}</div>
        <div>邮件归属确认：{{ confirmationLabel }}</div>
        <button type="button" class="btn btn-secondary mt-2" @click="refreshConfirmation">刷新确认状态</button>
      </div>
      <button v-if="canRoute" type="button" class="btn btn-primary mt-4" @click="goRoute">
        去自动路由计算
      </button>
      <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { sortingApi } from '../../lib/api';
import { getUpuBarcodeError, normalizeUpuBarcode } from '../../lib/upuBarcode';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const router = useRouter();
const submitting = ref(false);
const loadingPackages = ref(false);
const packageScanning = ref(false);
const itemScanning = ref(false);
const result = ref('');
const packageScanPreview = ref('');
const itemScanInput = ref('');
const itemNos = ref([]);
const pendingItems = ref([]);
const packageQueue = ref([]);
const consumedPackageNos = ref([]);
const selectedManifestDetail = ref(null);
const canRoute = ref(false);
const confirmEmpty = ref(false);
const storageKey = `sorting-unpack:${session.user?.userId || session.user?.username || ''}:${session.user?.facilityCode || ''}`;
const pendingSubmission = ref(null);
const lastRequestNo = ref('');
const confirmationStatus = ref('');
const confirmationLabel = computed(() => ({
  PENDING: '等待 OMS 确认', SENDING: '正在确认', RETRY: '等待自动重试',
  SENT: '已确认，可继续路由', CONFLICT: '归属冲突，需核查；未开放后续处理',
}[confirmationStatus.value] || '尚未查询'));

function persistSubmission() {
  sessionStorage.setItem(storageKey, JSON.stringify({ pending: pendingSubmission.value, lastRequestNo: lastRequestNo.value }));
}

const form = reactive({
  stationCode: session.user?.facilityCode || '',
  packageNo: '',
  manifestNo: '',
  scanMode: 'PREALERT',
  operatorId: session.user?.userId || '',
});

const remainingPackages = computed(() => packageQueue.value.filter((item) => !consumedPackageNos.value.includes(item.packageNo)));
const remainingItems = computed(() => pendingItems.value.filter((item) => !itemNos.value.includes(item)));
const nextPendingItem = computed(() => remainingItems.value[0] || '');
const canSubmit = computed(() => Boolean(pendingSubmission.value || (form.stationCode && form.packageNo && form.manifestNo && (itemNos.value.length || confirmEmpty.value))));

function normalize(value) {
  return String(value || '').trim().toUpperCase();
}

function delay(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

async function animateScan(targetRef, code, busyRef) {
  busyRef.value = true;
  targetRef.value = '';
  try {
    for (const char of code) {
      targetRef.value += char;
      await delay(35);
    }
    await delay(120);
  } finally {
    busyRef.value = false;
  }
}

function preloadNextItem() {
  itemScanInput.value = nextPendingItem.value;
}

function buildPendingItemsFromManifest(detail, packageNo) {
  if (!detail) {
    return [];
  }
  const normalizedPackageNo = normalize(packageNo);
  const items = Array.isArray(detail.items) ? detail.items : [];
  const expectedFromItems = items
    .filter((item) => normalize(item.expectedPackageNo) === normalizedPackageNo)
    .map((item) => normalize(item.itemNo))
    .filter(Boolean);
  if (expectedFromItems.length) {
    return [...new Set(expectedFromItems)];
  }
  const packages = Array.isArray(detail.packages) ? detail.packages : [];
  const matched = packages.find((item) => normalize(item.packageNo) === normalizedPackageNo);
  if (matched && Array.isArray(matched.items)) {
    return [...new Set(matched.items.map((item) => normalize(item.itemNo)).filter(Boolean))];
  }
  return [];
}

function applyPendingItems(items) {
  const seen = new Set();
  pendingItems.value = items
    .filter((item) => {
      if (!item || seen.has(item)) return false;
      seen.add(item);
      return true;
    });
  itemNos.value = [];
}

async function loadPackages() {
  loadingPackages.value = true;
  try {
    const packages = await sortingApi.listPackages(session.token);
    packageQueue.value = packages
      .filter((item) => String(item.packageStatus || '').toUpperCase() === 'RECEIVED')
      .map((item) => ({
        ...item,
        packageNo: normalize(item.packageNo),
      }))
      .filter((item) => item.packageNo);

    consumedPackageNos.value = consumedPackageNos.value.filter((code) =>
      packageQueue.value.some((item) => item.packageNo === code),
    );

    if (!form.packageNo || !remainingPackages.value.some((item) => item.packageNo === form.packageNo)) {
      form.packageNo = remainingPackages.value[0]?.packageNo || '';
      form.manifestNo = remainingPackages.value[0]?.manifestNo || '';
    }

    await loadPendingItemsByManifest(form.manifestNo, form.packageNo);
    preloadNextItem();
  } finally {
    loadingPackages.value = false;
  }
}

async function loadPendingItemsByManifest(manifestNo, packageNo) {
  selectedManifestDetail.value = null;
  if (!packageNo) {
    pendingItems.value = [];
    return;
  }
  try {
    const preview = await sortingApi.previewUnpackItems({
      packageNo,
      manifestNo: manifestNo || null,
    }, session.token);
    if (preview?.manifestNo) {
      form.manifestNo = preview.manifestNo;
    }
    if (preview?.manifestNo) {
      try {
        selectedManifestDetail.value = await sortingApi.getManifest(preview.manifestNo, session.token);
      } catch {
        selectedManifestDetail.value = null;
      }
    }
    applyPendingItems(Array.isArray(preview?.itemNos) ? preview.itemNos.map((item) => normalize(item)).filter(Boolean) : []);
  } catch {
    pendingItems.value = [];
  }
}

async function scanPackage() {
  if (pendingSubmission.value || submitting.value || packageScanning.value || loadingPackages.value) {
    return;
  }
  const nextPackage = remainingPackages.value[0];
  if (!nextPackage) {
    result.value = '暂无可开拆总包。';
    return;
  }
  await animateScan(packageScanPreview, nextPackage.packageNo, packageScanning);
  form.packageNo = nextPackage.packageNo;
  confirmEmpty.value = false;
  form.manifestNo = nextPackage.manifestNo || form.manifestNo;
  consumedPackageNos.value = [...consumedPackageNos.value, nextPackage.packageNo];
  await loadPendingItemsByManifest(form.manifestNo, nextPackage.packageNo);
  preloadNextItem();
  result.value = `已扫描总包 ${nextPackage.packageNo}，已预置邮件 ${itemScanInput.value || '-'}`;
}

async function scanItem() {
  if (pendingSubmission.value || submitting.value || itemScanning.value) {
    return;
  }
  if (!form.packageNo) {
    result.value = '请先扫描总包。';
    return;
  }

  const raw = itemScanInput.value.trim();
  let code = raw ? normalizeUpuBarcode(raw) : nextPendingItem.value;
  if (!code) {
    result.value = '当前总包没有可扫描邮件。';
    return;
  }

  const error = getUpuBarcodeError(code, '邮件单号');
  if (error) {
    result.value = error;
    itemScanInput.value = '';
    return;
  }

  code = normalize(code);
  if (itemNos.value.includes(code)) {
    result.value = `邮件 ${code} 已扫描。`;
    itemScanInput.value = '';
    return;
  }

  await animateScan(itemScanInput, code, itemScanning);
  itemNos.value.push(code);
  itemScanInput.value = '';
  preloadNextItem();
  result.value = pendingItems.value.includes(code) ? `已扫描邮件 ${code}` : `已扫描预期外邮件 ${code}，完成后登记盈余并隔离。`;
}

function removeItem(code) {
  if (pendingSubmission.value || submitting.value) return;
  itemNos.value = itemNos.value.filter((item) => item !== code);
}

async function submit() {
  if (!canSubmit.value) {
    result.value = '请先扫描总包并完成邮件扫描。';
    return;
  }
  submitting.value = true;
  try {
    if (!pendingSubmission.value) {
      const requestNo = `UNPACK-${crypto.randomUUID()}`.toUpperCase();
      pendingSubmission.value = {
      stationCode: form.stationCode,
      packageNo: form.packageNo,
      manifestNo: form.manifestNo || null,
      scanMode: form.scanMode,
      operatorId: form.operatorId || null,
      deviceId: 'WEB-UI',
      scanBatchNo: requestNo,
      idempotencyKey: requestNo,
      items: itemNos.value.map((itemNo) => ({ itemNo })),
      };
      persistSubmission();
    }
    const submitted = pendingSubmission.value;
    const payload = await sortingApi.unpackItem(submitted, session.token);
    lastRequestNo.value = submitted.idempotencyKey;
    pendingSubmission.value = null;
    persistSubmission();
    result.value = JSON.stringify(payload, null, 2);
    await refreshConfirmation();
    itemNos.value = [];
    confirmEmpty.value = false;
    consumedPackageNos.value = [...consumedPackageNos.value, submitted.packageNo];
    await loadPackages();
  } catch (error) {
    result.value = error.message;
    canRoute.value = false;
  } finally {
    submitting.value = false;
  }
}

async function refreshConfirmation() {
  if (!lastRequestNo.value) return;
  try {
    const receipt = await sortingApi.unpackReceipt(lastRequestNo.value, session.token);
    confirmationStatus.value = receipt.status;
    canRoute.value = receipt.status === 'SENT';
    if (receipt.lastError) result.value = receipt.lastError;
  } catch (error) {
    canRoute.value = false;
    result.value = `开拆已保存，确认状态查询失败：${error.message}`;
  }
}

function goRoute() {
  router.push('/sorting/route');
}

onMounted(async () => {
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
    pendingSubmission.value = saved?.pending || null;
    lastRequestNo.value = saved?.lastRequestNo || '';
  } catch { sessionStorage.removeItem(storageKey); }
  if (pendingSubmission.value) {
    Object.assign(form, pendingSubmission.value);
    itemNos.value = pendingSubmission.value.items.map((item) => item.itemNo);
    confirmEmpty.value = !itemNos.value.length;
    packageScanPreview.value = form.packageNo;
  } else await loadPackages();
  await refreshConfirmation();
});
</script>

<style scoped>
.scanner-flash {
  animation: scanner-pulse 0.45s ease-in-out infinite alternate;
}

@keyframes scanner-pulse {
  from {
    border-color: #93c5fd;
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.15);
  }
  to {
    border-color: #2563eb;
    box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.12);
  }
}
</style>
