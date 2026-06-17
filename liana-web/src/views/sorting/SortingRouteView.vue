<template>
  <div class="grid gap-6 lg:grid-cols-[1.1fr_1.4fr]">
    <section class="card p-5">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h3 class="card-title">分拣路由计算</h3>
          <p class="mt-1 text-sm text-gray-500">普通分拣局使用，按扫描结果推导下一跳路由。</p>
        </div>
        <span class="rounded-full px-3 py-1 text-xs font-medium" :class="auditBadgeClass">{{ auditStateText }}</span>
      </div>

      <div class="mt-5 space-y-4">
        <input
          ref="scanInputRef"
          v-model="scanInput"
          class="input text-lg tracking-[0.2em]"
          placeholder="扫描邮件号"
          autofocus
          @keydown.enter.prevent="submitScan"
        />

        <div class="rounded-3xl border-2 border-dashed p-5 transition-all duration-300" :class="auditPanelClass">
          <div class="flex items-center justify-between gap-3">
            <div>
              <div class="text-sm font-semibold">路由结果</div>
              <div class="mt-1 text-sm" :class="auditTextClass">{{ auditMessage }}</div>
            </div>
            <div class="text-right text-xs text-gray-500">
              <div>下一跳</div>
              <div class="text-sm font-semibold text-gray-900">{{ latest?.nextHop || '-' }}</div>
            </div>
          </div>
        </div>

        <div class="rounded-2xl border border-gray-200 bg-slate-50 p-4 text-sm text-gray-700">
          <div>当前单号：<span class="font-semibold text-gray-900">{{ latest?.itemNo || '-' }}</span></div>
          <div class="mt-1">匹配路由：<span class="font-semibold text-gray-900">{{ latest?.routeCode || '-' }}</span></div>
          <div class="mt-1">下一跳：<span class="font-semibold text-gray-900">{{ latest?.nextHop || '-' }}</span></div>
          <div class="mt-1">安全状态：<span class="font-semibold text-gray-900">{{ latest?.securityStatus || '-' }}</span></div>
        </div>
      </div>
    </section>

    <section class="card p-5">
      <div class="flex items-center justify-between gap-3">
        <div>
          <h3 class="card-title">路由轨迹</h3>
          <p class="mt-1 text-sm text-gray-500">这里保留普通分拣局的默认路由工作台。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="refresh">刷新</button>
      </div>

      <div class="mt-4 overflow-hidden rounded-2xl border border-gray-200">
        <table class="table">
          <thead>
            <tr>
              <th>邮件单号</th>
              <th>路由</th>
              <th>下一跳</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.itemNo">
              <td>{{ row.itemNo }}</td>
              <td>{{ row.routeCode }}</td>
              <td>{{ row.nextHop }}</td>
              <td>
                <span class="rounded-full px-2 py-1 text-xs font-medium" :class="row.securityStatus === 'DANGER' ? 'bg-red-100 text-red-700' : 'bg-emerald-100 text-emerald-700'">
                  {{ row.securityStatus }}
                </span>
              </td>
            </tr>
            <tr v-if="!rows.length">
              <td colspan="4" class="py-10 text-center text-sm text-gray-400">等待扫描</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { sortingApi } from '../../lib/api';
import { getUpuBarcodeError, normalizeUpuBarcode } from '../../lib/upuBarcode';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const scanInput = ref('');
const scanInputRef = ref(null);
const rows = ref([]);
const loading = ref(false);
const latest = ref(null);
const auditState = ref('PASS');
const auditMessage = ref('OK');
let routeScanSequence = 0;

const auditStateText = computed(() => (auditState.value === 'DANGER' ? 'DANGER' : 'PASS'));
const auditBadgeClass = computed(() => (auditState.value === 'DANGER' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'));
const auditPanelClass = computed(() => (auditState.value === 'DANGER' ? 'border-red-500 bg-red-50' : 'border-emerald-400 bg-emerald-50'));
const auditTextClass = computed(() => (auditState.value === 'DANGER' ? 'text-red-700' : 'text-emerald-700'));

function focusScan() {
  nextTick(() => scanInputRef.value?.focus());
}

async function submitScan() {
  const itemNo = normalizeUpuBarcode(scanInput.value);
  const error = getUpuBarcodeError(itemNo, '邮件单号');
  if (error) {
    auditState.value = 'DANGER';
    auditMessage.value = error;
    scanInput.value = '';
    focusScan();
    return;
  }
  try {
    const payload = await sortingApi.routeCalculateScan({
      stationCode: session.user?.facilityCode || '',
      itemNo,
      operatorId: session.user?.userId || null,
      deviceId: 'SCAN-GUN-STUB',
      idempotencyKey: `route-scan-${Date.now()}-${++routeScanSequence}`,
    }, session.token);
    latest.value = payload;
    auditState.value = payload.securityStatus || 'PASS';
    auditMessage.value = payload.message || 'OK';
    rows.value.unshift({
      itemNo: payload.itemNo,
      routeCode: payload.routeCode,
      nextHop: payload.nextHop,
      securityStatus: payload.securityStatus,
    });
  } catch (error) {
    auditState.value = 'DANGER';
    auditMessage.value = error.message;
  } finally {
    scanInput.value = '';
    focusScan();
  }
}

async function refresh() {
  loading.value = true;
  try {
    const history = await sortingApi.listLinesByRoute(session.token);
    rows.value = history.slice().reverse().map((row) => ({
      itemNo: row.itemNo,
      routeCode: row.toPackageNo || row.targetCenterCode || row.manifestNo || '-',
      nextHop: row.targetCenterCode || row.toPackageNo || '-',
      securityStatus: row.ext && String(row.ext).includes('DANGER') ? 'DANGER' : 'PASS',
    }));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  refresh();
  focusScan();
});
</script>
