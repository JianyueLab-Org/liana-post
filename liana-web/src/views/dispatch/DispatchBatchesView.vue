<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">批次管理</h3>
          <p class="mt-1 text-sm text-gray-500">建袋后先生成并审批批次，再交接，避免 dispatch_batch 缺失。</p>
        </div>
        <button class="btn btn-primary" @click="refresh">刷新</button>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #actions="{ row }">
        <button class="btn btn-secondary" :disabled="row.status === 'APPROVED'" @click="approve(row)">审批通过</button>
      </template>
    </GenericListView>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { dispatchApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const statusOptions = ['PENDING', 'APPROVED', 'HANDED_OFF'];
const columns = [
  { key: 'batchNo', label: '批次号' },
  { key: 'bagNo', label: '邮袋号' },
  { key: 'routeCode', label: '路线' },
  { key: 'status', label: '状态' },
  { key: 'approvedBy', label: '审批人' },
  { key: 'approvedAt', label: '审批时间' },
  { key: 'actions', label: '操作' },
];

async function refresh() {
  rows.value = await dispatchApi.listBatches(session.token);
}

async function approve(row) {
  await dispatchApi.approveBatch(row.batchNo, { approvedBy: session.user?.userId || null }, session.token);
  await refresh();
}

onMounted(refresh);
</script>
