<template>
  <div v-if="open" class="fixed inset-0 z-50 flex justify-end bg-slate-900/40">
    <div class="h-full w-full max-w-3xl overflow-auto bg-white p-6 shadow-2xl">
      <div class="mb-5 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-bold text-gray-900">{{ title }}</h2>
          <p v-if="subtitle" class="text-sm text-gray-500">{{ subtitle }}</p>
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
