<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">同步总览</h3>
          <p class="mt-1 text-sm text-gray-500">Outbox、同步任务与重试记录的运行状态。</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
          <button class="btn btn-secondary" :disabled="loading" @click="createSuccessDemo">成功样例</button>
          <button class="btn btn-secondary" :disabled="loading" @click="createFailOnceDemo">失败重试样例</button>
          <button class="btn btn-secondary" :disabled="loading" @click="scanOutbox">扫描 Outbox</button>
          <button class="btn btn-primary" :disabled="loading" @click="retryTasks">重试任务</button>
        </div>
      </div>
    </div>

    <div v-if="error" class="card border-red-200 bg-red-50 p-5 text-sm text-red-700">
      {{ error }}
    </div>

    <div class="grid-three">
      <div v-for="item in section.metrics" :key="item.label" class="card p-5">
        <div class="flex items-center justify-between gap-3">
          <p class="text-sm text-gray-500">{{ item.label }}</p>
          <span :class="['badge', toneClass(item.tone)]">{{ toneLabel(item.tone) }}</span>
        </div>
        <p class="mt-2 text-3xl font-bold text-gray-900">{{ item.value }}</p>
        <p class="mt-1 truncate text-xs text-gray-500">{{ item.hint }}</p>
      </div>
    </div>

    <div class="grid-two">
      <div v-for="group in section.breakdowns" :key="group.title" class="card p-5">
        <h4 class="card-title">{{ group.title }}</h4>
        <div class="mt-4 space-y-3">
          <div v-for="item in group.items" :key="`${group.title}-${item.label}`" class="flex items-center justify-between gap-4 rounded-lg bg-slate-50 px-4 py-3">
            <span class="text-sm font-medium text-gray-800">{{ item.label }}</span>
            <span class="text-lg font-semibold text-gray-900">{{ item.value }}</span>
          </div>
          <p v-if="!group.items.length" class="text-sm text-gray-500">暂无数据</p>
        </div>
      </div>
    </div>

    <div class="grid-two">
      <div class="card p-5">
        <h4 class="card-title">最近同步任务</h4>
        <div class="mt-4 overflow-hidden rounded-lg border border-gray-200">
          <table class="table">
            <thead>
              <tr>
                <th>任务</th>
                <th>链路</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in normalized.recentTasks" :key="task.id || task.taskNo">
                <td class="font-medium text-gray-900">{{ task.taskNo }}</td>
                <td>{{ task.sourceService }} -> {{ task.targetService }}</td>
                <td><span :class="['badge', statusClass(task.status)]">{{ task.status }}</span></td>
              </tr>
              <tr v-if="!normalized.recentTasks.length">
                <td colspan="3" class="text-sm text-gray-500">暂无任务</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card p-5">
        <h4 class="card-title">最近失败</h4>
        <div class="mt-4 space-y-3">
          <div v-for="task in normalized.recentFailures" :key="task.id || task.taskNo" class="rounded-lg bg-red-50 px-4 py-3">
            <div class="flex items-center justify-between gap-4">
              <p class="min-w-0 truncate text-sm font-medium text-red-900">{{ task.taskNo }}</p>
              <span class="badge badge-danger">{{ task.retryCount || 0 }}/{{ task.maxRetries || 0 }}</span>
            </div>
            <p class="mt-1 text-xs text-red-700">{{ task.lastError || '无错误信息' }}</p>
          </div>
          <p v-if="!normalized.recentFailures.length" class="rounded-lg bg-slate-50 px-4 py-8 text-center text-sm text-gray-500">
            暂无失败任务
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { syncApi } from '../../lib/api';
import { normalizeSyncerSummary, syncerDashboardSection } from '../../lib/syncerDashboard';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const error = ref('');
const rawSummary = ref({});

const normalized = computed(() => normalizeSyncerSummary(rawSummary.value));
const section = computed(() => syncerDashboardSection(rawSummary.value));

async function load() {
  loading.value = true;
  error.value = '';
  try {
    rawSummary.value = await syncApi.summary(session.token);
  } catch (err) {
    error.value = err.message || '同步总览加载失败';
  } finally {
    loading.value = false;
  }
}

async function scanOutbox() {
  loading.value = true;
  try {
    await syncApi.scanOutbox(session.token);
    await load();
  } finally {
    loading.value = false;
  }
}

async function retryTasks() {
  loading.value = true;
  try {
    await syncApi.retryTasks(session.token);
    await load();
  } finally {
    loading.value = false;
  }
}

async function createSuccessDemo() {
  loading.value = true;
  try {
    await syncApi.createSuccessDemo(session.token);
    await load();
  } finally {
    loading.value = false;
  }
}

async function createFailOnceDemo() {
  loading.value = true;
  try {
    await syncApi.createFailOnceDemo(session.token);
    await load();
  } finally {
    loading.value = false;
  }
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

function statusClass(status) {
  if (status === 'SUCCESS') return 'badge-success';
  if (status === 'FAILED') return 'badge-danger';
  if (status === 'PENDING' || status === 'NEW') return 'badge-warning';
  if (status === 'PROCESSING') return 'badge-info';
  return 'badge-neutral';
}

onMounted(load);
</script>
