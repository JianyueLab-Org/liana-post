<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">Outbox 消息</h3>
          <p class="mt-1 text-sm text-gray-500">查看待同步业务事件及其处理状态。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
          <button class="btn btn-primary" :disabled="loading" @click="scan">扫描 Outbox</button>
        </div>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #title>消息列表</template>
      <template #subtitle>业务事件先进入 Outbox，再由 Syncer 转换为同步任务。</template>
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
const statusOptions = ['NEW', 'PROCESSING', 'SUCCESS', 'FAILED'];
const columns = [
  { key: 'msgId', label: '消息编号' },
  { key: 'eventType', label: '事件类型' },
  { key: 'status', label: '状态' },
  { key: 'retryCount', label: '重试次数' },
  { key: 'nextRetryTime', label: '下次重试' },
  { key: 'createdAt', label: '创建时间' },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await syncApi.listOutbox(session.token);
  } finally {
    loading.value = false;
  }
}

async function scan() {
  loading.value = true;
  try {
    await syncApi.scanOutbox(session.token);
    await load();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
