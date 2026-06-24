<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">用户管理</h3>
          <p class="mt-1 text-sm text-gray-500">维护用户账号、所属网点、角色与启用状态。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="loadUsers">刷新</button>
          <button class="btn btn-primary" :disabled="loading" @click="runInit">初始化基础权限</button>
        </div>
      </div>

      <div class="mt-4 grid-three">
        <input v-model="form.username" class="input" placeholder="用户名" />
        <input v-model="form.displayName" class="input" placeholder="姓名" />
        <select v-model="form.roleCode" class="select">
          <option value="">选择角色</option>
          <option v-for="role in roles" :key="role.code" :value="role.code">{{ role.name }} ({{ role.code }})</option>
        </select>
      </div>
      <div class="mt-3 grid-three">
        <input v-model="form.password" class="input" placeholder="初始密码" />
        <input v-model="form.facilityCode" class="input" placeholder="网点编码" />
        <button class="btn btn-primary" :disabled="loading || !canCreate" @click="createUser">新增用户</button>
      </div>
    </div>

    <GenericListView :columns="columns" :rows="users" :status-options="statusOptions">
      <template #title>用户列表</template>
      <template #subtitle>用户通过角色获得权限，符合 User-Role-Permission 的 RBAC 模型。</template>
      <template #actions="{ row }">
        <button class="btn btn-secondary" @click="open(row)">详情</button>
        <button class="btn btn-ghost" @click="fillReset(row)">重置密码</button>
      </template>
    </GenericListView>

    <DetailDrawer :open="drawerOpen" title="用户详情" :subtitle="current?.username" @close="drawerOpen = false">
      <div class="grid-two">
        <div class="card p-4">
          <p class="text-sm text-gray-500">基础信息</p>
          <dl class="mt-3 space-y-2 text-sm text-gray-700">
            <div><dt class="inline text-gray-400">用户名：</dt><dd class="inline">{{ current?.username || '-' }}</dd></div>
            <div><dt class="inline text-gray-400">姓名：</dt><dd class="inline">{{ current?.displayName || '-' }}</dd></div>
            <div><dt class="inline text-gray-400">角色：</dt><dd class="inline">{{ current?.role || '-' }}</dd></div>
            <div><dt class="inline text-gray-400">网点：</dt><dd class="inline">{{ current?.facilityCode || '-' }}</dd></div>
            <div><dt class="inline text-gray-400">状态：</dt><dd class="inline">{{ current?.status }}</dd></div>
          </dl>
          <p class="mt-4 text-sm text-gray-500">权限码</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <span v-for="item in currentPermissions" :key="item" class="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700">{{ item }}</span>
            <span v-if="!currentPermissions.length" class="text-sm text-gray-400">暂无权限</span>
          </div>
        </div>
        <div class="card p-4 space-y-3">
          <p class="text-sm text-gray-500">重置密码</p>
          <input v-model="resetForm.username" class="input" placeholder="用户名" />
          <input v-model="resetForm.newPassword" class="input" placeholder="新密码" />
          <button class="btn btn-primary w-full" @click="resetPassword">提交重置</button>
        </div>
      </div>
    </DetailDrawer>

    <DetailDrawer :open="initDrawerOpen" title="权限初始化结果" subtitle="角色与权限基础数据" @close="initDrawerOpen = false">
      <pre class="whitespace-pre-wrap text-sm text-gray-700">{{ initResultText }}</pre>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { authApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const users = ref([]);
const roles = ref([]);
const current = ref(null);
const drawerOpen = ref(false);
const initDrawerOpen = ref(false);
const initResult = ref(null);
const statusOptions = ['1', '0'];
const columns = [
  { key: 'username', label: '用户名' },
  { key: 'displayName', label: '姓名' },
  { key: 'role', label: '角色' },
  { key: 'facilityCode', label: '网点' },
  { key: 'phone', label: '电话' },
  { key: 'email', label: '邮箱' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' },
];
const form = reactive({ username: '', password: '123456', displayName: '', facilityCode: 'B1', roleCode: '' });
const resetForm = reactive({ username: '', newPassword: '123456' });

const canCreate = computed(() => Boolean(form.username && form.password && form.displayName && form.facilityCode && form.roleCode));
const currentPermissions = computed(() => current.value?.permissions || []);
const initResultText = computed(() => (initResult.value ? JSON.stringify(initResult.value, null, 2) : ''));

async function loadUsers() {
  loading.value = true;
  try {
    const [userList, roleList] = await Promise.all([
      authApi.users(session.token),
      authApi.roles(session.token),
    ]);
    users.value = userList || [];
    roles.value = roleList || [];
    if (!form.roleCode && roles.value.length > 0) {
      form.roleCode = roles.value[0].code;
    }
  } finally {
    loading.value = false;
  }
}

function open(row) {
  current.value = row;
  drawerOpen.value = true;
}

function fillReset(row) {
  resetForm.username = row?.username || '';
  resetForm.newPassword = '123456';
  current.value = row;
  drawerOpen.value = true;
}

async function createUser() {
  loading.value = true;
  try {
    await authApi.createUser({
      username: form.username,
      password: form.password,
      displayName: form.displayName,
      facilityCode: form.facilityCode,
      roleCode: form.roleCode,
      status: 1,
    }, session.token);
    await loadUsers();
    form.username = '';
    form.displayName = '';
    form.password = '123456';
  } finally {
    loading.value = false;
  }
}

async function resetPassword() {
  if (!resetForm.username) return;
  loading.value = true;
  try {
    await authApi.resetPassword({ username: resetForm.username, newPassword: resetForm.newPassword }, session.token);
    await loadUsers();
  } finally {
    loading.value = false;
  }
}

async function runInit() {
  loading.value = true;
  try {
    initResult.value = await authApi.initProject(session.token);
    initDrawerOpen.value = true;
    await loadUsers();
  } finally {
    loading.value = false;
  }
}

onMounted(loadUsers);
</script>
