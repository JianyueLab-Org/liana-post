<template>
  <div class="space-y-6">
    <section class="card p-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p class="text-xs font-bold text-cyan-700">Dashboard</p>
          <h3 class="mt-1 text-2xl font-black text-slate-950">数据工作台</h3>
          <p class="mt-2 text-sm text-slate-500">按当前角色与机构汇总业务运行数据。</p>
        </div>
        <button class="btn btn-secondary" :disabled="loading" @click="loadDashboard">
          {{ loading ? '加载中...' : '刷新数据' }}
        </button>
      </div>
    </section>

    <div v-if="error" class="card border-red-200 bg-red-50 p-5 text-sm font-semibold text-red-700">
      {{ error }}
    </div>

    <div v-if="loading && !sections.length" class="grid gap-4 lg:grid-cols-4">
      <div class="card p-5 lg:col-span-2 lg:row-span-2">
        <div class="h-4 w-28 rounded bg-cyan-100"></div>
        <div class="mt-6 h-12 w-24 rounded bg-cyan-200"></div>
        <div class="mt-4 h-3 w-48 rounded bg-cyan-50"></div>
      </div>
      <div v-for="index in 4" :key="index" class="card p-5">
        <div class="h-4 w-24 rounded bg-cyan-100"></div>
        <div class="mt-4 h-8 w-16 rounded bg-cyan-200"></div>
        <div class="mt-3 h-3 w-32 rounded bg-cyan-50"></div>
      </div>
    </div>

    <section v-for="section in sections" :key="section.title" class="card p-6">
      <div class="flex flex-wrap items-end justify-between gap-3 border-b border-cyan-100 pb-4">
        <div>
          <h3 class="text-lg font-black text-slate-950">{{ section.title }}</h3>
          <p class="mt-1 text-sm text-slate-500">{{ section.scope }}</p>
        </div>
        <span class="badge badge-info">{{ section.metrics.length }} 项指标</span>
      </div>

      <div class="mt-5 grid gap-4 xl:grid-cols-4">
        <article
          v-if="section.metrics[0]"
          class="relative overflow-hidden rounded-xl border border-cyan-200 bg-gradient-to-br from-cyan-600 via-teal-500 to-emerald-500 p-6 text-white shadow-lg shadow-cyan-900/15 xl:col-span-2 xl:row-span-2"
        >
          <div class="absolute -right-12 -top-12 h-36 w-36 rounded-full bg-white/15"></div>
          <div class="absolute bottom-4 right-5 h-20 w-20 rounded-full border border-white/25"></div>
          <div class="relative">
            <div class="flex items-center justify-between gap-3">
              <p class="text-sm font-semibold text-cyan-50">{{ section.metrics[0].label }}</p>
              <span class="rounded-full bg-white/18 px-3 py-1 text-xs font-bold text-white">{{ toneLabel(section.metrics[0].tone) }}</span>
            </div>
            <p class="mt-5 text-5xl font-black tracking-normal">{{ section.metrics[0].value }}</p>
            <p class="mt-3 max-w-sm text-sm leading-6 text-cyan-50">{{ section.metrics[0].hint || '核心业务指标' }}</p>
          </div>
        </article>

        <article
          v-for="item in section.metrics.slice(1)"
          :key="`${section.title}-${item.label}`"
          class="rounded-xl border border-cyan-100 bg-white/78 p-5 shadow-sm shadow-cyan-900/5 transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-cyan-900/10"
        >
          <div class="flex items-start justify-between gap-3">
            <p class="text-sm font-semibold text-slate-500">{{ item.label }}</p>
            <span :class="['badge', toneClass(item.tone)]">{{ toneLabel(item.tone) }}</span>
          </div>
          <p class="mt-4 text-3xl font-black text-slate-950">{{ item.value }}</p>
          <p class="mt-2 line-clamp-2 text-xs leading-5 text-slate-500">{{ item.hint || '-' }}</p>
        </article>
      </div>

      <div v-if="section.breakdowns.length" class="mt-5 grid gap-4 xl:grid-cols-2">
        <article
          v-for="group in section.breakdowns"
          :key="`${section.title}-${group.title}`"
          class="rounded-xl border border-cyan-100 bg-white/72 p-5 shadow-sm shadow-cyan-900/5"
        >
          <div class="flex items-center justify-between gap-3">
            <h4 class="font-black text-slate-900">{{ group.title }}</h4>
            <span class="text-xs font-semibold text-slate-400">{{ group.items?.length || 0 }} 条</span>
          </div>
          <div class="mt-4 grid gap-3 sm:grid-cols-2">
            <div
              v-for="item in group.items"
              :key="`${group.title}-${item.label}`"
              class="rounded-lg border border-cyan-50 bg-cyan-50/45 px-4 py-3"
            >
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-sm font-bold text-slate-800">{{ item.label }}</p>
                  <p v-if="item.status" class="mt-1 truncate text-xs text-slate-500">{{ item.status }}</p>
                </div>
                <p class="shrink-0 text-lg font-black text-cyan-800">{{ item.value }}</p>
              </div>
            </div>
            <p v-if="!group.items?.length" class="text-sm text-slate-500">暂无数据</p>
          </div>
        </article>
      </div>
    </section>

    <div v-if="!loading && !sections.length && !error" class="card p-6 text-sm text-slate-500">
      当前角色暂无可展示的数据面板。
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { authApi, dispatchApi, facilityApi, mailApi, sortingApi, syncApi, transportApi } from '../../lib/api';
import { sentinelDemoSummary } from '../../lib/sentinelDemo';
import { syncerDashboardSection } from '../../lib/syncerDashboard';
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
    () => syncApi.summary(session.token).then(syncerDashboardSection),
    () => Promise.resolve(sentinelDemoSummary()),
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
