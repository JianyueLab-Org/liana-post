<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">批次管理</h3>
          <p class="mt-1 text-sm text-gray-500">按起点、终点和路线将已封总包编入同一运输批次。</p>
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
        <button class="btn btn-secondary" :disabled="row.status !== 'PENDING'" @click="approve(row)">审批通过</button>
      </template>
    </GenericListView>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { dispatchApi } from '../../lib/api';
import { notifyApiError } from '../../lib/apiErrorEvents';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const bags = ref([]);
const selectedBagNos = ref([]);
const loading = ref(false);
const statusOptions = ['PENDING', 'APPROVED', 'HANDED_OFF', 'CANCELLED'];
const columns = [
  { key: 'batchNo', label: '批次号' },
  { key: 'bagNo', label: '邮袋号' },
  { key: 'bagNos', label: '总包清单' },
  { key: 'routeCode', label: '路线' },
  { key: 'status', label: '状态' },
  { key: 'approvedBy', label: '审批人' },
  { key: 'approvedAt', label: '审批时间' },
  { key: 'actions', label: '操作' },
];
const selectedBags = computed(() => bags.value.filter((bag) => selectedBagNos.value.includes(bag.bagNo)));
const availableBags = computed(() => {
  if (!selectedBags.value.length) return bags.value.filter((bag) => ['CREATED', 'REVIEWED'].includes(bag.status));
  const first = selectedBags.value[0];
  return bags.value.filter((bag) => ['CREATED', 'REVIEWED'].includes(bag.status)
    && bag.originFacilityCode === first.originFacilityCode
    && bag.destinationFacilityCode === first.destinationFacilityCode
    && bag.routeCode === first.routeCode);
});

watch(availableBags, (next) => {
  const available = new Set(next.map((bag) => bag.bagNo));
  selectedBagNos.value = selectedBagNos.value.filter((bagNo) => available.has(bagNo));
});

async function refresh() {
  loading.value = true;
  try {
    [rows.value, bags.value] = await Promise.all([
      dispatchApi.listBatches(session.token),
      dispatchApi.listBags(session.token),
    ]);
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

onMounted(refresh);
</script>
