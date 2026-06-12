<template>
  <div class="space-y-6">
    <div class="grid-three">
      <div v-for="item in metrics" :key="item.label" class="card p-5">
        <p class="text-sm text-gray-500">{{ item.label }}</p>
        <p class="mt-2 text-3xl font-bold text-gray-900">{{ item.value }}</p>
        <p class="mt-1 text-xs text-gray-500">{{ item.hint }}</p>
      </div>
    </div>
    <div class="grid-two">
      <div class="card p-5">
        <h3 class="card-title">今日业务流程</h3>
        <div class="mt-4 space-y-3">
          <div v-for="step in flows" :key="step.name" class="flex items-center justify-between rounded-xl bg-slate-50 px-4 py-3">
            <span class="text-sm font-medium text-gray-800">{{ step.name }}</span>
            <StatusBadge :status="step.status" />
          </div>
        </div>
      </div>
      <div class="card p-5">
        <h3 class="card-title">当前登录信息</h3>
        <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ user }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const user = computed(() => session.user);
const metrics = [
  { label: '身份', value: user.value?.role || '-', hint: '真实后端 profile' },
  { label: '机构', value: user.value?.facilityCode || '-', hint: '从 JWT/profile 读取' },
  { label: '权限数', value: String(user.value?.permissions?.length || 0), hint: '后端返回 permissions' },
];
const flows = [
  { name: '收寄', status: 'ACCEPTED' },
  { name: '分拣', status: 'IN_SORTING' },
  { name: '封发', status: 'CONFIRMED' },
  { name: '转运交接', status: 'IN_TRANSIT' },
];
</script>
