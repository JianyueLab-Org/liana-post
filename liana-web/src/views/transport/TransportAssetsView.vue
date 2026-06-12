<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">运输资源</h3>
          <p class="mt-1 text-sm text-gray-500">管理邮船、飞机、邮车等运输资产。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" @click="load">刷新</button>
          <button class="btn btn-primary" @click="openCreate">新增</button>
        </div>
      </div>
      <div class="mt-4 grid-three">
        <input v-model="query.keyword" class="input" placeholder="编码 / 名称" />
        <select v-model="query.status" class="select">
          <option value="">全部状态</option>
          <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
        </select>
        <input v-model.number="query.pageSize" class="input" type="number" min="1" placeholder="每页条数" />
      </div>
    </div>

    <div class="card p-5">
      <DataTable :columns="columns" :rows="rows">
        <template #actions="{ row }">
          <button class="btn btn-secondary" @click="openEdit(row)">编辑</button>
        </template>
      </DataTable>
      <div class="mt-4 flex items-center justify-between text-sm text-gray-500">
        <span>第 {{ page }} 页 / 共 {{ total }} 条</span>
        <div class="flex gap-2">
          <button class="btn btn-secondary" :disabled="page === 1" @click="page--; load()">上一页</button>
          <button class="btn btn-secondary" :disabled="page * query.pageSize >= total" @click="page++; load()">下一页</button>
        </div>
      </div>
    </div>

    <DetailDrawer :open="drawerOpen" :title="editing ? '编辑资源' : '新增资源'" :subtitle="form.code || '未命名资源'" @close="drawerOpen = false">
      <div class="space-y-3">
        <input v-model="form.code" class="input" placeholder="资源编码" />
        <input v-model="form.name" class="input" placeholder="资源名称" />
        <select v-model="form.type" class="select">
          <option value="">选择类型</option>
          <option value="SHIP">SHIP</option>
          <option value="AIRCRAFT">AIRCRAFT</option>
          <option value="TRUCK">TRUCK</option>
        </select>
        <input v-model="form.capacity" class="input" type="number" placeholder="容量" />
        <select v-model="form.status" class="select">
          <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
        </select>
        <button class="btn btn-primary w-full" @click="save">保存</button>
      </div>
    </DetailDrawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import DataTable from '../../components/DataTable.vue';
import DetailDrawer from '../../components/DetailDrawer.vue';
import { transportApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const statuses = ['AVAILABLE', 'IN_SERVICE', 'MAINTENANCE', 'RETIRED'];
const columns = [
  { key: 'code', label: '编码' },
  { key: 'name', label: '名称' },
  { key: 'type', label: '类型' },
  { key: 'capacity', label: '容量' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作' },
];
const query = reactive({ page: 1, pageSize: 10, keyword: '', status: '' });
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const drawerOpen = ref(false);
const editing = ref(false);
const form = reactive({ code: '', name: '', type: 'TRUCK', capacity: '', status: 'AVAILABLE' });

async function load() {
  query.page = page.value;
  const result = await transportApi.listAssets(query, session.token);
  rows.value = result.list || [];
  total.value = result.total || 0;
  page.value = result.page || 1;
}

function openCreate() {
  editing.value = false;
  Object.assign(form, { code: '', name: '', type: 'TRUCK', capacity: '', status: 'AVAILABLE' });
  drawerOpen.value = true;
}

function openEdit(row) {
  editing.value = true;
  Object.assign(form, row);
  drawerOpen.value = true;
}

async function save() {
  const payload = { ...form, capacity: Number(form.capacity) };
  if (editing.value) {
    await transportApi.updateAsset(form.code, payload, session.token);
  } else {
    await transportApi.createAsset(payload, session.token);
  }
  drawerOpen.value = false;
  await load();
}

onMounted(load);
</script>
