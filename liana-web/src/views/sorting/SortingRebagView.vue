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
        <button class="btn btn-primary mt-4 w-full" :disabled="!slot.pendingCount || sealing" @click="seal(slot)">
          {{ sealing ? '封包中...' : '满袋封包' }}
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
  try {
    await sortingApi.sealBagBySlot({
      slotCode: slot.slotCode,
      stationCode: session.user?.facilityCode || '',
      operatorId: session.user?.userId || null,
    }, session.token);
    await refresh();
  } finally {
    sealing.value = false;
  }
}

onMounted(refresh);
</script>
