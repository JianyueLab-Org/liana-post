<template>
  <GenericListView :columns="columns" :rows="permissions" :status-options="statusOptions">
    <template #title>权限管理</template>
    <template #subtitle>权限码用于菜单访问和关键操作控制。</template>
  </GenericListView>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { authApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const permissions = ref([]);
const statusOptions = ['1', '0'];
const columns = [
  { key: 'code', label: '权限编码' },
  { key: 'name', label: '权限名称' },
  { key: 'description', label: '说明' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
];

onMounted(async () => {
  permissions.value = await authApi.permissions(session.token);
});
</script>
