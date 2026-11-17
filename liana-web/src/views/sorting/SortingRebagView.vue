<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">分拣机物理格口集包盘</h3>
          <p class="mt-1 text-sm text-gray-500">按格口聚合暂存件，满袋后一键封包。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="refresh">刷新</button>
      </div>
    </div>

    <section v-if="Object.keys(requests).length" class="card space-y-3 p-5 text-sm">
      <h4 class="font-semibold">本次重封请求</h4>
      <article v-for="(saved, slotCode) in requests" :key="saved.requestNo" class="rounded-lg border p-3">
        <div>{{ slotCode }} · {{ saved.bagNo || '等待提交结果' }} · {{ statusLabel(saved.status) }}</div>
        <p v-if="saved.lastError" class="mt-1 text-amber-700">{{ saved.lastError }}</p>
        <div class="mt-2 flex gap-2">
          <button class="btn btn-secondary" @click="refreshRequest(slotCode)">查询确认</button>
          <button v-if="saved.status === 'SUBMITTING'" class="btn btn-secondary" :disabled="sealing" @click="seal({ slotCode })">重试原请求</button>
          <button v-if="saved.status === 'SENT'" class="btn btn-secondary" @click="finishRequest(slotCode)">完成此轮</button>
          <RouterLink v-if="saved.status === 'SENT'" class="btn btn-primary" to="/dispatch/bags">查看 OMS 装包确认</RouterLink>
        </div>
      </article>
    </section>

    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <article v-for="slot in slots" :key="slot.slotCode" class="rounded-3xl border border-gray-200 bg-white p-5 shadow-sm">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="text-sm font-semibold text-gray-900">{{ slot.slotCode }}</div>
            <div class="mt-1 text-xs text-gray-500">{{ slot.destinationOrgCode || slot.destinationNode || '-' }}</div>
          </div>
          <span class="rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-700">{{ slot.bagStatus }}</span>
        </div>
        <div class="mt-4 flex items-end justify-between gap-3">
          <div>
            <div class="text-3xl font-bold text-gray-900">{{ slot.pendingCount }}</div>
            <div class="text-xs text-gray-500">当前积压件数</div>
          </div>
          <div class="max-w-[12rem] truncate text-right text-xs text-gray-500">
            {{ (slot.previewItemNos || []).join(', ') || '-' }}
          </div>
        </div>
        <div v-if="requests[slot.slotCode]" class="mt-3 text-xs text-gray-600">
          {{ requests[slot.slotCode].bagNo }} · {{ statusLabel(requests[slot.slotCode].status) }}
        </div>
        <button class="btn btn-primary mt-4 w-full" :disabled="!slot.pendingCount || sealing || ['PENDING','SENDING','RETRY','SENT'].includes(requests[slot.slotCode]?.status)" @click="seal(slot)">
          {{ sealingSlot === slot.slotCode ? '提交中...' : requests[slot.slotCode] ? '等待当前请求处理' : '提交结构化重封' }}
        </button>
      </article>
      <div v-if="!slots.length" class="rounded-2xl border border-dashed border-gray-300 p-8 text-center text-sm text-gray-400">
        暂无可封口格口
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const sealing = ref(false);
const slots = ref([]);
const requests = ref({});
const sealingSlot = ref('');
const requestKey = `sorting-repack:${session.user?.userId || session.user?.username || ''}:${session.user?.facilityCode || ''}`;

function statusLabel(status) {
  return ({ SUBMITTING: '提交结果待确认', PENDING: '待 dispatch 接收', SENDING: '正在发送', RETRY: '等待重试', SENT: '已创建结构化总包，OMS 装包确认中', CONFLICT: '存在冲突，需核查' }[status] || status || '未知');
}

function saveRequests() {
  sessionStorage.setItem(requestKey, JSON.stringify(requests.value));
}

async function refresh() {
  loading.value = true;
  try {
    slots.value = await sortingApi.listSlots(session.token, session.user?.facilityCode || '');
  } finally {
    loading.value = false;
  }
}

async function seal(slot) {
  if (sealing.value) return;
  sealing.value = true;
  sealingSlot.value = slot.slotCode;
  try {
    const saved = requests.value[slot.slotCode];
    const requestNo = saved?.requestNo || `REPACK-${crypto.randomUUID()}`.toUpperCase();
    const payload = saved?.payload || {
      slotCode: slot.slotCode,
      stationCode: session.user?.facilityCode || '',
      operatorId: session.user?.userId || null,
      idempotencyKey: requestNo,
    };
    requests.value = { ...requests.value, [slot.slotCode]: { ...saved, requestNo, payload, status: saved?.status || 'SUBMITTING' } };
    saveRequests();
    const response = await sortingApi.sealBagBySlot(payload, session.token);
    requests.value = { ...requests.value, [slot.slotCode]: { requestNo, payload, bagNo: response.packageNo, status: 'PENDING' } };
    saveRequests();
    await refreshRequest(slot.slotCode);
    await refresh();
  } finally {
    sealing.value = false;
    sealingSlot.value = '';
  }
}

function finishRequest(slotCode) {
  if (requests.value[slotCode]?.status !== 'SENT') return;
  const updated = { ...requests.value };
  delete updated[slotCode];
  requests.value = updated;
  saveRequests();
}

async function refreshRequest(slotCode) {
  const saved = requests.value[slotCode];
  if (!saved) return;
  const receipt = await sortingApi.repackingReceipt(saved.requestNo, session.token);
  requests.value = { ...requests.value, [slotCode]: { ...saved, status: receipt.status, lastError: receipt.lastError, bagNo: receipt.command.bagNo } };
  saveRequests();
}

async function refreshRequests() {
  for (const slotCode of Object.keys(requests.value)) {
    try { await refreshRequest(slotCode); } catch { /* Keep the saved request available for the next refresh. */ }
  }
}

onMounted(async () => {
  try { requests.value = JSON.parse(sessionStorage.getItem(requestKey) || '{}'); } catch { sessionStorage.removeItem(requestKey); }
  await Promise.all([refresh(), refreshRequests()]);
});
</script>
