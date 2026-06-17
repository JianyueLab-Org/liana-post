<template>
  <div class="space-y-6">
    <div class="card p-5 hero-panel">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">机构管理</h3>
          <p class="mt-1 text-sm text-gray-500">按机构类型、机构、机构路线三张表分别录入，直接对接现有后端表结构。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="reloadAll">刷新</button>
      </div>
    </div>

    <div class="grid-two">
      <div class="card p-5">
        <h4 class="text-base font-semibold text-gray-900">机构类型</h4>
        <div class="mt-4 grid gap-3">
          <input v-model="typeForm.code" class="input" placeholder="类型编码" />
          <input v-model="typeForm.name" class="input" placeholder="类型名称" />
          <input v-model="typeForm.description" class="input" placeholder="描述" />
          <button class="btn btn-primary" :disabled="loading || !canCreateType" @click="createType">新增机构类型</button>
        </div>
      </div>

      <div class="card p-5">
        <h4 class="text-base font-semibold text-gray-900">机构</h4>
        <div class="mt-4 grid gap-3">
          <input v-model="facilityForm.facilityCode" class="input" placeholder="机构编码" />
          <input v-model="facilityForm.name" class="input" placeholder="机构名称" />
          <select v-model="facilityForm.typeCode" class="select">
            <option value="">选择机构类型</option>
            <option v-for="item in facilityTypes" :key="item.code" :value="item.code">{{ item.code }} - {{ item.name }}</option>
          </select>
          <select v-model="facilityForm.parentFacilityCode" class="select">
            <option value="">无上级机构</option>
            <option v-for="item in facilities" :key="item.facilityCode" :value="item.facilityCode">{{ item.facilityCode }} - {{ item.name }}</option>
          </select>
          <input v-model="facilityForm.countryCode" class="input" placeholder="国家代码" />
          <input v-model="facilityForm.province" class="input" placeholder="省/州" />
          <input v-model="facilityForm.city" class="input" placeholder="城市" />
          <input v-model="facilityForm.address" class="input" placeholder="地址" />
          <div class="grid grid-cols-2 gap-3">
            <input v-model="facilityForm.latitude" class="input" type="number" step="any" placeholder="纬度" />
            <input v-model="facilityForm.longitude" class="input" type="number" step="any" placeholder="经度" />
          </div>
          <button class="btn btn-primary" :disabled="loading || !canCreateFacility" @click="createFacility">新增机构</button>
        </div>
      </div>
    </div>

    <div class="card p-5">
      <h4 class="text-base font-semibold text-gray-900">机构路线</h4>
      <div class="mt-4 grid-three">
        <input v-model="routeForm.routeCode" class="input" placeholder="路线编码" />
        <select v-model="routeForm.originFacilityCode" class="select">
          <option value="">始发机构</option>
          <option v-for="item in facilities" :key="item.facilityCode" :value="item.facilityCode">{{ item.facilityCode }} - {{ item.name }}</option>
        </select>
        <select v-model="routeForm.destinationFacilityCode" class="select">
          <option value="">目的地</option>
          <optgroup label="机构">
            <option v-for="item in facilities" :key="`facility-${item.facilityCode}`" :value="item.facilityCode">{{ item.facilityCode }} - {{ item.name }}</option>
          </optgroup>
          <optgroup label="国家">
            <option v-for="item in countries" :key="item.code" :value="item.code">{{ item.code }} - {{ item.name }}</option>
          </optgroup>
        </select>
      </div>
      <div class="mt-3 grid-three">
        <input v-model="routeForm.transportMode" class="input" placeholder="运输方式" />
        <input v-model.number="routeForm.distanceKm" class="input" type="number" step="any" placeholder="距离(km)" />
        <input v-model.number="routeForm.estimatedHours" class="input" type="number" step="any" placeholder="预计小时" />
      </div>
      <div class="mt-3 grid-three">
        <input v-model.number="routeForm.priorityLevel" class="input" type="number" placeholder="优先级" />
        <div></div>
        <button class="btn btn-primary" :disabled="loading || !canCreateRoute" @click="createRoute">新增路线</button>
      </div>
    </div>

    <div class="grid-two">
      <GenericListView :columns="typeColumns" :rows="facilityTypes">
        <template #title>机构类型列表</template>
      </GenericListView>
      <GenericListView :columns="facilityColumns" :rows="facilities">
        <template #title>机构列表</template>
      </GenericListView>
    </div>

    <GenericListView :columns="routeColumns" :rows="routes">
      <template #title>机构路线列表</template>
    </GenericListView>

    <div class="card p-5">
      <h4 class="text-base font-semibold text-gray-900">提交结果</h4>
      <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { facilityApi, mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const result = ref('');
const facilityTypes = ref([]);
const facilities = ref([]);
const routes = ref([]);
const countries = ref([]);

const typeForm = reactive({ code: '', name: '', description: '' });
const facilityForm = reactive({
  facilityCode: '',
  name: '',
  typeCode: '',
  parentFacilityCode: '',
  countryCode: 'LN',
  province: '',
  city: '',
  address: '',
  latitude: '',
  longitude: '',
});
const routeForm = reactive({
  routeCode: '',
  originFacilityCode: '',
  destinationFacilityCode: '',
  transportMode: 'LAND',
  distanceKm: '',
  estimatedHours: '',
  priorityLevel: 0,
});

const typeColumns = [
  { key: 'code', label: '编码' },
  { key: 'name', label: '名称' },
  { key: 'description', label: '描述' },
];
const facilityColumns = [
  { key: 'facilityCode', label: '编码' },
  { key: 'name', label: '名称' },
  { key: 'typeCode', label: '类型' },
  { key: 'parentFacilityCode', label: '上级' },
  { key: 'countryCode', label: '国家' },
  { key: 'status', label: '状态' },
];
const routeColumns = [
  { key: 'routeCode', label: '编码' },
  { key: 'originFacilityCode', label: '始发' },
  { key: 'destinationFacilityCode', label: '目的' },
  { key: 'transportMode', label: '运输方式' },
  { key: 'priorityLevel', label: '优先级' },
  { key: 'status', label: '状态' },
];

const canCreateType = computed(() => Boolean(typeForm.code && typeForm.name));
const canCreateFacility = computed(() => Boolean(facilityForm.facilityCode && facilityForm.name && facilityForm.typeCode));
const canCreateRoute = computed(() => Boolean(routeForm.routeCode && routeForm.originFacilityCode && routeForm.destinationFacilityCode && routeForm.transportMode));

async function reloadAll() {
  loading.value = true;
  try {
    const [types, allFacilities, allRoutes, allCountries] = await Promise.all([
      facilityApi.listTypes(session.token),
      facilityApi.listFacilities(session.token),
      facilityApi.listRoutes(session.token),
      mailApi.listCountries(session.token),
    ]);
    facilityTypes.value = types || [];
    facilities.value = allFacilities || [];
    routes.value = allRoutes || [];
    countries.value = allCountries || [];
  } finally {
    loading.value = false;
  }
}

async function createType() {
  loading.value = true;
  try {
    await facilityApi.createFacilityType({
      code: typeForm.code.trim(),
      name: typeForm.name.trim(),
      description: typeForm.description.trim(),
    }, session.token);
    typeForm.code = '';
    typeForm.name = '';
    typeForm.description = '';
    await reloadAll();
    result.value = '机构类型已创建';
  } finally {
    loading.value = false;
  }
}

async function createFacility() {
  loading.value = true;
  try {
    await facilityApi.createFacility({
      facilityCode: facilityForm.facilityCode.trim(),
      name: facilityForm.name.trim(),
      typeCode: facilityForm.typeCode.trim(),
      parentFacilityCode: facilityForm.parentFacilityCode || null,
      countryCode: facilityForm.countryCode.trim(),
      province: facilityForm.province.trim(),
      city: facilityForm.city.trim(),
      address: facilityForm.address.trim(),
      latitude: facilityForm.latitude === '' ? null : Number(facilityForm.latitude),
      longitude: facilityForm.longitude === '' ? null : Number(facilityForm.longitude),
    }, session.token);
    Object.assign(facilityForm, {
      facilityCode: '',
      name: '',
      typeCode: '',
      parentFacilityCode: '',
      countryCode: 'LN',
      province: '',
      city: '',
      address: '',
      latitude: '',
      longitude: '',
    });
    await reloadAll();
    result.value = '机构已创建';
  } finally {
    loading.value = false;
  }
}

async function createRoute() {
  loading.value = true;
  try {
    await facilityApi.createRoute({
      routeCode: routeForm.routeCode.trim(),
      originFacilityCode: routeForm.originFacilityCode.trim(),
      destinationFacilityCode: routeForm.destinationFacilityCode.trim(),
      transportMode: routeForm.transportMode.trim(),
      distanceKm: routeForm.distanceKm === '' ? null : Number(routeForm.distanceKm),
      estimatedHours: routeForm.estimatedHours === '' ? null : Number(routeForm.estimatedHours),
      priorityLevel: routeForm.priorityLevel === '' ? null : Number(routeForm.priorityLevel),
    }, session.token);
    Object.assign(routeForm, {
      routeCode: '',
      originFacilityCode: '',
      destinationFacilityCode: '',
      transportMode: 'LAND',
      distanceKm: '',
      estimatedHours: '',
      priorityLevel: 0,
    });
    await reloadAll();
    result.value = '机构路线已创建';
  } finally {
    loading.value = false;
  }
}

onMounted(reloadAll);
</script>
