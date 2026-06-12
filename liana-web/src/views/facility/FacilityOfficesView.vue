<template><GenericListView :columns="columns" :rows="rows" :status-options="statusOptions" /></template>
<script setup>
import { onMounted, ref } from 'vue';
import GenericListView from '../shared/GenericListView.vue';
import { facilityApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const statusOptions = ['1'];
const columns = [{ key: 'facilityCode', label: '网点编码' }, { key: 'name', label: '名称' }, { key: 'typeCode', label: '类型' }, { key: 'parentFacilityCode', label: '上级' }, { key: 'status', label: '状态' }];

async function load() {
  const allFacilities = await facilityApi.listFacilities(session.token);
  rows.value = allFacilities.filter((item) => item.typeCode === 'POST_OFFICE');
}

onMounted(load);
</script>