<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">邮件详情</h3>
          <p class="mt-1 text-sm text-gray-500">只读查看单票邮件。</p>
        </div>
      </div>
    </div>
    <div class="grid-two">
      <div class="card p-5 space-y-2">
        <div><span class="text-gray-500">运单号：</span>{{ detail.waybillNo }}</div>
        <div><span class="text-gray-500">邮件范围：</span>{{ detail.mailScope }}</div>
        <div><span class="text-gray-500">邮件类型：</span>{{ detail.mailTypeCode }}</div>
        <div><span class="text-gray-500">服务类型：</span>{{ detail.serviceType }}</div>
        <div v-if="detail.destCountryCode"><span class="text-gray-500">寄达国：</span>{{ detail.destCountryCode }}</div>
        <div><span class="text-gray-500">状态：</span>{{ detail.status }}</div>
        <div><span class="text-gray-500">寄件人：</span>{{ detail.senderFullName }}</div>
        <div><span class="text-gray-500">收件人：</span>{{ detail.recipientFullName }}</div>
        <div><span class="text-gray-500">总包号：</span>{{ detail.bagNo }}</div>
        <div><span class="text-gray-500">当前网点：</span>{{ detail.currentFacilityCode }}</div>
        <div><span class="text-gray-500">目的网点：</span>{{ detail.destFacilityCode }}</div>
      </div>
      <div class="card p-5">
        <h3 class="card-title">轨迹时间线</h3>
        <TimelineView class="mt-4" :items="events" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useSessionStore } from '../../stores/session';
import TimelineView from '../../components/TimelineView.vue';
import { mailApi, trackingApi } from '../../lib/api';

const props = defineProps({ id: String });
const session = useSessionStore();
const detail = ref({});
const events = ref([]);

onMounted(async () => {
  detail.value = await mailApi.detail(props.id, session.token);
  const payload = await trackingApi.listEventsByWaybill(props.id, session.token);
  events.value = payload
    .slice()
    .sort((a, b) => String(a.eventTime || '').localeCompare(String(b.eventTime || '')))
    .map((item) => ({
      eventTime: item.eventTime,
      nodeName: item.locationText || item.facilityName || item.facilityCode || '未知节点',
      eventType: item.eventType,
      stageName: item.stageName,
      description: item.displayText || item.payload || item.sourceService || '',
    }));
});
</script>
