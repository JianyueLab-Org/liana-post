<template>
  <div class="shell grid min-h-screen grid-cols-[288px_minmax(0,1fr)]">
    <aside class="sidebar flex flex-col border-r border-cyan-100/80">
      <div class="px-5 py-5">
        <div class="surface-glow rounded-xl p-4">
          <div class="flex items-center gap-3">
            <div class="brand-mark flex h-12 w-12 items-center justify-center rounded-xl text-base font-black text-white">
              LP
            </div>
            <div class="min-w-0">
              <p class="text-sm font-black text-slate-950">Liana Post</p>
              <p class="mt-0.5 text-xs font-semibold text-cyan-700">邮政业务操作台</p>
            </div>
          </div>
        </div>
      </div>

      <nav class="flex-1 space-y-2 overflow-auto px-3 pb-3">
        <template v-for="item in menus" :key="item.id">
          <RouterLink
            v-if="!item.children?.length"
            :to="item.path"
            class="block rounded-lg px-4 py-3 text-sm transition"
            :class="isActive(item.path) ? 'bg-gradient-to-r from-cyan-600 to-teal-500 text-white shadow-lg shadow-cyan-700/20' : 'text-slate-600 hover:bg-white/75 hover:text-cyan-800 hover:shadow-sm'"
          >
            <div class="font-semibold">{{ item.name }}</div>
          </RouterLink>

          <div v-else class="rounded-xl border border-cyan-100 bg-white/62 px-2 py-2 shadow-sm shadow-cyan-900/5 backdrop-blur">
            <div class="px-2 py-2 text-xs font-bold text-slate-500">
              {{ item.name }}
            </div>
            <div class="space-y-1">
              <RouterLink
                v-for="child in item.children"
                :key="child.id"
                :to="child.path"
                class="block rounded-lg px-4 py-2 text-sm transition"
                :class="isActive(child.path) ? 'bg-gradient-to-r from-cyan-600 to-teal-500 text-white shadow-lg shadow-cyan-700/20' : 'text-slate-600 hover:bg-cyan-50 hover:text-cyan-800'"
              >
                {{ child.name }}
              </RouterLink>
            </div>
          </div>
        </template>
      </nav>

      <div class="border-t border-cyan-100 px-5 py-4">
        <div class="surface-glow rounded-xl px-3 py-3 text-xs text-slate-500">
          <div class="font-bold text-slate-900">{{ user?.displayName || user?.username || '未登录' }}</div>
          <div class="mt-2 flex items-center justify-between gap-3">
            <span>{{ user?.facilityCode || '无组织信息' }}</span>
            <span>{{ session.facilityTypeLabel }}</span>
          </div>
        </div>
      </div>
    </aside>

    <div class="flex min-w-0 flex-col">
      <header class="topbar sticky top-0 z-30 px-6 py-4">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p class="text-xs font-bold text-cyan-700">业务工作台</p>
            <h2 class="mt-1 text-2xl font-black text-slate-950">{{ title }}</h2>
          </div>
          <div class="flex items-center gap-3">
            <div class="rounded-full border border-cyan-200 bg-cyan-50 px-4 py-2 text-sm font-bold text-cyan-800">{{ user?.role || '未登录' }}</div>
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
