<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">重试记录</h3>
          <p class="mt-1 text-sm text-gray-500">查看同步任务失败后的补偿尝试、计划时间和错误信息。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #title>重试明细</template>
      <template #subtitle>待重试记录会在定时任务或手动重试后更新状态。</template>
    </GenericListView>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { syncApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';
import GenericListView from '../shared/GenericListView.vue';

const session = useSessionStore();
const loading = ref(false);
const rows = ref([]);
const statusOptions = ['PENDING', 'SUCCESS', 'FAILED'];
const columns = [
  { key: 'bizType', label: '业务类型' },
  { key: 'bizId', label: '业务编号' },
  { key: 'attemptNo', label: '尝试次数' },
  { key: 'status', label: '状态' },
  { key: 'retryTime', label: '重试时间' },
  { key: 'errorMessage', label: '错误信息' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await syncApi.listRetries(session.token);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
