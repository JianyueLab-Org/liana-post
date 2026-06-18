<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">路单管理</h3>
          <p class="mt-1 text-sm text-gray-500">展示上游预告路单，支持查看预报总包与内件明细。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="refresh">刷新</button>
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows" id-key="manifestNo">
        <template #actions="{ row }">
          <button class="btn btn-primary" @click="open(row)">详情</button>
        </template>
      </DataTable>
    </div>

    <DetailDrawer :open="drawerOpen" title="路单详情" :subtitle="current?.manifestNo" @close="drawerOpen = false">
      <div v-if="detail" class="space-y-4">
        <div class="grid gap-3 md:grid-cols-2 text-sm">
          <div>路单号：{{ detail.manifestNo }}</div>
          <div>类型：{{ detail.manifestType }}</div>
          <div>来源机构：{{ detail.sourceOrgCode }}</div>
          <div>目的机构：{{ detail.destinationOrgCode }}</div>
          <div>状态：{{ detail.manifestStatus }}</div>
          <div>预告：{{ detail.prealertFlag }}</div>
          <div>预期总包数：{{ detail.expectedPackageQty }}</div>
          <div>预期邮件数：{{ detail.expectedItemQty }}</div>
        </div>
        <div>
          <h4 class="mb-2 text-sm font-semibold">明细</h4>
          <div class="space-y-2 text-sm text-gray-700">
            <div v-for="item in detail.items" :key="item.id" class="rounded-lg border border-gray-200 p-3">
              <div>邮件号：{{ item.itemNo }}</div>
              <div>预报总包：{{ item.expectedPackageNo }}</div>
              <div>路由：{{ item.expectedRouteCode }}</div>
            </div>
            <div v-if="!detail.items?.length" class="text-gray-400">暂无明细</div>
          </div>
        </div>
      </div>
      <pre v-else class="whitespace-pre-wrap text-sm text-gray-700">{{ current }}</pre>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const rows = ref([]);
const drawerOpen = ref(false);
const current = ref(null);
const detail = ref(null);

const columns = [
  { key: 'manifestNo', label: '路单号' },
  { key: 'manifestType', label: '类型' },
  { key: 'sourceOrgCode', label: '来源机构' },
  { key: 'destinationOrgCode', label: '目的机构' },
  { key: 'manifestStatus', label: '状态' },
  { key: 'expectedPackageQty', label: '预期总包' },
  { key: 'expectedItemQty', label: '预期邮件' },
  { key: 'actions', label: '操作' },
];

async function refresh() {
  loading.value = true;
  try {
    rows.value = await sortingApi.listManifests(session.token, { receiveCandidate: false });
  } finally {
    loading.value = false;
  }
}

async function open(row) {
  current.value = row;
  drawerOpen.value = true;
  detail.value = await sortingApi.getManifest(row.manifestNo, session.token);
}

onMounted(refresh);
</script>
