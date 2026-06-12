<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title"><slot name="title">列表页</slot></h3>
          <p class="mt-1 text-sm text-gray-500"><slot name="subtitle">支持分页、过滤、状态标记与详情抽屉</slot></p>
        </div>
      </div>
      <div class="mt-4 grid-three">
        <input v-model="filters.keyword" class="input" placeholder="关键词过滤" />
        <select v-model="filters.status" class="select">
          <option value="">全部状态</option>
          <option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</option>
        </select>
        <input v-model="filters.date" class="input" type="date" />
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="pagedRows">
        <template #status="{ row }"><StatusBadge :status="row.status" /></template>
        <template #actions="{ row }"><slot name="actions" :row="row" /></template>
      </DataTable>
      <div class="mt-4 flex items-center justify-between text-sm text-gray-500">
        <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="page === 1" @click="page--">上一页</button>
          <button class="btn btn-secondary" :disabled="page * pageSize >= total" @click="page++">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import StatusBadge from '../../components/StatusBadge.vue';

const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  statusOptions: { type: Array, default: () => [] },
});

const filters = reactive({ keyword: '', status: '', date: '' });
const page = ref(1);
const pageSize = 10;

const filteredRows = computed(() => props.rows.filter((row) => {
  const keywordHit = !filters.keyword || Object.values(row).some((value) => String(value).includes(filters.keyword));
  const statusHit = !filters.status || row.status === filters.status;
  const dateHit = !filters.date || String(row.createdAt || row.lastRunAt || '').startsWith(filters.date);
  return keywordHit && statusHit && dateHit;
}));

const total = computed(() => filteredRows.value.length);
const pagedRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize));
</script>
