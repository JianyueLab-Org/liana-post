<template>
  <div class="space-y-4">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">邮件台账</h3>
          <p class="mt-1 text-sm text-gray-500">列表页仅查询与详情，收寄入口在单独页面。</p>
        </div>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #actions="{ row }">
        <button class="btn btn-secondary" @click="open(row)">详情抽屉</button>
        <button class="btn btn-ghost" :disabled="!canEdit">编辑</button>
      </template>
    </GenericListView>

    <DetailDrawer :open="drawerOpen" title="邮件详情" :subtitle="current?.waybillNo" @close="drawerOpen = false">
      <div class="grid-two">
        <div class="card p-4">
          <p class="text-sm text-gray-500">结构化数据</p>
          <pre class="mt-3 whitespace-pre-wrap text-sm text-gray-700">{{ current }}</pre>
        </div>
        <div class="card p-4">
          <p class="text-sm text-gray-500">可用操作</p>
          <div class="mt-3 flex gap-2">
            <button class="btn btn-primary" :disabled="!canEdit">更新状态</button>
            <button class="btn btn-secondary">打印面单</button>
          </div>
        </div>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { useSessionStore } from '../../stores/session';
import { mailApi } from '../../lib/api';

const session = useSessionStore();
const rows = ref([]);
const statusOptions = ['CREATED', 'ACCEPTED', 'SORTED', 'DISPATCHED', 'ARRIVED', 'DELIVERED', 'RETURNED'];
const columns = [
  { key: 'waybillNo', label: '运单号' },
  { key: 'senderFullName', label: '寄件人' },
  { key: 'recipientFullName', label: '收件人' },
  { key: 'mailScope', label: '邮件范围' },
  { key: 'status', label: '状态' },
  { key: 'weightGrams', label: '重量(g)' },
  { key: 'bagNo', label: '总包号' },
  { key: 'actions', label: '操作' },
];
const drawerOpen = ref(false);
const current = ref(null);
const canEdit = computed(() => session.hasAction('MAIL_CREATE'));

async function load() {
  rows.value = await mailApi.list(session.token);
}

function open(row) {
  current.value = row;
  drawerOpen.value = true;
}

onMounted(load);
</script>
