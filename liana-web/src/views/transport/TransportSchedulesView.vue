<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">运输计划</h3>
          <p class="mt-1 text-sm text-gray-500">为运输资产和线路编排计划。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" @click="load">刷新</button>
          <button class="btn btn-primary" @click="openCreate">新增</button>
        </div>
      </div>
      <div class="mt-4 grid-three">
        <input v-model="query.keyword" class="input" placeholder="计划编码 / 星期" />
        <select v-model="query.status" class="select">
          <option value="">全部状态</option>
          <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <input v-model.number="query.pageSize" class="input" type="number" min="1" placeholder="每页条数" />
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows" />
      <div class="mt-4 flex items-center justify-between text-sm text-gray-500">
        <span>第 {{ page }} 页</span>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="page === 1" @click="page--; load()">上一页</button>
          <button class="btn btn-secondary" :disabled="page * query.pageSize >= total" @click="page++; load()">下一页</button>
        </div>
      </div>
    </div>

    <DetailDrawer :open="drawerOpen" :title="editing ? '编辑计划' : '新增计划'" :subtitle="form.scheduleCode || '未命名计划'" @close="drawerOpen = false">
      <div class="space-y-3">
        <input v-model="form.scheduleCode" class="input" placeholder="计划编码" />

        <div class="space-y-1">
          <label class="text-xs text-gray-500">运输资产</label>
          <select v-model="form.assetId" class="select">
            <option value="">请选择资产</option>
            <option v-for="item in assets" :key="item.id" :value="String(item.id)">
              {{ item.code }} - {{ item.name }}
            </option>
          </select>
        </div>

        <div class="space-y-1">
          <label class="text-xs text-gray-500">运输线路</label>
          <select v-model="form.routeId" class="select">
            <option value="">请选择线路</option>
            <option v-for="item in routes" :key="item.id" :value="String(item.id)">
              {{ item.routeCode }} - {{ routeDisplay(item) }}
            </option>
          </select>
        </div>

        <input v-model="form.departureTime" class="input" type="datetime-local" />
        <input v-model="form.arrivalTime" class="input" type="datetime-local" />
        <input v-model="form.weekday" class="input" placeholder="星期代码，例如 MON" />
        <select v-model="form.status" class="select">
          <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <button class="btn btn-primary w-full" @click="save">保存</button>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { transportApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const drawerOpen = ref(false);
const editing = ref(false);
const query = reactive({ page: 1, pageSize: 10, keyword: '', status: '' });
const statusOptions = ['PLANNED', 'ACTIVE', 'SUSPENDED'];
const columns = [
  { key: 'scheduleCode', label: '计划编码' },
  { key: 'assetId', label: '资产ID' },
  { key: 'routeId', label: '线路ID' },
  { key: 'departureTime', label: '出发时间' },
  { key: 'arrivalTime', label: '到达时间' },
  { key: 'weekday', label: '星期' },
  { key: 'status', label: '状态' },
];
const assets = ref([]);
const routes = ref([]);
const form = reactive({ scheduleCode: '', assetId: '', routeId: '', departureTime: '', arrivalTime: '', weekday: 'MON', status: 'PLANNED' });

function routeDisplay(item) {
  return `${item.originFacilityId} -> ${item.destinationFacilityId}`;
}

async function loadAssets() {
  const result = await transportApi.listAssets({ page: 1, pageSize: 100 }, session.token);
  assets.value = result.list || [];
}

async function loadRoutes() {
  const result = await transportApi.listRoutes({ page: 1, pageSize: 100 }, session.token);
  routes.value = result.list || [];
}

async function load() {
  query.page = page.value;
  const result = await transportApi.listSchedules(query, session.token);
  rows.value = result.list || [];
  total.value = result.total || 0;
  page.value = result.page || 1;
}

function openCreate() {
  editing.value = false;
  Object.assign(form, { scheduleCode: '', assetId: '', routeId: '', departureTime: '', arrivalTime: '', weekday: 'MON', status: 'PLANNED' });
  drawerOpen.value = true;
}

async function save() {
  const payload = {
    scheduleCode: form.scheduleCode,
    assetId: Number(form.assetId),
    routeId: Number(form.routeId),
    departureTime: form.departureTime ? `${form.departureTime}:00` : null,
    arrivalTime: form.arrivalTime ? `${form.arrivalTime}:00` : null,
    weekday: form.weekday,
    status: form.status,
  };
  if (editing.value) {
    await transportApi.updateSchedule(form.scheduleCode, payload, session.token);
  } else {
    await transportApi.createSchedule(payload, session.token);
  }
  drawerOpen.value = false;
  await load();
}

onMounted(async () => {
  await Promise.all([loadAssets(), loadRoutes(), load()]);
});
</script>
