<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">运输任务</h3>
          <p class="mt-1 text-sm text-gray-500">按已封路单整单装载；同一路段的多张路单可共用一个航班、船班或陆运任务。</p>
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
          <option v-for="s in statusOptions" :key="s" :value="s">{{ labels[s] || s }}</option>
        </select>
        <input v-model.number="query.pageSize" class="input" type="number" min="1" placeholder="每页条数" />
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows">
        <template #actions="{ row }">
          <button v-if="row.status === 'CREATED'" class="btn btn-secondary" @click="openAssign(row)">指派班次</button>
          <button v-if="['CREATED', 'ASSIGNED'].includes(row.status)" class="btn btn-secondary" @click="openAdd(row)">添加路单</button>
          <button class="btn btn-secondary" @click="openHistory(row)">分配历史</button>
          <button v-if="!['CANCELLING', 'CANCELLED', 'COMPLETED'].includes(row.status)" class="btn btn-secondary" @click="openStatus(row)">改状态</button>
        </template>
      </DataTable>
    </div>

    <DetailDrawer :open="drawerOpen" :title="editing ? '手工建任务' : '新建任务'" :subtitle="form.taskCode || '自动生成任务号'" @close="drawerOpen = false">
      <div class="space-y-3">
        <input v-model="form.taskCode" class="input" placeholder="任务编码，可留空自动生成" />
        <input v-model="form.manifestNo" class="input" placeholder="已封路单号" />
        <select v-model="form.scheduleId" class="select">
          <option value="">暂不指派班次</option>
          <option v-for="item in schedules" :key="item.id" :value="String(item.id)">{{ item.scheduleCode }} · {{ lianaTime(utc(item.departureTime)) }}</option>
        </select>
        <button class="btn btn-primary w-full" :disabled="busy || !form.manifestNo.trim()" @click="save">保存</button>
      </div>
    </DetailDrawer>
    <DetailDrawer :open="assignDrawerOpen" title="指派运输班次" :subtitle="current?.taskCode" @close="assignDrawerOpen = false">
      <div class="space-y-3">
        <select v-model="assignScheduleId" class="select">
          <option value="">请选择未来班次</option>
          <option v-for="item in schedules" :key="item.id" :value="String(item.id)">{{ item.scheduleCode }} · {{ item.departureTime }}</option>
        </select>
        <button class="btn btn-primary w-full" :disabled="busy || !assignScheduleId" @click="saveAssignment">确认指派</button>
      </div>
    </DetailDrawer>

    <DetailDrawer :open="dispatchDrawerOpen" title="从邮袋创建任务" subtitle="将该总包所属整张已封路单加入新任务，随后指派班次" @close="dispatchDrawerOpen = false">
      <div class="space-y-3">
        <input v-model.number="dispatchBagId" class="input" type="number" placeholder="dispatchBagId" />
        <button class="btn btn-primary w-full" :disabled="busy || !dispatchBagId" @click="createFromBag">创建任务</button>
      </div>
    </DetailDrawer>

    <DetailDrawer :open="statusDrawerOpen" title="修改任务状态" :subtitle="current?.taskCode" @close="statusDrawerOpen = false">
      <div class="space-y-3">
        <select v-model="statusForm.status" class="select">
          <option v-for="s in nextStatuses" :key="s" :value="s">{{ labels[s] || s }}</option>
        </select>
        <textarea v-if="statusForm.status === 'CANCELLED'" v-model="statusForm.reason" class="input" maxlength="512" placeholder="取消原因，例如风暴停航后改派" />
        <p v-if="statusForm.status === 'CANCELLED'" class="text-sm text-gray-500">取消后等待整单释放确认。确认前仍保留占用，确认后可用原路单创建新任务。</p>
        <button class="btn btn-primary w-full" :disabled="busy || (statusForm.status === 'CANCELLED' && !statusForm.reason.trim())" @click="saveStatus">提交</button>
      </div>
    </DetailDrawer>
    <DetailDrawer :open="addDrawerOpen" title="添加整张路单" :subtitle="current?.taskCode" @close="addDrawerOpen = false">
      <div class="space-y-3">
        <input v-model="addedManifestNo" class="input" placeholder="同起讫机构、同邮路的已封路单号" />
        <button class="btn btn-primary w-full" :disabled="busy || !addedManifestNo.trim()" @click="saveManifest">确认添加</button>
      </div>
    </DetailDrawer>
    <DetailDrawer :open="historyOpen" title="路单分配历史" :subtitle="current?.taskCode" @close="historyOpen = false">
      <div class="space-y-4">
        <p v-if="!current?.manifestAssignments?.length" class="text-sm text-gray-500">旧任务仅供查阅，请按最新设计重建演示数据。</p>
        <div v-for="item in current?.manifestAssignments || []" :key="item.assignmentNo" class="rounded border p-3 text-sm space-y-1">
          <p>{{ item.manifestNo }} · v{{ item.versionNo }} · {{ labels[item.status] || item.status }}</p>
          <p class="text-gray-500 break-all">{{ item.assignmentNo }}</p>
          <p>申请：{{ lianaTime(item.createdAt) }}</p>
          <p>分配确认：{{ lianaTime(item.confirmedAt) }}</p>
          <p>释放确认：{{ lianaTime(item.releasedAt) }}</p>
          <p>{{ item.status === 'SETTLED' ? '缺袋已审核结算，未虚构收齐时间' : `总包收齐：${lianaTime(item.receivedAt)}` }}</p>
          <p v-if="item.reason">取消原因：{{ item.reason }}</p>
          <p v-if="item.lastError" class="text-red-600">{{ item.lastError }}</p>
        </div>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { transportApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

import { notifyApiError } from '../../lib/apiErrorEvents';

const session = useSessionStore();
const busy = ref(false);
const addDrawerOpen = ref(false);
const historyOpen = ref(false);
const addedManifestNo = ref('');
const labels = { CREATED: '待指派班次', ASSIGNED: '已指派班次', DEPARTED: '已出发', IN_TRANSIT: '运输中', ARRIVED: '已到达', COMPLETED: '已完成', CANCELLING: '取消确认中', CANCELLED: '已取消', PENDING: '分配待确认', CONFIRMED: '分配已确认', RELEASING: '释放待确认', RELEASED: '已释放', RECEIVED: '总包已收齐', SETTLED: '缺袋已结算', CONFLICT: '分配冲突' };
const nextStatuses = computed(() => ({ CREATED: ['CANCELLED'], ASSIGNED: ['DEPARTED', 'CANCELLED'], DEPARTED: ['IN_TRANSIT', 'ARRIVED'], IN_TRANSIT: ['ARRIVED'], ARRIVED: ['COMPLETED'] }[current.value?.status] || []));
function utc(value) { return value && !/Z$|[+-]\\d\\d:\\d\\d$/.test(value) ? value + 'Z' : value; }
function lianaTime(value) {
  return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Etc/GMT-12', dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) + ' UTC+12' : '—';
}
async function mutate(action) {
  if (busy.value) return;
  busy.value = true;
  try { await action(); await load(); } catch (error) { notifyApiError(error); } finally { busy.value = false; }
}
function openAdd(row) { current.value = row; addedManifestNo.value = ''; addDrawerOpen.value = true; }
async function saveManifest() {
  await mutate(async () => { await transportApi.addManifest(current.value.taskCode, addedManifestNo.value.trim(), session.token); addDrawerOpen.value = false; });
}
async function openHistory(row) {
  try { current.value = await transportApi.getTask(row.taskCode, session.token); historyOpen.value = true; } catch (error) { notifyApiError(error); }
}
const rows = ref([]);
const current = ref(null);
const drawerOpen = ref(false);
const dispatchDrawerOpen = ref(false);
const statusDrawerOpen = ref(false);
const assignDrawerOpen = ref(false);
const assignScheduleId = ref('');
const schedules = ref([]);
const editing = ref(false);
const query = reactive({ page: 1, pageSize: 10, keyword: '', status: '' });
const dispatchBagId = ref('');
const statusOptions = ['CREATED', 'ASSIGNED', 'DEPARTED', 'IN_TRANSIT', 'ARRIVED', 'COMPLETED', 'CANCELLING', 'CANCELLED'];
const columns = [
  { key: 'taskCode', label: '任务编码' },
  { key: 'manifestSummary', label: '路单分配' },
  { key: 'bagNos', label: '装载邮袋' },
  { key: 'assetId', label: '资产ID' },
  { key: 'routeId', label: '线路ID' },
  { key: 'scheduleId', label: '计划ID' },
  { key: 'statusLabel', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作' },
];
const form = reactive({ taskCode: '', manifestNo: '', assetId: '', routeId: '', scheduleId: '', status: 'CREATED' });
const statusForm = reactive({ status: 'ASSIGNED', reason: '' });

async function load() {
  const result = await transportApi.listTasks(query, session.token);
  rows.value = (result.list || []).map(row => ({ ...row,
    statusLabel: labels[row.status] || row.status,
    manifestSummary: (row.manifestAssignments || []).map(item => item.manifestNo + ' · ' + (labels[item.status] || item.status)).join('；'),
  }));
}

async function openCreate() {
  const result = await transportApi.listSchedules({ page: 1, pageSize: 200 }, session.token);
  schedules.value = (result.list || []).filter(item => item.status === 'ACTIVE' && new Date(utc(item.departureTime)) > new Date());
  editing.value = false;
  Object.assign(form, { taskCode: '', manifestNo: '', assetId: '', routeId: '', scheduleId: '', status: 'CREATED' });
  drawerOpen.value = true;
}

function openFromBag() {
  dispatchBagId.value = '';
  dispatchDrawerOpen.value = true;
}

function openStatus(row) {
  current.value = row;
  statusForm.status = ({ CREATED: 'CANCELLED', ASSIGNED: 'DEPARTED', DEPARTED: 'IN_TRANSIT', IN_TRANSIT: 'ARRIVED', ARRIVED: 'COMPLETED' })[row.status] || '';
  statusForm.reason = '';
  statusDrawerOpen.value = true;
}
async function openAssign(row) {
  current.value = row;
  assignScheduleId.value = '';
  const result = await transportApi.listSchedules({ page: 1, pageSize: 200 }, session.token);
  schedules.value = (result.list || []).filter(item => item.status === 'ACTIVE' && new Date(utc(item.departureTime)) > new Date());
  assignDrawerOpen.value = true;
}
async function saveAssignment() {
  await mutate(async () => {
    await transportApi.assignTask(current.value.taskCode, Number(assignScheduleId.value), session.token);
    assignDrawerOpen.value = false;
  });
}

async function save() {
  await mutate(async () => {
    await transportApi.createTask({
      taskCode: form.taskCode || undefined,
      manifestNo: form.manifestNo.trim(),
      scheduleId: form.scheduleId === '' ? null : Number(form.scheduleId),
    }, session.token);
    drawerOpen.value = false;
  });
}

async function createFromBag() {
  await mutate(async () => {
    await transportApi.createTaskFromDispatchBag(Number(dispatchBagId.value), session.token);
    dispatchDrawerOpen.value = false;
  });
}

async function saveStatus() {
  await mutate(async () => {
    await transportApi.updateTaskStatus(current.value.taskCode, { status: statusForm.status, reason: statusForm.reason }, session.token);
    statusDrawerOpen.value = false;
  });
}

onMounted(load);
</script>
