<template>
  <div class="shell grid min-h-screen grid-cols-[260px_minmax(0,1fr)]">
    <aside class="sidebar flex flex-col border-r border-slate-800">
      <div class="border-b border-slate-800 px-5 py-5">
        <p class="text-xs uppercase tracking-[0.25em] text-slate-400">Liana Pacific Postal System</p>
        <h1 class="mt-2 text-lg font-bold text-white">邮政业务操作台</h1>
        <p class="mt-1 text-xs text-slate-400">业务流驱动 · RBAC 菜单 · 真实后端接入</p>
      </div>

      <nav class="flex-1 space-y-2 p-3">
        <template v-for="item in menus" :key="item.id">
          <RouterLink
            v-if="!item.children?.length"
            :to="item.path"
            class="block rounded-xl px-4 py-3 text-sm transition"
            :class="isActive(item.path) ? 'bg-blue-500 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'"
          >
            <div class="font-semibold">{{ item.name }}</div>
          </RouterLink>

          <div v-else class="rounded-xl border border-slate-800/80 bg-slate-900/40 px-2 py-2">
            <div class="px-2 py-2 text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
              {{ item.name }}
            </div>
            <div class="space-y-1">
              <RouterLink
                v-for="child in item.children"
                :key="child.id"
                :to="child.path"
                class="block rounded-lg px-4 py-2 text-sm transition"
                :class="isActive(child.path) ? 'bg-blue-500 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'"
              >
                {{ child.name }}
              </RouterLink>
            </div>
          </div>
        </template>
      </nav>

      <div class="border-t border-slate-800 px-5 py-4 text-xs text-slate-400">
        <div>{{ user?.displayName || user?.username || '未登录' }}</div>
        <div class="mt-1">{{ user?.facilityCode || '无组织信息' }}</div>
      </div>
    </aside>

    <div class="flex min-w-0 flex-col">
      <header class="topbar sticky top-0 z-30 px-6 py-4">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p class="text-xs uppercase tracking-[0.25em] text-gray-500">Business Console</p>
            <h2 class="text-xl font-bold text-gray-900">{{ title }}</h2>
          </div>
          <div class="flex items-center gap-3">
            <div class="rounded-full bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700">{{ user?.role || '未登录' }}</div>
            <button class="btn btn-secondary" @click="logout">退出</button>
          </div>
        </div>
      </header>
      <main class="min-w-0 flex-1 p-6"><RouterView /></main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter, RouterView, RouterLink } from 'vue-router';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const menus = computed(() => session.menuTree || []);
const user = computed(() => session.user);
const title = computed(() => route.meta.title || '工作台');

function isActive(path) {
  return route.path === path || route.path.startsWith(`${path}/`);
}

function logout() {
  session.logout();
  router.push('/login');
}
</script>
