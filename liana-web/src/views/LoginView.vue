<template>
  <div class="flex min-h-screen items-center justify-center bg-slate-100 px-4">
    <div class="card w-full max-w-md p-8">
      <p class="text-xs uppercase tracking-[0.3em] text-blue-600">Liana Postal System</p>
      <h1 class="mt-2 text-2xl font-bold text-gray-900">业务后台登录</h1>
      <p class="mt-1 text-sm text-gray-500">登录后从真实后端拉取 `/api/auth/login` 和 `/api/auth/profile`。</p>
      <form class="mt-6 space-y-4" @submit.prevent="login">
        <input v-model="form.username" class="input" placeholder="clerk001 / manager001 / sorter001 / admin001" />
        <input v-model="form.password" type="password" class="input" placeholder="password" />
        <button class="btn btn-primary w-full" :disabled="loading">{{ loading ? '登录中...' : '进入系统' }}</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSessionStore } from '../stores/session';
import { authApi } from '../lib/api';

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ username: 'clerk001', password: 'password' });

async function login() {
  loading.value = true;
  try {
    const result = await authApi.login(form);
    session.setSession(result.token, {
      ...result,
      username: result.username,
      displayName: result.displayName,
      role: result.role,
      facilityCode: result.facilityCode,
    });
    await session.bootstrap(true);
    router.push('/dashboard');
  } finally {
    loading.value = false;
  }
}
</script>
