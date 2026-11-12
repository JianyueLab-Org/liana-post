<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="card-title">Sentinel 监控</h3>
          <p class="mt-1 text-sm text-gray-500">Gateway 限流、Feign 降级与 Dashboard 接入状态</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <a class="btn btn-secondary" :href="config.dashboardUrl" target="_blank" rel="noreferrer">Dashboard</a>
          <button class="btn btn-primary" :disabled="probing" @click="runProbe">
            {{ probing ? '探测中...' : '限流探测' }}
          </button>
        </div>
      </div>
    </div>

    <div class="grid-three">
      <div v-for="item in summary.metrics" :key="item.label" class="card p-5">
        <div class="flex items-center justify-between gap-3">
          <p class="text-sm text-gray-500">{{ item.label }}</p>
          <span :class="['badge', toneClass(item.tone)]">{{ toneLabel(item.tone) }}</span>
        </div>
        <p class="mt-2 text-3xl font-bold text-gray-900">{{ item.value }}</p>
        <p class="mt-1 text-xs text-gray-500">{{ item.hint }}</p>
      </div>
    </div>

    <div class="grid-two">
      <div class="card p-5">
        <h4 class="card-title">Gateway 限流规则</h4>
        <div class="mt-4 space-y-3">
          <div class="flex items-center justify-between gap-4 rounded-lg bg-slate-50 px-4 py-3">
            <span class="text-sm text-gray-500">Route ID</span>
            <span class="font-semibold text-gray-900">{{ config.rule.routeId }}</span>
          </div>
          <div class="flex items-center justify-between gap-4 rounded-lg bg-slate-50 px-4 py-3">
            <span class="text-sm text-gray-500">Path</span>
            <span class="font-semibold text-gray-900">{{ config.rule.path }}</span>
          </div>
          <div class="flex items-center justify-between gap-4 rounded-lg bg-slate-50 px-4 py-3">
            <span class="text-sm text-gray-500">QPS</span>
            <span class="font-semibold text-gray-900">{{ config.rule.qps }}</span>
          </div>
          <div class="flex items-center justify-between gap-4 rounded-lg bg-slate-50 px-4 py-3">
            <span class="text-sm text-gray-500">限流响应</span>
            <span class="font-semibold text-gray-900">{{ config.rule.statusCode }}</span>
          </div>
        </div>
      </div>

      <div class="card p-5">
        <h4 class="card-title">限流探测</h4>
        <div v-if="probeResult" class="mt-4 grid grid-cols-2 gap-3">
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <p class="text-xs text-gray-500">请求数</p>
            <p class="mt-1 text-xl font-bold text-gray-900">{{ probeResult.total }}</p>
          </div>
          <div class="rounded-lg bg-slate-50 px-4 py-3">
            <p class="text-xs text-gray-500">耗时</p>
            <p class="mt-1 text-xl font-bold text-gray-900">{{ probeResult.durationMs }}ms</p>
          </div>
          <div class="rounded-lg bg-green-50 px-4 py-3">
            <p class="text-xs text-green-700">通过</p>
            <p class="mt-1 text-xl font-bold text-green-800">{{ probeResult.success }}</p>
          </div>
          <div class="rounded-lg bg-amber-50 px-4 py-3">
            <p class="text-xs text-amber-700">限流</p>
            <p class="mt-1 text-xl font-bold text-amber-800">{{ probeResult.limited }}</p>
          </div>
        </div>
        <div v-else class="mt-4 rounded-lg bg-slate-50 px-4 py-8 text-center text-sm text-gray-500">
          尚未执行探测
        </div>
        <p v-if="probeError" class="mt-3 text-sm text-red-600">{{ probeError }}</p>
      </div>
    </div>

    <div class="grid-two">
      <div class="card p-5">
        <h4 class="card-title">接入应用</h4>
        <div class="mt-4 overflow-hidden rounded-lg border border-gray-200">
          <table class="table">
            <thead>
              <tr>
                <th>应用</th>
                <th>角色</th>
                <th>端口</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="service in config.services" :key="service.name">
                <td class="font-medium text-gray-900">{{ service.name }}</td>
                <td>{{ service.role }}</td>
                <td>{{ service.dashboardPort }}</td>
                <td><span class="badge badge-success">{{ service.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card p-5">
        <h4 class="card-title">Feign 降级链路</h4>
        <div class="mt-4 space-y-3">
          <div v-for="chain in config.fallbackChains" :key="`${chain.source}-${chain.target}`" class="rounded-lg bg-slate-50 px-4 py-3">
            <div class="flex items-center justify-between gap-4">
              <p class="min-w-0 truncate text-sm font-medium text-gray-900">{{ chain.source }} -> {{ chain.target }}</p>
              <span class="badge badge-success">{{ chain.status }}</span>
            </div>
            <p class="mt-1 text-xs text-gray-500">{{ chain.strategy }}</p>
          </div>
        </div>
      </div>
    </div>

    <div v-if="probeResult?.results?.length" class="card p-5">
      <h4 class="card-title">探测明细</h4>
      <div class="mt-4 overflow-hidden rounded-lg border border-gray-200">
        <table class="table">
          <thead>
            <tr>
              <th>#</th>
              <th>HTTP</th>
              <th>结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in probeResult.results" :key="index">
              <td>{{ index + 1 }}</td>
              <td>
                <span :class="['badge', item.status === config.rule.statusCode ? 'badge-warning' : item.ok ? 'badge-success' : 'badge-danger']">
                  {{ item.status || 'ERR' }}
                </span>
              </td>
              <td>{{ item.message || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { probeGatewayLimit, sentinelDemoConfig, sentinelDemoSummary } from '../../lib/sentinelDemo';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const config = sentinelDemoConfig();
const summary = sentinelDemoSummary();
const probing = ref(false);
const probeResult = ref(null);
const probeError = ref('');

async function runProbe() {
  probing.value = true;
  probeError.value = '';
  try {
    probeResult.value = await probeGatewayLimit({ token: session.token, count: 10 });
  } catch (error) {
    probeError.value = error.message || '限流探测失败';
  } finally {
    probing.value = false;
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
</script>
