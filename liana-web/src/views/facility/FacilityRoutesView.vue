<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">封发关系管理</h3>
          <p class="mt-1 text-sm text-gray-500">始发机构和目的机构都支持直接输入机构编码或名称，下方会显示匹配候选项。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="loadRoutes">刷新</button>
        </div>
      </div>

      <div class="mt-4 grid-three">
        <input v-model="form.routeCode" class="input" placeholder="路线编码" />
        <div class="relative">
          <input
            v-model="form.originInput"
            class="input"
            placeholder="始发机构（可输入编码或名称）"
            @focus="originFocused = true"
            @blur="onOriginBlur"
          />
          <div v-if="showOriginSuggestions" class="suggest-box">
            <button
              v-for="item in originSuggestions"
              :key="item.facilityCode"
              type="button"
              class="suggest-item"
              @mousedown.prevent="selectOrigin(item)"
            >
              {{ item.facilityCode }} · {{ item.name }}
            </button>
          </div>
        </div>
        <div class="relative">
          <input
            v-model="form.destinationInput"
            class="input"
            placeholder="目的机构（可输入编码或名称）"
            @focus="destinationFocused = true"
            @blur="onDestinationBlur"
          />
          <div v-if="showDestinationSuggestions" class="suggest-box">
            <button
              v-for="item in destinationSuggestions"
              :key="item.facilityCode"
              type="button"
              class="suggest-item"
              @mousedown.prevent="selectDestination(item)"
            >
              {{ item.facilityCode }} · {{ item.name }}
            </button>
          </div>
        </div>
      </div>
      <div class="mt-3 grid-three">
        <input v-model="form.transportMode" class="input" placeholder="运输方式" />
        <input v-model.number="form.priorityLevel" class="input" type="number" placeholder="优先级" />
        <button class="btn btn-primary" :disabled="loading || !canSave" @click="saveRoute">{{ editing ? '更新封发关系' : '新增封发关系' }}</button>
      </div>
      <p class="mt-2 text-xs text-gray-500">当前已选：{{ originDisplay }} → {{ destinationDisplay }}</p>
    </div>

    <GenericListView :columns="columns" :rows="rows" :status-options="statusOptions">
      <template #title>封发关系列表</template>
      <template #subtitle>支持查看与编辑已有始发/目的关系</template>
      <template #actions="{ row }">
        <button class="btn btn-secondary" @click="edit(row)">编辑</button>
      </template>
    </GenericListView>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { facilityApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const rows = ref([]);
const facilities = ref([]);
const editing = ref(false);
const originFocused = ref(false);
const destinationFocused = ref(false);
const statusOptions = ['1'];
const columns = [
  { key: 'routeCode', label: '路线编码' },
  { key: 'originFacilityCode', label: '始发机构' },
  { key: 'destinationFacilityCode', label: '目的机构' },
  { key: 'transportMode', label: '运输方式' },
  { key: 'priorityLevel', label: '优先级' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' },
];
const form = reactive({
  routeCode: '',
  originInput: '',
  destinationInput: '',
  originFacilityCode: '',
  destinationFacilityCode: '',
  transportMode: 'LAND',
  priorityLevel: 0,
});

const canSave = computed(() => Boolean(form.routeCode && resolveFacilityCode(form.originInput, form.originFacilityCode) && resolveFacilityCode(form.destinationInput, form.destinationFacilityCode) && form.transportMode));
const originSuggestions = computed(() => searchFacilities(form.originInput));
const destinationSuggestions = computed(() => searchFacilities(form.destinationInput));
const showOriginSuggestions = computed(() => originFocused.value && Boolean(form.originInput) && originSuggestions.value.length > 0);
const showDestinationSuggestions = computed(() => destinationFocused.value && Boolean(form.destinationInput) && destinationSuggestions.value.length > 0);
const originDisplay = computed(() => displayFacility(form.originInput, form.originFacilityCode));
const destinationDisplay = computed(() => displayFacility(form.destinationInput, form.destinationFacilityCode));

function facilityLabel(item) {
  return `${item.facilityCode} · ${item.name}`;
}

function displayFacility(input, code) {
  if (code) {
    const matched = facilities.value.find((item) => item.facilityCode === code);
    if (matched) return facilityLabel(matched);
  }
  return input || '-';
}

function searchFacilities(keyword) {
  const value = (keyword || '').trim().toLowerCase();
  if (!value) return [];
  return facilities.value
    .filter((item) => item.facilityCode.toLowerCase().includes(value) || item.name.toLowerCase().includes(value))
    .slice(0, 8);
}

function resolveFacilityCode(input, selectedCode) {
  if (selectedCode) {
    return selectedCode;
  }
  const value = (input || '').trim();
  if (!value) return '';
  const exact = facilities.value.find((item) => item.facilityCode === value || item.name === value || facilityLabel(item) === value);
  if (exact) return exact.facilityCode;
  const matches = searchFacilities(value);
  return matches.length === 1 ? matches[0].facilityCode : '';
}

function selectOrigin(item) {
  form.originInput = facilityLabel(item);
  form.originFacilityCode = item.facilityCode;
  originFocused.value = false;
}

function selectDestination(item) {
  form.destinationInput = facilityLabel(item);
  form.destinationFacilityCode = item.facilityCode;
  destinationFocused.value = false;
}

function onOriginBlur() {
  window.setTimeout(() => {
    originFocused.value = false;
    form.originFacilityCode = resolveFacilityCode(form.originInput, form.originFacilityCode);
  }, 120);
}

function onDestinationBlur() {
  window.setTimeout(() => {
    destinationFocused.value = false;
    form.destinationFacilityCode = resolveFacilityCode(form.destinationInput, form.destinationFacilityCode);
  }, 120);
}

async function loadFacilities() {
  facilities.value = await facilityApi.listFacilities(session.token);
}

async function loadRoutes() {
  loading.value = true;
  try {
    rows.value = await facilityApi.listRoutes(session.token);
  } finally {
    loading.value = false;
  }
}

function edit(row) {
  editing.value = true;
  form.routeCode = row.routeCode || '';
  form.originFacilityCode = row.originFacilityCode || '';
  form.destinationFacilityCode = row.destinationFacilityCode || '';
  form.originInput = displayFacility('', row.originFacilityCode);
  form.destinationInput = displayFacility('', row.destinationFacilityCode);
  form.transportMode = row.transportMode || 'LAND';
  form.priorityLevel = row.priorityLevel ?? 0;
}

async function saveRoute() {
  const originFacilityCode = resolveFacilityCode(form.originInput, form.originFacilityCode);
  const destinationFacilityCode = resolveFacilityCode(form.destinationInput, form.destinationFacilityCode);
  if (!form.routeCode || !originFacilityCode || !destinationFacilityCode || !form.transportMode) {
    return;
  }

  loading.value = true;
  try {
    const payload = {
      routeCode: form.routeCode,
      originFacilityCode,
      destinationFacilityCode,
      transportMode: form.transportMode,
      priorityLevel: form.priorityLevel,
    };
    if (editing.value) {
      await facilityApi.updateRoute(form.routeCode, payload, session.token);
    } else {
      await facilityApi.createRoute(payload, session.token);
    }
    editing.value = false;
    form.routeCode = '';
    form.originInput = '';
    form.destinationInput = '';
    form.originFacilityCode = '';
    form.destinationFacilityCode = '';
    form.transportMode = 'LAND';
    form.priorityLevel = 0;
    await loadRoutes();
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadFacilities(), loadRoutes()]);
});
</script>

<style scoped>
.suggest-box {
  position: absolute;
  z-index: 20;
  margin-top: 0.5rem;
  width: 100%;
  overflow: hidden;
  border-radius: 0.75rem;
  border: 1px solid rgb(226 232 240);
  background: white;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
}

.suggest-item {
  width: 100%;
  text-align: left;
  padding: 0.75rem 1rem;
  font-size: 0.875rem;
  color: rgb(31 41 55);
}

.suggest-item:hover {
  background: rgb(241 245 249);
}
</style>