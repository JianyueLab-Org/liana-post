<template>
  <div class="space-y-6">
    <div class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">接收总包</h3>
          <p class="mt-1 text-sm text-gray-500">接收后自动开拆，包内邮件直接进入待投打印清单。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" @click="load">刷新</button>
          <button class="btn btn-primary" :disabled="loading || !form.packageId" @click="receiveAndOpen">接收并开拆</button>
        </div>
      </div>
    </div>

    <div class="grid gap-6 lg:grid-cols-2">
      <section class="card p-5">
        <h4 class="text-base font-semibold text-gray-900">总包列表</h4>
        <div class="mt-4 space-y-3">
          <button
            v-for="item in packages"
            :key="item.packageId"
            type="button"
            class="w-full rounded-xl border px-4 py-3 text-left transition"
            :class="selectedPackageId === item.packageId ? 'border-blue-500 bg-blue-50' : 'border-gray-200 bg-white'"
            @click="selectPackage(item.packageId)"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="font-medium text-gray-900">{{ item.packageId }}</span>
              <span class="text-xs text-gray-500">{{ item.packageStatus }}</span>
            </div>
            <div class="mt-2 grid grid-cols-2 gap-2 text-xs text-gray-500">
              <span>数量：{{ item.mailCount }}</span>
              <span>到达：{{ item.arrivedCount }}</span>
              <span>已分拣：{{ item.sortedCount }}</span>
              <span>已投递：{{ item.deliveredCount }}</span>
            </div>
            <div class="mt-2 text-xs text-gray-400">{{ (item.previewWaybillNos || []).join(' / ') }}</div>
          </button>
          <div v-if="!packages.length" class="py-8 text-sm text-gray-400">当前没有可接收的总包</div>
        </div>
      </section>

      <section class="card p-5">
        <h4 class="text-base font-semibold text-gray-900">操作面板</h4>
        <div class="mt-4 space-y-4">
          <input v-model="form.packageId" class="input" placeholder="总包号" />
          <div class="rounded-lg bg-slate-50 px-4 py-3 text-sm text-gray-600">
            接收并开拆机构：<span class="font-semibold text-gray-900">{{ currentFacilityCode || '-' }}</span>
          </div>
          <pre class="whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const packages = ref([]);
const selectedPackageId = ref('');
const loading = ref(false);
const result = ref('');
const currentFacilityCode = computed(() => session.user?.facilityCode || '');
const form = reactive({
  packageId: '',
});

function selectPackage(packageId) {
  const item = packages.value.find((entry) => entry.packageId === packageId);
  if (!item) return;
  selectedPackageId.value = item.packageId;
  form.packageId = item.packageId;
}

async function load() {
  packages.value = await mailApi.listPackages(session.token);
  if (!form.packageId && packages.value.length) {
    selectPackage(packages.value[0].packageId);
  }
}

async function receiveAndOpen() {
  loading.value = true;
  try {
    const payload = await mailApi.receiveAndOpenPackage({
      packageId: form.packageId.trim().toUpperCase(),
      currentFacilityCode: currentFacilityCode.value,
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
    await load();
  } catch (error) {
    result.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
