<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">用户管理</h3>
          <p class="mt-1 text-sm text-gray-500">真实接口：`/api/auth/system/users`、`/api/auth/system/project/init`</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="loadUsers">刷新</button>
          <button class="btn btn-primary" :disabled="loading" @click="runInit">项目初始化</button>
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
      <p class="mt-2 text-xs text-gray-400">创建用户默认使用当前表单中的密码；项目初始化会创建 `testclerk/testadmin`，密码统一为 `123456`。</p>
    </div>

    <GenericListView :columns="columns" :rows="users" :status-options="statusOptions">
      <template #title>用户列表</template>
      <template #subtitle>真实后端返回的用户、角色和权限</template>
      <template #actions="{ row }">
        <button class="btn btn-secondary" @click="open(row)">详情</button>
        <button class="btn btn-ghost" @click="fillReset(row)">重置密码</button>
      </template>
    </GenericListView>

    <DetailDrawer :open="drawerOpen" title="用户详情" :subtitle="current?.username" @close="drawerOpen = false">
      <div class="grid-two">
        <div class="card p-4">
          <p class="text-sm text-gray-500">基础信息</p>
          <pre class="mt-3 whitespace-pre-wrap text-sm text-gray-700">{{ current }}</pre>
        </div>
        <div class="card p-4 space-y-3">
          <p class="text-sm text-gray-500">重置密码</p>
          <input v-model="resetForm.username" class="input" placeholder="用户名" />
          <input v-model="resetForm.newPassword" class="input" placeholder="新密码" />
          <button class="btn btn-primary w-full" @click="resetPassword">提交重置</button>
        </div>
      </div>
    </DetailDrawer>

    <DetailDrawer :open="initDrawerOpen" title="项目初始化结果" subtitle="/api/auth/system/project/init" @close="initDrawerOpen = false">
      <pre class="whitespace-pre-wrap text-sm text-gray-700">{{ initResult }}</pre>
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
