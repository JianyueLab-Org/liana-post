<template>
  <div class="space-y-6">
    <div class="card p-5">
      <h3 class="card-title">轨迹查询</h3>
      <div class="mt-4 flex gap-3">
        <input v-model="waybillNo" class="input" placeholder="输入运单号" />
        <button class="btn btn-primary" @click="search">查询</button>
      </div>
      <p class="mt-3 text-sm text-gray-500">Tracking 只读：只查询事件，不写业务。</p>
    </div>
    <div class="grid-two">
      <div class="card p-5">
        <h3 class="card-title">结构化数据</h3>
        <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ summary }}</pre>
      </div>
      <div class="card p-5">
        <h3 class="card-title">轨迹时间线</h3>
        <TimelineView class="mt-4" :items="events" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import TimelineView from '../../components/TimelineView.vue';
import { trackingApi } from '../../lib/api';
import { getUpuBarcodeError, normalizeUpuBarcode } from '../../lib/upuBarcode';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const waybillNo = ref('EE123456785LN');
const summary = ref({});
const events = ref([]);

function mapEvent(item) {
  return {
    eventTime: item.eventTime,
    nodeName: item.locationText || item.facilityName || item.facilityCode || '未知节点',
    eventType: item.eventType,
    stageName: item.stageName,
    description: item.displayText || item.payload || item.sourceService || item.operatorName || '',
  };
}

async function search() {
  const normalized = normalizeUpuBarcode(waybillNo.value);
  const error = getUpuBarcodeError(normalized, '运单号');
  if (error) {
    summary.value = { error };
    events.value = [];
    return;
  }
  waybillNo.value = normalized;
  const payload = await trackingApi.search({ waybillNo: normalized }, session.token);
  events.value = payload
    .slice()
    .sort((a, b) => String(a.eventTime || '').localeCompare(String(b.eventTime || '')))
    .map(mapEvent);
  summary.value = { waybillNo: normalized, count: events.value.length };
}
</script>
