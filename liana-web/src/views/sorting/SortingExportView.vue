<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">国家格口封袋</h3>
          <p class="mt-1 text-sm text-gray-500">互换局出口按目的国聚合，确认后生成出口总包。</p>
        </div>
        <span class="rounded-full bg-blue-600 px-3 py-1 text-xs font-medium text-white">
          {{ session.facilityTypeLabel }}
        </span>
      </div>
    </div>

    <div class="grid gap-6 lg:grid-cols-[1.1fr_1.4fr]">
      <section class="card p-5">
        <h4 class="text-base font-semibold text-gray-900">国家格口</h4>
        <p class="mt-1 text-sm text-gray-500">按国家自动聚合待处理国际邮件，支持整格出口封袋。</p>

        <div class="mt-4 flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="refresh">刷新</button>
          <button class="btn btn-primary" :disabled="!selectedCountry || submitting" @click="sealSelected">
            {{ submitting ? '处理中...' : '出口封袋' }}
          </button>
        </div>

        <div class="mt-4 space-y-3">
          <article
            v-for="slot in slots"
            :key="slot.countryCode"
            class="rounded-2xl border p-4 transition"
            :class="selectedCountry === slot.countryCode ? 'border-blue-500 bg-blue-50' : 'border-gray-200 bg-white'"
            @click="selectedCountry = slot.countryCode"
          >
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="font-semibold text-gray-900">{{ slot.countryCode }} <span class="text-xs text-gray-500">{{ slot.countryName }}</span></div>
                <div class="mt-1 text-xs text-gray-500">出口局：{{ slot.exportFacilityCode || '-' }}</div>
              </div>
              <span class="rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-700">{{ slot.pendingCount }}</span>
            </div>
            <div class="mt-3 text-xs text-gray-500">
              {{ (slot.previewItemNos || []).join(' / ') || '-' }}
            </div>
          </article>

          <div v-if="!slots.length" class="rounded-2xl border border-dashed border-gray-300 p-8 text-center text-sm text-gray-400">
            暂无国家格口
          </div>
        </div>
      </section>

      <section class="card p-5">
        <div class="flex items-center justify-between gap-3">
          <div>
            <h4 class="text-base font-semibold text-gray-900">封袋结果</h4>
            <p class="mt-1 text-sm text-gray-500">这里展示最近一次出口封袋或国家格口状态。</p>
          </div>
          <button class="btn btn-secondary" @click="reset">清空</button>
        </div>

        <div class="mt-4 rounded-2xl border border-gray-200 bg-white p-4 text-sm text-gray-700">
          <div>当前局：<span class="font-semibold">{{ session.user?.facilityCode || '-' }}</span></div>
          <div class="mt-1">当前国家：<span class="font-semibold">{{ selectedCountry || '-' }}</span></div>
          <div class="mt-1">出口模式：<span class="font-semibold">国家格口</span></div>
        </div>

        <pre class="mt-4 whitespace-pre-wrap rounded-2xl border border-gray-200 bg-slate-50 p-4 text-sm text-gray-700">{{ result }}</pre>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const submitting = ref(false);
const slots = ref([]);
const selectedCountry = ref('');
const result = ref('');

async function refresh() {
  loading.value = true;
  try {
    slots.value = await sortingApi.listCountrySlots(session.token, session.user?.facilityCode || '');
    const selectedStillVisible = slots.value.some((item) => item.countryCode === selectedCountry.value);
    if (!selectedStillVisible) {
      selectedCountry.value = slots.value[0]?.countryCode || '';
    } else if (!selectedCountry.value && slots.value.length) {
      selectedCountry.value = slots.value[0].countryCode;
    }
  } catch (error) {
    result.value = error?.message || '加载国家格口失败';
  } finally {
    loading.value = false;
  }
}

function reset() {
  result.value = '';
  selectedCountry.value = slots.value[0]?.countryCode || '';
}

async function sealSelected() {
  if (!selectedCountry.value) return;
  const slot = slots.value.find((item) => item.countryCode === selectedCountry.value);
  if (!slot) return;
  submitting.value = true;
  try {
    const payload = await sortingApi.sealCountrySlot({
      stationCode: session.user?.facilityCode || '',
      countryCode: slot.countryCode,
      exportFacilityCode: slot.exportFacilityCode || 'A2',
      operatorId: session.user?.userId || null,
      idempotencyKey: `country-slot-${Date.now()}-${slot.countryCode}`,
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
    await refresh();
  } catch (error) {
    result.value = error?.message || '出口封袋失败';
  } finally {
    submitting.value = false;
  }
}

onMounted(refresh);
</script>
