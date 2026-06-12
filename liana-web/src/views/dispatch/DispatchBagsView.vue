<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">邮袋管理</h3>
          <p class="mt-1 text-sm text-gray-500">营业员可建袋、查看邮袋，并进入批次审批/交接流程。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" @click="goCreate">创建总包</button>
          <button class="btn btn-secondary" @click="goHandoff">交接确认</button>
        </div>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #actions="{ row }">
        <button class="btn btn-secondary" @click="open(row)">详情抽屉</button>
      </template>
    </GenericListView>

    <DetailDrawer :open="drawerOpen" title="邮袋详情" :subtitle="current?.bagNo" @close="drawerOpen = false">
      <pre class="whitespace-pre-wrap text-sm text-gray-700">{{ current }}</pre>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import GenericListView from '../shared/GenericListView.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { dispatchApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const router = useRouter();
const session = useSessionStore();
const rows = ref([]);
const statusOptions = ['CREATED', 'REVIEWED'];
const columns = [{ key: 'bagNo', label: '邮袋号' }, { key: 'originFacilityCode', label: '起点' }, { key: 'destinationFacilityCode', label: '终点' }, { key: 'routeCode', label: '路线' }, { key: 'status', label: '状态' }, { key: 'mailCount', label: '邮件数' }, { key: 'totalWeightGrams', label: '总重(g)' }, { key: 'actions', label: '操作' }];
const drawerOpen = ref(false);
const current = ref(null);

async function load() {
  rows.value = (await dispatchApi.listBags(session.token)).filter((item) => item.status !== 'DISPATCHED');
}
function open(row) { current.value = row; drawerOpen.value = true; }
function goCreate() { router.push('/dispatch/create-bag'); }
function goHandoff() { router.push('/dispatch/handoff'); }
onMounted(load);
</script>
