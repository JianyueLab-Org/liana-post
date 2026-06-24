<template>
  <div v-if="open" class="fixed inset-0 z-50 flex justify-end bg-slate-950/35 backdrop-blur-sm">
    <div class="h-full w-full max-w-3xl overflow-auto border-l border-cyan-100 bg-white p-6 shadow-2xl">
      <div class="mb-5 flex items-start justify-between gap-4 border-b border-cyan-100 pb-4">
        <div>
          <p class="text-xs font-semibold text-cyan-700">详情面板</p>
          <h2 class="mt-1 text-lg font-bold text-slate-950">{{ title }}</h2>
          <p v-if="subtitle" class="mt-1 text-sm text-slate-500">{{ subtitle }}</p>
        </div>
        <button class="btn btn-secondary" @click="$emit('close')">关闭</button>
      </div>
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({ open: Boolean, title: String, subtitle: String });
defineEmits(['close']);
</script>
