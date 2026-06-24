<template>
  <div class="card p-5">
    <h3 class="card-title">事件流</h3>
    <div class="mt-4 grid-two">
      <div class="card p-4" v-for="item in events" :key="item.eventNo">
        <StatusBadge :status="item.eventType" />
        <p class="mt-2 text-sm font-medium text-gray-900">{{ item.displayText || item.stageName || item.eventType }}</p>
        <p class="text-sm text-gray-600">{{ item.waybillNo }} · {{ item.locationText || item.facilityName || item.facilityCode || '-' }}</p>
        <p class="mt-1 text-xs text-gray-400">{{ item.eventTime }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import StatusBadge from '../../components/StatusBadge.vue';
import { trackingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const events = ref([]);

onMounted(async () => {
  events.value = await trackingApi.listEvents(session.token);
});
</script>
