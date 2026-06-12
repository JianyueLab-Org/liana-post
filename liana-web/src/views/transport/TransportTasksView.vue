<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">运输任务</h3>
          <p class="mt-1 text-sm text-gray-500">从邮袋自动串联任务，也支持手工建任务。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" @click="load">刷新</button>
          <button class="btn btn-secondary" @click="openFromBag">从邮袋创建</button>
          <button class="btn btn-primary" @click="openCreate">手工建任务</button>
        </div>
      </div>
      <div class="mt-4 grid-three">
        <input v-model="query.keyword" class="input" placeholder="任务编码" />
        <select v-model="query.status" class="select">
          <option value="">全部状态</option>
          <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <input v-model.number="query.pageSize" class="input" type="number" min="1" placeholder="每页条数" />
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows">
        <template #actions="{ row }">
          <button class="btn btn-secondary" @click="openStatus(row)">改状态</button>
        </template>
      </DataTable>
    </div>

    <DetailDrawer :open="drawerOpen" :title="editing ? '手工建任务' : '新建任务'" :subtitle="form.taskCode || '自动生成任务号'" @close="drawerOpen = false">
      <div class="space-y-3">
        <input v-model="form.taskCode" class="input" placeholder="任务编码，可留空自动生成" />
        <input v-model.number="form.dispatchBagId" class="input" type="number" placeholder="dispatchBagId" />
        <input v-model.number="form.assetId" class="input" type="number" placeholder="资产ID（可选）" />
        <input v-model.number="form.routeId" class="input" type="number" placeholder="线路ID（可选）" />
        <input v-model.number="form.scheduleId" class="input" type="number" placeholder="计划ID（可选）" />
        <select v-model="form.status" class="select">
          <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <button class="btn btn-primary w-full" @click="save">保存</button>
      </div>
    </DetailDrawer>

    <DetailDrawer :open="dispatchDrawerOpen" title="从邮袋创建任务" subtitle="输入 dispatchBagId 直接串联任务" @close="dispatchDrawerOpen = false">
      <div class="space-y-3">
        <input v-model.number="dispatchBagId" class="input" type="number" placeholder="dispatchBagId" />
        <button class="btn btn-primary w-full" @click="createFromBag">创建任务</button>
      </div>
    </DetailDrawer>

    <DetailDrawer :open="statusDrawerOpen" title="修改任务状态" :subtitle="current?.taskCode" @close="statusDrawerOpen = false">
      <div class="space-y-3">
        <select v-model="statusForm.status" class="select">
          <option v-for="s in statusOptions" :key="s" :value="s">{{ s }}</option>
        </select>
        <button class="btn btn-primary w-full" @click="saveStatus">提交</button>
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
const current = ref(null);
const drawerOpen = ref(false);
const dispatchDrawerOpen = ref(false);
const statusDrawerOpen = ref(false);
const editing = ref(false);
const query = reactive({ page: 1, pageSize: 10, keyword: '', status: '' });
const dispatchBagId = ref('');
const statusOptions = ['CREATED', 'ASSIGNED', 'DEPARTED', 'IN_TRANSIT', 'ARRIVED', 'COMPLETED', 'CANCELLED'];
const columns = [
  { key: 'taskCode', label: '任务编码' },
  { key: 'dispatchBagId', label: 'dispatchBagId' },
  { key: 'assetId', label: '资产ID' },
  { key: 'routeId', label: '线路ID' },
  { key: 'scheduleId', label: '计划ID' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作' },
];
const form = reactive({ taskCode: '', dispatchBagId: '', assetId: '', routeId: '', scheduleId: '', status: 'CREATED' });
const statusForm = reactive({ status: 'ASSIGNED' });

async function load() {
  const result = await transportApi.listTasks(query, session.token);
  rows.value = result.list || [];
}

function openCreate() {
  editing.value = false;
  Object.assign(form, { taskCode: '', dispatchBagId: '', assetId: '', routeId: '', scheduleId: '', status: 'CREATED' });
  drawerOpen.value = true;
}

function openFromBag() {
  dispatchBagId.value = '';
  dispatchDrawerOpen.value = true;
}

function openStatus(row) {
  current.value = row;
  statusForm.status = row.status || 'ASSIGNED';
  statusDrawerOpen.value = true;
}

async function save() {
  await transportApi.createTask({
    taskCode: form.taskCode || undefined,
    dispatchBagId: Number(form.dispatchBagId),
    assetId: form.assetId === '' ? null : Number(form.assetId),
    routeId: form.routeId === '' ? null : Number(form.routeId),
    scheduleId: form.scheduleId === '' ? null : Number(form.scheduleId),
    status: form.status || undefined,
  }, session.token);
  drawerOpen.value = false;
  await load();
}

async function createFromBag() {
  await transportApi.createTaskFromDispatchBag(Number(dispatchBagId.value), session.token);
  dispatchDrawerOpen.value = false;
  await load();
}

async function saveStatus() {
  await transportApi.updateTaskStatus(current.value.taskCode, { status: statusForm.status }, session.token);
  statusDrawerOpen.value = false;
  await load();
}

onMounted(load);
</script>
