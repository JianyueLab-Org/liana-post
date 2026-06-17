<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">运输路线管理</h3>
          <p class="mt-1 text-sm text-gray-500">始发只选机构，目的地可选机构或 OMS 国家代码，后端按字符串保存。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="reloadAll">刷新</button>
      </div>

      <div class="mt-4 grid-three">
        <input v-model="form.routeCode" class="input" placeholder="路线编码" />
        <select v-model="form.originFacilityCode" class="select">
          <option value="">选择始发机构</option>
          <option v-for="item in facilities" :key="item.facilityCode" :value="item.facilityCode">
            {{ item.facilityCode }} - {{ item.name }}
          </option>
        </select>
        <select v-model="form.destinationFacilityCode" class="select">
          <option value="">选择目的地</option>
          <optgroup label="机构">
            <option v-for="item in facilities" :key="`facility-${item.facilityCode}`" :value="item.facilityCode">
              {{ item.facilityCode }} - {{ item.name }}
            </option>
          </optgroup>
          <optgroup label="国家">
            <option v-for="item in countries" :key="item.code" :value="item.code">
              {{ item.code }} - {{ item.name }}
            </option>
          </optgroup>
        </select>
      </div>

      <div class="mt-3 grid-three">
        <input v-model="form.transportMode" class="input" placeholder="运输方式" />
        <input v-model.number="form.priorityLevel" class="input" type="number" placeholder="优先级" />
        <button class="btn btn-primary" :disabled="loading || !canSave" @click="saveRoute">
          {{ editing ? '更新路线' : '新增路线' }}
        </button>
      </div>
      <p class="mt-2 text-xs text-gray-500">当前选择：{{ originDisplay }} -> {{ destinationDisplay }}</p>
    </div>

    <GenericListView :columns="columns" :rows="displayRows" :status-options="statusOptions">
      <template #title>运输路线列表</template>
      <template #subtitle>支持查看与编辑既有始发 / 目的路由</template>
      <template #actions="{ row }">
        <button class="btn btn-secondary" @click="edit(row)">编辑</button>
      </template>
    </GenericListView>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { facilityApi, mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const rows = ref([]);
const facilities = ref([]);
const countries = ref([]);
const editing = ref(false);
const statusOptions = ['1'];

const columns = [
  { key: 'routeCode', label: '路线编码' },
  { key: 'originDisplay', label: '始发机构' },
  { key: 'destinationDisplay', label: '目的地' },
  { key: 'transportMode', label: '运输方式' },
  { key: 'priorityLevel', label: '优先级' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' },
];

const form = reactive({
  routeCode: '',
  originFacilityCode: '',
  destinationFacilityCode: '',
  transportMode: 'LAND',
  priorityLevel: 0,
});

const displayRows = computed(() => rows.value.map((row) => ({
  ...row,
  originDisplay: formatFacility(row.originFacilityCode),
  destinationDisplay: formatDestination(row.destinationFacilityCode),
})));

const canSave = computed(() => Boolean(form.routeCode && form.originFacilityCode && form.destinationFacilityCode && form.transportMode));
const originDisplay = computed(() => formatFacility(form.originFacilityCode));
const destinationDisplay = computed(() => formatDestination(form.destinationFacilityCode));

function formatFacility(code) {
  const item = facilities.value.find((entry) => entry.facilityCode === code);
  return item ? `${item.facilityCode} - ${item.name}` : '';
}

function formatCountry(code) {
  const item = countries.value.find((entry) => entry.code === code);
  return item ? `${item.code} - ${item.name}` : '';
}

function formatDestination(code) {
  return formatCountry(code) || formatFacility(code) || (code || '-');
}

async function reloadAll() {
  loading.value = true;
  try {
    const [facilityList, routeList, countryList] = await Promise.all([
      facilityApi.listFacilities(session.token),
      facilityApi.listRoutes(session.token),
      mailApi.listCountries(session.token),
    ]);
    facilities.value = facilityList || [];
    rows.value = routeList || [];
    countries.value = countryList || [];
  } finally {
    loading.value = false;
  }
}

function edit(row) {
  editing.value = true;
  form.routeCode = row.routeCode || '';
  form.originFacilityCode = row.originFacilityCode || '';
  form.destinationFacilityCode = row.destinationFacilityCode || '';
  form.transportMode = row.transportMode || 'LAND';
  form.priorityLevel = row.priorityLevel ?? 0;
}

async function saveRoute() {
  if (!canSave.value) return;
  loading.value = true;
  try {
    const payload = {
      routeCode: form.routeCode.trim(),
      originFacilityCode: form.originFacilityCode.trim(),
      destinationFacilityCode: form.destinationFacilityCode.trim().toUpperCase(),
      transportMode: form.transportMode.trim(),
      priorityLevel: form.priorityLevel === '' ? null : Number(form.priorityLevel),
    };
    if (editing.value) {
      await facilityApi.updateRoute(form.routeCode, payload, session.token);
    } else {
      await facilityApi.createRoute(payload, session.token);
    }
    editing.value = false;
    Object.assign(form, {
      routeCode: '',
      originFacilityCode: '',
      destinationFacilityCode: '',
      transportMode: 'LAND',
      priorityLevel: 0,
    });
    await reloadAll();
  } finally {
    loading.value = false;
  }
}

onMounted(reloadAll);
</script>
