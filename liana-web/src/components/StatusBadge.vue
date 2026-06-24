<template>
  <span class="badge" :class="badgeClass">{{ label }}</span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({ status: { type: String, required: true } });

const map = {
  ACTIVE: ['badge-success', '启用'],
  CONFIRMED: ['badge-success', '已确认'],
  ACCEPTED: ['badge-info', '已收寄'],
  IN_SORTING: ['badge-warning', '分拣中'],
  IN_TRANSIT: ['badge-info', '运输中'],
  DISPATCHED: ['badge-info', '已发出'],
  ARRIVED: ['badge-warning', '已到达'],
  DELIVERED: ['badge-success', '已投递'],
  RETURNED: ['badge-neutral', '已退回'],
  DRAFT: ['badge-neutral', '草稿'],
  PENDING: ['badge-warning', '待发送'],
  SENT: ['badge-success', '已发送'],
  RUNNING: ['badge-info', '运行中'],
  IDLE: ['badge-neutral', '空闲'],
  PLANNED: ['badge-warning', '计划中'],
  SORTED: ['badge-success', '已分拣'],
  READY_FOR_DELIVERY: ['badge-warning', '待投递'],
};

const badgeClass = computed(() => map[props.status]?.[0] || 'badge-neutral');
const label = computed(() => map[props.status]?.[1] || props.status);
</script>
