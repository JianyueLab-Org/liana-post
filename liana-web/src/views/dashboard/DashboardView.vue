<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="card-title">数据工作台</h3>
          <p class="mt-1 text-sm text-gray-500">按角色权限展示业务数据面板</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="loadDashboard">
          {{ loading ? '加载中...' : '刷新' }}
        </button>
      </div>
    </div>

    <div v-if="error" class="card border-red-200 bg-red-50 p-5 text-sm text-red-700">
      {{ error }}
    </div>

    <div v-if="loading && !sections.length" class="grid-three">
      <div v-for="index in 3" :key="index" class="card p-5">
        <div class="h-4 w-24 rounded bg-gray-200"></div>
        <div class="mt-4 h-8 w-16 rounded bg-gray-200"></div>
        <div class="mt-3 h-3 w-32 rounded bg-gray-100"></div>
      </div>
    </div>

    <section v-for="section in sections" :key="section.title" class="space-y-4">
      <div class="flex items-end justify-between gap-3">
        <div>
          <h3 class="card-title">{{ section.title }}</h3>
          <p class="mt-1 text-xs text-gray-500">{{ section.scope }}</p>
        </div>
      </div>

      <div class="grid-three">
        <div v-for="item in section.metrics" :key="`${section.title}-${item.label}`" class="card p-5">
          <div class="flex items-center justify-between gap-3">
            <p class="text-sm text-gray-500">{{ item.label }}</p>
            <span :class="['badge', toneClass(item.tone)]">{{ toneLabel(item.tone) }}</span>
          </div>
          <p class="mt-2 text-3xl font-bold text-gray-900">{{ item.value }}</p>
          <p class="mt-1 text-xs text-gray-500">{{ item.hint }}</p>
        </div>
      </div>

      <div class="grid-two">
        <div v-for="group in section.breakdowns" :key="`${section.title}-${group.title}`" class="card p-5">
          <h4 class="card-title">{{ group.title }}</h4>
          <div class="mt-4 space-y-3">
            <div v-for="item in group.items" :key="`${group.title}-${item.label}`" class="flex items-center justify-between gap-4 rounded-lg bg-slate-50 px-4 py-3">
              <div class="min-w-0">
                <p class="truncate text-sm font-medium text-gray-800">{{ item.label }}</p>
                <p v-if="item.status" class="mt-0.5 truncate text-xs text-gray-500">{{ item.status }}</p>
              </div>
              <p class="text-lg font-semibold text-gray-900">{{ item.value }}</p>
            </div>
            <p v-if="!group.items?.length" class="text-sm text-gray-500">暂无数据</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="!loading && !sections.length && !error" class="card p-5 text-sm text-gray-500">
      当前角色暂无可展示的数据面板。
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { authApi, dispatchApi, facilityApi, mailApi, sortingApi, transportApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const sections = ref([]);
const loading = ref(false);
const error = ref('');

const role = computed(() => (session.user?.role || '').toUpperCase());
const facilityCode = computed(() => session.user?.facilityCode || '');
const ROLE_LOADERS = {
  ADMIN: [
    () => authApi.dashboardSummary(session.token),
    () => mailApi.dashboardSummary(facilityCode.value, session.token),
    () => facilityApi.dashboardSummary(session.token),
    () => transportApi.dashboardSummary(session.token),
  ],
  MANAGER: [
    () => mailApi.dashboardSummary(facilityCode.value, session.token),
    () => dispatchApi.dashboardSummary(facilityCode.value, session.token),
    () => facilityApi.dashboardSummary(session.token),
  ],
  SORTER: [
    () => mailApi.dashboardSummary(facilityCode.value, session.token),
    () => sortingApi.dashboardSummary(facilityCode.value, session.token),
    () => dispatchApi.dashboardSummary(facilityCode.value, session.token),
  ],
  CLERK: [
    () => mailApi.dashboardSummary(facilityCode.value, session.token),
    () => dispatchApi.dashboardSummary(facilityCode.value, session.token),
  ],
};

function loadersForRole() {
  return ROLE_LOADERS[role.value] || [
    () => mailApi.dashboardSummary(facilityCode.value, session.token),
  ];
}

async function loadDashboard() {
  loading.value = true;
  error.value = '';
  try {
    const results = await Promise.allSettled(loadersForRole().map((loader) => loader()));
    sections.value = results
      .filter((result) => result.status === 'fulfilled' && result.value)
      .map((result) => normalizeSection(result.value));
    const failed = results.filter((result) => result.status === 'rejected');
    if (failed.length && !sections.value.length) {
      error.value = failed[0].reason?.message || '数据加载失败';
    }
  } finally {
    loading.value = false;
  }
}

function normalizeSection(section) {
  return {
    title: section.title || '数据面板',
    scope: section.scope || '全部',
    metrics: Array.isArray(section.metrics) ? section.metrics : [],
    breakdowns: Array.isArray(section.breakdowns) ? section.breakdowns : [],
  };
}

function toneClass(tone) {
  if (tone === 'success') return 'badge-success';
  if (tone === 'warning') return 'badge-warning';
  if (tone === 'danger') return 'badge-danger';
  if (tone === 'info') return 'badge-info';
  return 'badge-neutral';
}

function toneLabel(tone) {
  if (tone === 'success') return '正常';
  if (tone === 'warning') return '关注';
  if (tone === 'danger') return '异常';
  if (tone === 'info') return '总览';
  return '统计';
}

onMounted(loadDashboard);
</script>
