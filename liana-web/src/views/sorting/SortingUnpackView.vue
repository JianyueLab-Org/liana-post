<template>
  <div class="grid-two">
    <div class="card p-5">
      <h3 class="card-title">开拆作业</h3>
      <p class="mt-2 text-sm text-gray-500">支持有预告盲扫和无预告强制开拆，条码扫码后自动累加到待开拆明细。</p>

      <div class="mt-4 rounded-xl border border-gray-200 bg-slate-50 p-4 text-sm text-gray-700">
        <div>分拣机构：<span class="font-medium text-gray-900">{{ form.stationCode || '-' }}</span></div>
        <div class="mt-1">操作员：<span class="font-medium text-gray-900">{{ form.operatorId || '-' }}</span></div>
      </div>

      <form class="mt-4 space-y-4" @submit.prevent="submit">
        <input :value="form.stationCode" class="input" disabled />
        <div class="grid gap-3 md:grid-cols-2">
          <input v-model="form.packageNo" class="input" placeholder="总包号（扫码/输入）" />
          <input v-model="form.manifestNo" class="input" placeholder="电子路单号（有预告时可选）" />
        </div>
        <div class="grid gap-3 md:grid-cols-2">
          <select v-model="form.scanMode" class="select">
            <option value="PREALERT">有预告</option>
            <option value="BLIND">无预告</option>
            <option value="FORCE">强制开拆</option>
          </select>
          <input :value="form.operatorId" class="input" disabled />
        </div>

        <div class="rounded-2xl border border-dashed border-emerald-300 bg-emerald-50 p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-emerald-900">条码扫描模拟</h4>
            <button type="button" class="btn btn-secondary" @click="simulateScan">模拟扫描</button>
          </div>
          <div class="mt-3 flex gap-2">
            <input v-model="scanInput" class="input flex-1" placeholder="输入/模拟扫描邮件单号" @keyup.enter.prevent="simulateScan" />
            <button type="button" class="btn btn-primary" @click="simulateScan">加入</button>
          </div>
        </div>

        <div class="rounded-2xl border border-gray-200 bg-white p-4">
          <div class="flex items-center justify-between gap-2">
            <h4 class="text-sm font-semibold text-gray-900">待开拆邮件</h4>
            <span class="text-xs text-gray-500">{{ itemNos.length }} 件</span>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="item in itemNos"
              :key="item"
              type="button"
              class="rounded-full border border-gray-300 bg-gray-50 px-3 py-1 text-xs text-gray-700"
              @click="removeItem(item)"
            >
              {{ item }} ×
            </button>
            <span v-if="!itemNos.length" class="text-sm text-gray-400">尚未扫描</span>
          </div>
        </div>

        <button class="btn btn-primary w-full" :disabled="submitting || !canSubmit">
          {{ submitting ? '提交中...' : '提交开拆' }}
        </button>
      </form>
    </div>

    <div class="card p-5">
      <h3 class="card-title">结果</h3>
      <button v-if="canRoute" type="button" class="btn btn-primary mt-4" @click="goRoute">
        去自动路由安检
      </button>
      <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { sortingApi } from '../../lib/api';
import { getUpuBarcodeError, normalizeUpuBarcode } from '../../lib/upuBarcode';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const router = useRouter();
const submitting = ref(false);
const result = ref('');
const scanInput = ref('');
const itemNos = ref([]);
const canRoute = ref(false);
let unpackScanSequence = 0;

const form = reactive({
  stationCode: session.user?.facilityCode || '',
  packageNo: '',
  manifestNo: '',
  scanMode: 'PREALERT',
  operatorId: session.user?.userId || '',
});

const canSubmit = computed(() => Boolean(form.stationCode && form.packageNo && itemNos.value.length));

function normalize(value) {
  return String(value || '').trim().toUpperCase();
}

function simulateScan() {
  const code = normalizeUpuBarcode(scanInput.value);
  const error = getUpuBarcodeError(code, '邮件单号');
  if (error) {
    result.value = error;
    scanInput.value = '';
    return;
  }
  if (!itemNos.value.includes(code)) {
    itemNos.value.push(code);
  }
  scanInput.value = '';
}

function removeItem(code) {
  itemNos.value = itemNos.value.filter((item) => item !== code);
}

async function submit() {
  if (!canSubmit.value) {
    result.value = '请先完成邮件扫码后再提交。';
    return;
  }
  submitting.value = true;
  try {
    const payload = await sortingApi.unpackItem({
      stationCode: form.stationCode,
      packageNo: form.packageNo,
      manifestNo: form.manifestNo || null,
      scanMode: form.scanMode,
      operatorId: form.operatorId || null,
      deviceId: 'WEB-UI',
      scanBatchNo: `BATCH-${Date.now()}-${++unpackScanSequence}`,
      idempotencyKey: `unpack-${Date.now()}-${unpackScanSequence}`,
      items: itemNos.value.map((itemNo) => ({ itemNo })),
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
    canRoute.value = true;
  } catch (error) {
    result.value = error.message;
    canRoute.value = false;
  } finally {
    submitting.value = false;
  }
}

function goRoute() {
  router.push('/sorting/route');
}
</script>
