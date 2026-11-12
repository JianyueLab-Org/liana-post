<template>
  <TransitionGroup name="api-error" tag="div" class="api-error-stack" aria-live="assertive">
    <section v-for="item in errors" :key="item.id" class="api-error-toast" role="alert">
      <div class="api-error-icon">!</div>
      <div class="min-w-0 flex-1">
        <div class="flex items-start justify-between gap-3">
          <h2 class="api-error-title">请求失败</h2>
          <button class="api-error-close" type="button" aria-label="关闭请求失败提示" @click="dismiss(item.id)">x</button>
        </div>
        <p class="api-error-message">{{ item.message }}</p>
        <p class="api-error-meta">
          <span v-if="item.status">HTTP {{ item.status }}</span>
          <span v-if="item.code">业务码 {{ item.code }}</span>
          <span v-if="item.method || item.path">{{ item.method }} {{ item.path }}</span>
          <span>{{ item.occurredAt }}</span>
        </p>
      </div>
    </section>
  </TransitionGroup>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { API_ERROR_EVENT } from '../lib/apiErrorEvents';

const errors = ref([]);
const timers = new Map();

function dismiss(id) {
  errors.value = errors.value.filter((item) => item.id !== id);
  window.clearTimeout(timers.get(id));
  timers.delete(id);
}

function pushError(event) {
  const item = event.detail || {};
  errors.value = [item, ...errors.value].slice(0, 4);

  const timer = window.setTimeout(() => dismiss(item.id), 9000);
  timers.set(item.id, timer);
}

onMounted(() => {
  window.addEventListener(API_ERROR_EVENT, pushError);
});

onBeforeUnmount(() => {
  window.removeEventListener(API_ERROR_EVENT, pushError);
  timers.forEach((timer) => window.clearTimeout(timer));
  timers.clear();
});
</script>
