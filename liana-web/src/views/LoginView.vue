<template>
  <div class="shell flex min-h-screen items-center justify-center px-4 py-10">
    <div class="w-full max-w-md">
      <div class="mb-6 text-center">
        <div class="brand-mark mx-auto flex h-16 w-16 items-center justify-center rounded-2xl text-xl font-black text-white">
          LP
        </div>
        <p class="mt-5 text-sm font-bold text-cyan-700">Liana Post</p>
        <h1 class="mt-2 bg-gradient-to-r from-slate-950 via-cyan-800 to-teal-600 bg-clip-text text-3xl font-black text-transparent">
          邮政业务操作台
        </h1>
        <p class="mt-3 text-sm text-slate-500">统一身份认证后进入邮政物流业务工作台。</p>
      </div>

      <div class="surface-glow rounded-2xl p-1">
        <div class="card p-8">
          <form class="space-y-4" @submit.prevent="login">
            <label class="block">
              <span class="mb-1.5 block text-sm font-bold text-slate-700">账号</span>
              <input v-model="form.username" class="input" placeholder="clerk001 / manager001 / sorter001 / admin001" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-bold text-slate-700">密码</span>
              <input v-model="form.password" type="password" class="input" placeholder="请输入密码" />
            </label>
            <button class="btn btn-primary w-full" :disabled="loading">{{ loading ? '登录中...' : '进入系统' }}</button>
            <p v-if="error" class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
              {{ error }}
            </p>
          </form>
        </div>
      </div>
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
const error = ref('');
const form = reactive({ username: 'clerk001', password: 'password' });

async function login() {
  loading.value = true;
  error.value = '';
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
  } catch (err) {
    error.value = err?.message || '登录失败';
  } finally {
    loading.value = false;
  }
}
</script>
