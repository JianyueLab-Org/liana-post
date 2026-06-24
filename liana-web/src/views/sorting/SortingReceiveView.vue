<template>
  <div class="grid-two">
    <div class="card p-5">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h3 class="card-title">接收勾核</h3>
          <p class="mt-2 text-sm text-gray-500">选择预告路单后自动带出电子路单号，并按路单总包模拟连续扫码。</p>
        </div>
        <div class="rounded-lg bg-slate-50 px-4 py-3 text-right text-xs text-gray-500">
          <div>机构 <span class="font-semibold text-gray-900">{{ form.stationCode || '-' }}</span></div>
          <div class="mt-1">操作员 <span class="font-semibold text-gray-900">{{ form.operatorId || '-' }}</span></div>
        </div>
      </div>

      <form class="mt-5 space-y-5" @submit.prevent="submit">
        <section class="space-y-3">
          <div class="flex items-center justify-between gap-3">
            <h4 class="text-sm font-semibold text-gray-900">预告路单</h4>
            <button type="button" class="btn btn-secondary" @click="loadManifests">刷新路单</button>
          </div>
          <select v-model="selectedManifestNo" class="select" @change="applyManifest">
            <option value="">不选择预告路单，按无预告接收</option>
            <option v-for="item in manifests" :key="item.manifestNo" :value="item.manifestNo">
              {{ item.manifestNo }} - {{ item.sourceOrgCode }} -> {{ item.destinationOrgCode }} - {{ item.expectedPackageQty }} 包
            </option>
          </select>
          <div v-if="form.manifestNo" class="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-900">
            电子路单号：<span class="font-semibold">{{ form.manifestNo }}</span>
            <span class="ml-3 text-blue-700">待扫总包 {{ remainingManifestPackages.length }} / {{ manifestPackages.length }}</span>
          </div>
        </section>

        <section class="rounded-2xl border border-dashed border-blue-300 bg-blue-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-blue-900">总包扫描</h4>
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="scanAnimating || !remainingManifestPackages.length"
              @click="simulateManifestScan"
            >
              {{ scanAnimating ? '扫描中...' : '模拟扫描路单总包' }}
            </button>
          </div>
          <div class="mt-3 flex gap-2">
            <input
              v-model="scanInput"
              class="input flex-1 font-mono"
              :class="{ 'scanner-flash': scanAnimating }"
              placeholder="扫描或输入总包号"
              @keyup.enter.prevent="addScannedInput"
            />
            <button type="button" class="btn btn-primary" :disabled="scanAnimating" @click="addScannedInput">加入</button>
          </div>
        </section>

        <section v-if="manifestPackages.length" class="rounded-2xl border border-gray-200 bg-white p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-gray-900">路单总包</h4>
            <span class="text-xs text-gray-500">{{ packageNos.length }} / {{ manifestPackages.length }} 已加入</span>
          </div>
          <div class="mt-3 grid gap-2 md:grid-cols-2">
            <button
              v-for="item in manifestPackages"
              :key="item.packageNo"
              type="button"
              class="rounded-lg border px-3 py-2 text-left text-xs transition"
              :class="packageNos.includes(item.packageNo) ? 'border-blue-500 bg-blue-50 text-blue-900' : 'border-gray-200 bg-white text-gray-600'"
              @click="addPackage(item.packageNo)"
            >
              <div class="font-medium">{{ item.packageNo }}</div>
              <div class="mt-1">{{ item.packageStatus }} · {{ item.sourceOrgCode || '-' }} -> {{ item.destinationOrgCode || '-' }}</div>
            </button>
          </div>
        </section>

        <details class="rounded-2xl border border-gray-200 bg-white p-4">
          <summary class="cursor-pointer text-sm font-semibold text-gray-900">手工兜底</summary>
          <div class="mt-3 grid gap-3 md:grid-cols-2">
            <input v-model="form.manifestNo" class="input" placeholder="电子路单号，可选" />
            <select v-model="manualReceiveMode" class="select">
              <option value="">自动判断</option>
              <option value="BLIND">无预告</option>
              <option value="FORCE">强制接收</option>
            </select>
          </div>
          <div class="mt-3">
            <button type="button" class="btn btn-secondary" @click="loadPackages">刷新可接收总包</button>
          </div>
          <div class="mt-3 max-h-56 space-y-2 overflow-auto">
            <button
              v-for="item in packages"
              :key="item.packageNo"
              type="button"
              class="w-full rounded-xl border px-4 py-3 text-left transition"
              :class="packageNos.includes(item.packageNo) ? 'border-blue-500 bg-blue-50' : 'border-gray-200 bg-white'"
              @click="addPackage(item.packageNo)"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="font-medium text-gray-900">{{ item.packageNo }}</span>
                <span class="text-xs text-gray-500">{{ item.packageStatus }}</span>
              </div>
              <div class="mt-2 text-xs text-gray-500">{{ item.sourceOrgCode }} -> {{ item.destinationOrgCode }}</div>
            </button>
            <div v-if="!packages.length" class="py-4 text-sm text-gray-400">暂无可接收流转总包</div>
          </div>
        </details>

        <section class="rounded-2xl border border-gray-200 bg-white p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-gray-900">本次接收清单</h4>
            <span class="text-xs text-gray-500">{{ packageNos.length }} 项</span>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="item in packageNos"
              :key="item"
              type="button"
              class="rounded-full border border-gray-300 bg-gray-50 px-3 py-1 text-xs text-gray-700"
              @click="removePackage(item)"
            >
              {{ item }} x
            </button>
            <span v-if="!packageNos.length" class="text-sm text-gray-400">尚未扫码</span>
          </div>
        </section>

        <button class="btn btn-primary w-full" :disabled="submitting || scanAnimating || !canSubmit">
          {{ submitting ? '提交中...' : '提交接收' }}
        </button>
      </form>
    </div>

    <div class="card p-5">
      <h3 class="card-title">结果</h3>
      <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const submitting = ref(false);
const result = ref('');
const scanInput = ref('');
const packageNos = ref([]);
const packages = ref([]);
const manifests = ref([]);
const selectedManifestNo = ref('');
const selectedManifestDetail = ref(null);
const scanAnimating = ref(false);
const manualReceiveMode = ref('');
let scanSequence = 0;

const form = reactive({
  stationCode: session.user?.facilityCode || '',
  manifestNo: '',
  operatorId: session.user?.userId || '',
});

const manifestPackages = computed(() => {
  const detail = selectedManifestDetail.value;
  if (!detail) {
    return [];
  }
  if (detail.packages?.length) {
    return detail.packages;
  }
  const packageNos = [...new Set((detail.items || [])
    .map((item) => normalize(item.expectedPackageNo))
    .filter(Boolean))];
  return packageNos.map((packageNo) => ({
    packageNo,
    packageStatus: 'RAW',
    sourceOrgCode: detail.sourceOrgCode,
    destinationOrgCode: detail.destinationOrgCode,
  }));
});
const remainingManifestPackages = computed(() => manifestPackages.value.filter((item) => !packageNos.value.includes(item.packageNo)));
const receiveMode = computed(() => manualReceiveMode.value || (form.manifestNo ? 'PREALERT' : 'BLIND'));
const canSubmit = computed(() => Boolean(form.stationCode && packageNos.value.length));

function normalize(value) {
  return String(value || '').trim().toUpperCase();
}

function delay(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

async function typeScanText(code) {
  scanInput.value = '';
  for (const char of code) {
    scanInput.value += char;
    await delay(35);
  }
  await delay(120);
}

async function simulateManifestScan() {
  if (scanAnimating.value || !remainingManifestPackages.value.length) {
    return;
  }
  scanAnimating.value = true;
  try {
    const queue = remainingManifestPackages.value.map((item) => item.packageNo);
    for (const packageNo of queue) {
      await typeScanText(packageNo);
      addPackage(packageNo);
      scanInput.value = '';
      await delay(180);
    }
  } finally {
    scanInput.value = '';
    scanAnimating.value = false;
  }
}

function addScannedInput() {
  addPackage(scanInput.value);
  scanInput.value = '';
}

function addPackage(packageNo) {
  const code = normalize(packageNo);
  if (!code) return;
  if (!packageNos.value.includes(code)) {
    packageNos.value.push(code);
  }
}

function removePackage(code) {
  packageNos.value = packageNos.value.filter((item) => item !== code);
}

async function applyManifest() {
  const manifest = manifests.value.find((item) => item.manifestNo === selectedManifestNo.value);
  selectedManifestDetail.value = null;
  packageNos.value = [];
  manualReceiveMode.value = '';
  if (!manifest) {
    form.manifestNo = '';
    return;
  }
  form.manifestNo = manifest.manifestNo;
  selectedManifestDetail.value = await sortingApi.getManifest(manifest.manifestNo, session.token);
}

function isManifestChecked(item) {
  return String(item?.manifestStatus || '').toUpperCase() === 'CHECKED';
}

async function loadManifests() {
  manifests.value = (await sortingApi.listManifests(session.token, { receiveCandidate: true }))
    .filter((item) => !isManifestChecked(item));
  if (selectedManifestNo.value && !manifests.value.some((item) => item.manifestNo === selectedManifestNo.value)) {
    selectedManifestNo.value = '';
    form.manifestNo = '';
    selectedManifestDetail.value = null;
  }
}

async function loadPackages() {
  packages.value = await sortingApi.listPackages(session.token);
}

async function submit() {
  if (!canSubmit.value) {
    result.value = '请先完成总包扫码后再提交。';
    return;
  }
  submitting.value = true;
  try {
    const payload = await sortingApi.receive({
      stationCode: form.stationCode,
      manifestNo: form.manifestNo || null,
      packageNos: packageNos.value,
      receiveMode: receiveMode.value,
      operatorId: form.operatorId || null,
      idempotencyKey: `receive-${Date.now()}-${++scanSequence}`,
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
    packageNos.value = [];
    await Promise.all([loadManifests(), loadPackages()]);
    if (selectedManifestNo.value) {
      await applyManifest();
    }
  } catch (error) {
    result.value = error.message;
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadManifests(), loadPackages()]);
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
