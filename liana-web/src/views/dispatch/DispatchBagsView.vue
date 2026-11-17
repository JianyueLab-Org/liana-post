<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">邮袋管理</h3>
          <p class="mt-1 text-sm text-gray-500">建袋后清单固定，OMS 确认邮件归属后才能编批。等待重试会自动恢复；冲突总包保留记录并禁止发运。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="load">刷新状态</button>
          <button class="btn btn-primary" @click="goCreate">创建总包</button>
          <button class="btn btn-secondary" @click="goBatches">批次管理</button>
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
import { notifyApiError } from '../../lib/apiErrorEvents';
import { useSessionStore } from '../../stores/session';

const router = useRouter();
const session = useSessionStore();
const rows = ref([]);
const loading = ref(false);
const statusOptions = ['CREATED', 'REVIEWED', 'ARRIVED'];
const columns = [{ key: 'bagNo', label: '邮袋号' }, { key: 'originFacilityCode', label: '起点' }, { key: 'destinationFacilityCode', label: '终点' }, { key: 'routeCode', label: '路线' }, { key: 'status', label: '状态' }, { key: 'packingLabel', label: '装包确认' }, { key: 'packingVersion', label: '封装版本' }, { key: 'mailCount', label: '邮件数' }, { key: 'weightLabel', label: '邮件合重(g)' }, { key: 'packingError', label: '确认说明' }, { key: 'actions', label: '操作' }];
const drawerOpen = ref(false);
const current = ref(null);

async function load() {
  loading.value = true;
  try {
    const labels = { PENDING: '待确认', SENDING: '确认中', RETRY: '等待重试', CONFIRMED: '已确认', CONFLICT: '装包冲突', NOT_PREPARED: '缺少封装清单' };
    rows.value = (await dispatchApi.listBags(session.token)).filter((item) => item.status !== 'DISPATCHED')
      .map((item) => ({ ...item, packingLabel: labels[item.packingSyncStatus] || '未知', weightLabel: item.totalWeightGrams ?? '未知' }));
  } catch (error) { notifyApiError(error); }
  finally { loading.value = false; }
}
function open(row) { current.value = row; drawerOpen.value = true; }
function goCreate() { router.push('/dispatch/create-bag'); }
function goBatches() { router.push('/dispatch/batches'); }
function goHandoff() { router.push('/dispatch/handoff'); }
onMounted(load);
</script>
