<template>
  <div class="grid-two">
    <div class="card p-5">
      <h3 class="card-title">接收勾核</h3>
      <p class="mt-2 text-sm text-gray-500">优先按路单接收，也支持把其他局再次封发流转来的总包直接加入接收清单。</p>

      <div class="mt-4 rounded-xl border border-gray-200 bg-slate-50 p-4 text-sm text-gray-700">
        <div>分拣机构：<span class="font-medium text-gray-900">{{ form.stationCode || '-' }}</span></div>
        <div class="mt-1">操作员：<span class="font-medium text-gray-900">{{ form.operatorId || '-' }}</span></div>
      </div>

      <form class="mt-4 space-y-4" @submit.prevent="submit">
        <div class="grid gap-3 md:grid-cols-2">
          <input :value="form.stationCode" class="input" disabled />
          <input :value="form.operatorId" class="input" disabled />
        </div>

        <select v-model="selectedManifestNo" class="select" @change="applyManifest">
          <option value="">选择预告路单（可选）</option>
          <option v-for="item in manifests" :key="item.manifestNo" :value="item.manifestNo">
            {{ item.manifestNo }} - {{ item.sourceOrgCode }} -> {{ item.destinationOrgCode }} - {{ item.expectedPackageQty }} 包
          </option>
        </select>

        <div class="grid gap-3 md:grid-cols-2">
          <input v-model="form.manifestNo" class="input" placeholder="电子路单号（可选，手工兜底）" />
          <select v-model="form.receiveMode" class="select">
            <option value="PREALERT">有预告</option>
            <option value="BLIND">无预告</option>
            <option value="FORCE">强制接收</option>
          </select>
        </div>

        <div class="rounded-2xl border border-dashed border-blue-300 bg-blue-50 p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-blue-900">条码扫码模拟</h4>
            <button type="button" class="btn btn-secondary" @click="simulateScan">模拟扫描</button>
          </div>
          <div class="mt-3 flex gap-2">
            <input v-model="scanInput" class="input flex-1" placeholder="输入/模拟扫描总包号" @keyup.enter.prevent="simulateScan" />
            <button type="button" class="btn btn-primary" @click="simulateScan">加入</button>
          </div>
          <p class="mt-2 text-xs text-blue-700">扫码结果会直接作为实际卸车接收总包号。</p>
        </div>

        <div class="rounded-2xl border border-gray-200 bg-white p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-gray-900">可接收流转总包</h4>
            <button type="button" class="btn btn-secondary" @click="loadPackages">刷新列表</button>
          </div>
          <div class="mt-3 space-y-2">
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
        </div>

        <div class="rounded-2xl border border-gray-200 bg-white p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-gray-900">已接收总包</h4>
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
              {{ item }} ×
            </button>
            <span v-if="!packageNos.length" class="text-sm text-gray-400">尚未扫码</span>
          </div>
        </div>

        <button class="btn btn-primary w-full" :disabled="submitting || !canSubmit">
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
let scanSequence = 0;

const form = reactive({
  stationCode: session.user?.facilityCode || '',
  manifestNo: '',
  receiveMode: 'PREALERT',
  operatorId: session.user?.userId || '',
});

const canSubmit = computed(() => Boolean(form.stationCode && packageNos.value.length));

function normalize(value) {
  return String(value || '').trim().toUpperCase();
}

function simulateScan() {
  const code = normalize(scanInput.value);
  if (!code) return;
  if (!packageNos.value.includes(code)) {
    packageNos.value.push(code);
  }
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

function applyManifest() {
  const manifest = manifests.value.find((item) => item.manifestNo === selectedManifestNo.value);
  if (!manifest) return;
  form.manifestNo = manifest.manifestNo;
  form.receiveMode = 'PREALERT';
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
      receiveMode: form.receiveMode,
      operatorId: form.operatorId || null,
      idempotencyKey: `receive-${Date.now()}-${++scanSequence}`,
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
    packageNos.value = [];
    await Promise.all([loadManifests(), loadPackages()]);
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
