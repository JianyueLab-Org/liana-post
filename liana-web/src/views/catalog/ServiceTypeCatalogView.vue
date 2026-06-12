<template>
  <div class="space-y-6">
    <div class="card p-5 hero-panel">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="card-title">服务类型</h3>
          <p class="mt-1 text-sm text-gray-500">当前仅保留航空、SAL、海运三类示例。</p>
        </div>
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import { mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const columns = [
  { key: 'code', label: '代码' },
  { key: 'name', label: '名称' },
  { key: 'description', label: '说明' },
  { key: 'enabled', label: '启用' },
];

async function load() {
  rows.value = await mailApi.listServiceTypes(session.token);
}

onMounted(load);
</script>
