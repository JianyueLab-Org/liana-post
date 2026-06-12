<template>
  <ol class="space-y-4">
    <li v-for="item in items" :key="item.eventTime" class="flex gap-4">
      <div class="mt-1 h-3 w-3 rounded-full bg-blue-500"></div>
      <div class="flex-1 border-b border-dashed border-gray-200 pb-4">
        <div class="flex flex-wrap items-center gap-2">
          <strong class="text-sm text-gray-900">{{ item.nodeName }}</strong>
          <StatusBadge :status="item.eventType" />
        </div>
        <p class="mt-1 text-sm text-gray-600">{{ item.description }}</p>
        <p class="mt-1 text-xs text-gray-400">{{ item.eventTime }}</p>
      </div>
    </li>
  </ol>
</template>

<script setup>
import StatusBadge from './StatusBadge.vue';
defineProps({ items: { type: Array, default: () => [] } });
</script>
