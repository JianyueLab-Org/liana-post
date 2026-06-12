<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">运输线路</h3>
          <p class="mt-1 text-sm text-gray-500">维护线路、运输类型和预计时长。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" @click="load">刷新</button>
          <button class="btn btn-primary" @click="openCreate">新增</button>
        </div>
      </div>
      <div class="mt-4 grid-three">
        <input v-model="query.keyword" class="input" placeholder="线路编码" />
        <select v-model="query.status" class="select">
          <option value="">全部状态</option>
          <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <select v-model="query.transportType" class="select">
          <option value="">全部运输类型</option>
          <option v-for="s in transportTypes" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows">
        <template #actions="{ row }">
          <button class="btn btn-secondary" @click="openEdit(row)">编辑</button>
        </template>
      </DataTable>
    </div>

    <DetailDrawer :open="drawerOpen" :title="editing ? '编辑线路' : '新增线路'" :subtitle="form.routeCode || '未命名线路'" @close="drawerOpen = false">
      <div class="space-y-3">
        <input v-model="form.routeCode" class="input" placeholder="线路编码" />
        <div class="space-y-1">
          <label class="text-xs text-gray-500">起始设施</label>
          <select v-model="form.originFacilityCode" class="select">
            <option value="">请选择起始设施</option>
            <option v-for="item in facilities" :key="item.facilityCode" :value="item.facilityCode">
              {{ item.facilityCode }} - {{ item.name }}
            </option>
          </select>
        </div>
        <div class="space-y-1">
          <label class="text-xs text-gray-500">目的设施</label>
          <select v-model="form.destinationFacilityCode" class="select">
            <option value="">请选择目的设施</option>
            <option v-for="item in facilities" :key="item.facilityCode" :value="item.facilityCode">
              {{ item.facilityCode }} - {{ item.name }}
            </option>
          </select>
        </div>
        <select v-model="form.transportType" class="select">
          <option v-for="s in transportTypes" :key="s" :value="s">{{ s }}</option>
        </select>
        <input v-model="form.estimatedHours" class="input" type="number" step="0.1" placeholder="预计小时数" />
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
import { transportApi, facilityApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const transportTypes = ['SEA', 'AIR', 'LAND'];
const statusOptions = ['ACTIVE', 'PLANNED', 'DISABLED'];
const columns = [
  { key: 'routeCode', label: '线路编码' },
  { key: 'originFacilityId', label: '起始设施ID' },
  { key: 'destinationFacilityId', label: '目的设施ID' },
  { key: 'transportType', label: '运输类型' },
  { key: 'estimatedHours', label: '预计小时数' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' },
];
const query = reactive({ page: 1, pageSize: 10, keyword: '', status: '', transportType: '' });
const rows = ref([]);
const facilities = ref([]);
const drawerOpen = ref(false);
const editing = ref(false);
const form = reactive({ routeCode: '', originFacilityCode: '', destinationFacilityCode: '', transportType: 'LAND', estimatedHours: '', status: 'ACTIVE' });

async function loadFacilities() {
  facilities.value = await facilityApi.listFacilities(session.token);
}

async function load() {
  const result = await transportApi.listRoutes(query, session.token);
  rows.value = result.list || [];
}

function openCreate() {
  editing.value = false;
  Object.assign(form, { routeCode: '', originFacilityCode: '', destinationFacilityCode: '', transportType: 'LAND', estimatedHours: '', status: 'ACTIVE' });
  drawerOpen.value = true;
}

function openEdit(row) {
  editing.value = true;
  Object.assign(form, {
    routeCode: row.routeCode || '',
    originFacilityCode: row.originFacilityId ? String(row.originFacilityId) : '',
    destinationFacilityCode: row.destinationFacilityId ? String(row.destinationFacilityId) : '',
    transportType: row.transportType || 'LAND',
    estimatedHours: row.estimatedHours ?? '',
    status: row.status || 'ACTIVE',
  });
  drawerOpen.value = true;
}

async function save() {
  const payload = {
    routeCode: form.routeCode,
    originFacilityCode: form.originFacilityCode,
    destinationFacilityCode: form.destinationFacilityCode,
    transportType: form.transportType,
    estimatedHours: form.estimatedHours === '' ? null : Number(form.estimatedHours),
    status: form.status,
  };
  if (editing.value) {
    await transportApi.updateRoute(form.routeCode, payload, session.token);
  } else {
    await transportApi.createRoute(payload, session.token);
  }
  drawerOpen.value = false;
  await load();
}

onMounted(async () => {
  await Promise.all([loadFacilities(), load()]);
});
</script>
