<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">同步任务</h3>
          <p class="mt-1 text-sm text-gray-500">查看跨服务同步任务、失败原因和重试状态。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
          <button class="btn btn-primary" :disabled="loading" @click="retry">重试任务</button>
        </div>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #title>任务列表</template>
      <template #subtitle>失败任务会记录重试次数和下次补偿时间。</template>
    </GenericListView>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { syncApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const rows = ref([]);
const statusOptions = ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'];
const columns = [
  { key: 'taskNo', label: '任务编号' },
  { key: 'sourceService', label: '来源服务' },
  { key: 'targetService', label: '目标服务' },
  { key: 'status', label: '状态' },
  { key: 'retryCount', label: '重试次数' },
  { key: 'nextRetryTime', label: '下次重试' },
  { key: 'lastError', label: '错误信息' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await syncApi.listTasks(session.token);
  } finally {
    loading.value = false;
  }
}

async function retry() {
  loading.value = true;
  try {
    await syncApi.retryTasks(session.token);
    await load();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
