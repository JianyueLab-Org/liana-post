<template>
  <div class="grid gap-6 xl:grid-cols-[0.9fr_1.5fr]">
    <section class="card p-5">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h3 class="card-title">自动路由安检</h3>
          <p class="mt-1 text-sm text-gray-500">当前机构：{{ stationCode || '-' }}</p>
        </div>
        <span class="rounded-full px-3 py-1 text-xs font-medium" :class="auditBadgeClass">{{ auditStateText }}</span>
      </div>

      <div class="mt-5">
        <input
          ref="scanInputRef"
          class="input text-lg font-semibold tracking-[0.12em]"
          :value="queueHead?.itemNo || ''"
          placeholder="待路由队列为空"
          readonly
          autofocus
          @keydown.enter.prevent="submitHead"
        />
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <button class="btn btn-primary" :disabled="loading || !queue.length" @click="submitHead">
          {{ loading ? '处理中...' : '处理队首' }}
        </button>
        <button class="btn btn-secondary" :disabled="loading" @click="refreshPending">刷新队列</button>
      </div>

      <div class="mt-5 overflow-hidden rounded-2xl border border-gray-200">
        <table class="table">
          <thead>
            <tr>
              <th>邮件单号</th>
              <th>类型</th>
              <th>开拆时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in queue" :key="item.itemNo">
              <td class="font-medium text-gray-900">{{ item.itemNo }}</td>
              <td>{{ scopeText(item) }}</td>
              <td>{{ formatTime(item.unpackedAt) }}</td>
            </tr>
            <tr v-if="!queue.length">
              <td colspan="3" class="py-8 text-center text-sm text-gray-400">暂无待路由邮件</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="space-y-6">
      <div class="card p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 class="card-title">安检结果</h3>
            <p class="mt-1 text-sm" :class="auditTextClass">{{ auditMessage }}</p>
          </div>
          <div class="text-right text-sm text-gray-500">
            <div>下一跳</div>
            <div class="font-semibold text-gray-900">{{ latest?.nextHop || '-' }}</div>
          </div>
        </div>

        <div class="mt-5 grid gap-5 lg:grid-cols-[1.35fr_0.8fr]">
          <div class="overflow-hidden rounded-lg border border-gray-200 bg-gray-950">
            <img
              v-if="latest?.xrayImage"
              :src="latest.xrayImage"
              :alt="latest.sourceImageName || 'x-ray'"
              class="block h-full max-h-[520px] w-full object-contain"
            />
            <div v-else class="flex h-80 items-center justify-center text-sm text-gray-400">等待安检图像</div>
          </div>

          <div class="space-y-4">
            <div class="rounded-lg border border-gray-200 p-4 text-sm text-gray-700">
              <div>邮件单号：<span class="font-semibold text-gray-900">{{ latest?.itemNo || '-' }}</span></div>
              <div class="mt-2">路由格口：<span class="font-semibold text-gray-900">{{ latest?.routeCode || '-' }}</span></div>
              <div class="mt-2">邮件类型：<span class="font-semibold text-gray-900">{{ latestScopeText }}</span></div>
              <div class="mt-2">原寄局：<span class="font-semibold text-gray-900">{{ latest?.originFacilityCode || '-' }}</span></div>
              <div class="mt-2">样本图：<span class="font-semibold text-gray-900">{{ latest?.sourceImageName || '-' }}</span></div>
            </div>

            <div class="rounded-lg border border-gray-200 p-4">
              <div class="text-sm font-semibold text-gray-900">识别标签</div>
              <div class="mt-3 flex flex-wrap gap-2">
                <span
                  v-for="label in detectedLabels"
                  :key="`${label.label}-${label.x}-${label.y}`"
                  class="rounded-full px-3 py-1 text-xs font-medium"
                  :class="isDangerLabel(label.label) ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-700'"
                >
                  {{ label.label }}
                </span>
                <span v-if="!detectedLabels.length" class="text-sm text-gray-400">未检测到标签</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card p-5">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h3 class="card-title">处理轨迹</h3>
            <p class="mt-1 text-sm text-gray-500">最近的自动路由处理结果</p>
          </div>
          <button class="btn btn-secondary" :disabled="historyLoading" @click="refreshHistory">刷新</button>
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
              <tr v-for="row in rows" :key="`${row.itemNo}-${row.eventTime || row.routeCode}`">
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
                <td colspan="4" class="py-10 text-center text-sm text-gray-400">暂无处理记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const scanInputRef = ref(null);
const queue = ref([]);
const rows = ref([]);
const loading = ref(false);
const historyLoading = ref(false);
const latest = ref(null);
const auditState = ref('PASS');
const auditMessage = ref('等待队列处理');
let routeScanSequence = 0;

const stationCode = computed(() => session.user?.facilityCode || '');
const queueHead = computed(() => queue.value[0] || null);
const detectedLabels = computed(() => latest.value?.detectedLabels || []);
const dangerLabels = computed(() => latest.value?.dangerLabels || []);
const auditStateText = computed(() => (auditState.value === 'DANGER' ? '拦截' : '通过'));
const auditBadgeClass = computed(() => (auditState.value === 'DANGER' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'));
const auditTextClass = computed(() => (auditState.value === 'DANGER' ? 'text-red-700' : 'text-emerald-700'));
const latestScopeText = computed(() => {
  if (!latest.value) return '-';
  return `${latest.value.mailScope || '-'} / ${latest.value.serviceType || '-'}`;
});

function focusScan() {
  nextTick(() => scanInputRef.value?.focus());
}

function scopeText(item) {
  return `${item.mailScope || '-'} / ${item.serviceType || '-'}`;
}

function formatTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 19);
}

function isDangerLabel(label) {
  return dangerLabels.value.includes(label);
}

function parseExt(ext) {
  if (!ext) return {};
  try {
    return JSON.parse(ext);
  } catch {
    return {};
  }
}

async function submitHead() {
  if (!queueHead.value || loading.value) {
    auditMessage.value = '暂无待路由邮件';
    focusScan();
    return;
  }
  loading.value = true;
  const itemNo = queueHead.value.itemNo;
  try {
    const payload = await sortingApi.routeCalculateScan({
      stationCode: stationCode.value,
      itemNo,
      operatorId: session.user?.userId || null,
      deviceId: 'AUTO-ROUTE-XRAY',
      idempotencyKey: `route-xray-${Date.now()}-${++routeScanSequence}`,
    }, session.token);
    latest.value = payload;
    auditState.value = payload.securityStatus || 'PASS';
    auditMessage.value = payload.message || 'OK';
    rows.value.unshift({
      itemNo: payload.itemNo,
      routeCode: payload.routeCode,
      nextHop: payload.nextHop,
      securityStatus: payload.securityStatus,
      eventTime: payload.auditedAt,
    });
    await refreshPending();
  } catch (error) {
    auditState.value = 'DANGER';
    auditMessage.value = error.message;
  } finally {
    loading.value = false;
    focusScan();
  }
}

async function refreshPending() {
  queue.value = await sortingApi.listPendingRouteItems(stationCode.value, session.token);
  focusScan();
}

async function refreshHistory() {
  historyLoading.value = true;
  try {
    const history = await sortingApi.listLinesByRoute(session.token);
    rows.value = history
      .filter((row) => row.eventType === 'ROUTE')
      .slice()
      .reverse()
      .slice(0, 20)
      .map((row) => {
        const ext = parseExt(row.ext);
        return {
          itemNo: row.itemNo,
          routeCode: row.toPackageNo || row.targetCenterCode || '-',
          nextHop: ext.nextHop || row.targetCenterCode || row.toPackageNo || '-',
          securityStatus: ext.securityStatus || (String(row.ext || '').includes('DANGER') ? 'DANGER' : 'PASS'),
          eventTime: row.eventTime,
        };
      });
  } finally {
    historyLoading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([refreshPending(), refreshHistory()]);
  focusScan();
});
</script>
