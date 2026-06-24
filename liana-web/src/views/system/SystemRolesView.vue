<template>
  <GenericListView :columns="columns" :rows="roles" :status-options="statusOptions">
    <template #title>角色管理</template>
    <template #subtitle>角色作为用户与权限之间的授权分组。</template>
  </GenericListView>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { authApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const roles = ref([]);
const statusOptions = ['1', '0'];
const columns = [
  { key: 'code', label: '角色编码' },
  { key: 'name', label: '角色名称' },
  { key: 'description', label: '说明' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
];

onMounted(async () => {
  roles.value = await authApi.roles(session.token);
});
</script>
