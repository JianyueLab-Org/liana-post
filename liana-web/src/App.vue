<template>
  <RouterView />
  <ApiErrorToast />
</template>

<script setup>
import { RouterView } from 'vue-router';
import ApiErrorToast from './components/ApiErrorToast.vue';
</script>
