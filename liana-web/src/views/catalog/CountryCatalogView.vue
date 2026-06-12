<template>
  <div class="space-y-6">
    <div class="card p-5 hero-panel">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h3 class="card-title">国家管理</h3>
          <p class="mt-1 text-sm text-gray-500">预留通邮、UPU 区域和服务能力映射。</p>
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
  { key: 'englishName', label: '英文名' },
  { key: 'postalEnabled', label: '通邮' },
  { key: 'upuRegion', label: 'UPU 区域' },
  { key: 'remark', label: '备注' },
];

async function load() {
  rows.value = await mailApi.listCountries(session.token);
}

onMounted(load);
</script>
