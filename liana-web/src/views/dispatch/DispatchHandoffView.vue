<template>
  <div class="grid-two">
    <div class="card p-5">
      <h3 class="card-title">交接确认</h3>
      <p class="mt-2 text-sm text-gray-500">从当前机构已形成且未发出的总包里选择，接收机构会显示编码和名称。</p>
      <div class="mt-3 rounded-xl bg-slate-50 px-4 py-3 text-sm text-gray-700">
        当前机构：<span class="font-medium text-gray-900">{{ currentFacilityLabel }}</span>
      </div>
      <form class="mt-4 space-y-4" @submit.prevent="submit">
        <select class="select w-full" v-model="form.bagNo">
          <option value="">请选择总包</option>
          <option v-if="!availableBags.length" value="" disabled>暂无可交接的总包</option>
          <option v-for="item in availableBags" :key="item.bagNo" :value="item.bagNo">
            {{ item.bagNo }} ｜ {{ item.mailTypeCode }} ｜ {{ item.mailCount }} 封 ｜ {{ item.status }}
          </option>
        </select>

        <select class="select w-full" v-model="form.toFacilityCode">
          <option value="">请选择接收机构</option>
          <option v-if="!routeTargets.length" value="" disabled>暂无可选接收机构</option>
          <option v-for="item in routeTargets" :key="item.destinationFacilityCode" :value="item.destinationFacilityCode">
            {{ item.destinationFacilityCode }} · {{ facilityLabel(item.destinationFacilityCode) }}
          </option>
        </select>

        <input class="input" v-model.number="form.receiverId" type="number" placeholder="接收人ID（可选）" />
        <button class="btn btn-primary" :disabled="submitting || !canSubmit">提交交接</button>
      </form>
    </div>
    <div class="card p-5">
      <h3 class="card-title">提交结果</h3>
      <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { dispatchApi, facilityApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const result = ref('');
const submitting = ref(false);
const bags = ref([]);
const routes = ref([]);
const facilities = ref([]);
const form = reactive({ bagNo: '', toFacilityCode: '', receiverId: session.user?.userId || null });

const currentFacilityCode = computed(() => session.user?.facilityCode || '');
const currentFacilityLabel = computed(() => facilityLabel(currentFacilityCode.value));
const availableBags = computed(() => bags.value.filter((item) => item.originFacilityCode === currentFacilityCode.value && item.status !== 'DISPATCHED'));
const routeTargets = computed(() => routes.value.filter((item) => item.originFacilityCode === currentFacilityCode.value));
const canSubmit = computed(() => Boolean(form.bagNo && form.toFacilityCode) && !submitting.value);

function facilityLabel(code) {
  const facility = facilities.value.find((item) => item.facilityCode === code);
  if (!facility) {
    return code || '-';
  }
  return `${facility.facilityCode} · ${facility.name}`;
}

async function loadFacilities() {
  facilities.value = await facilityApi.listFacilities(session.token);
}

async function loadBags() {
  bags.value = await dispatchApi.listBags(session.token);
  if (!form.bagNo && availableBags.value.length) {
    form.bagNo = availableBags.value[0].bagNo;
  }
}

async function loadRoutes() {
  routes.value = await facilityApi.listRoutes(session.token);
  if (!form.toFacilityCode && routeTargets.value.length) {
    form.toFacilityCode = routeTargets.value[0].destinationFacilityCode;
  }
}

watch(currentFacilityCode, () => {
  form.bagNo = '';
  form.toFacilityCode = '';
  loadBags();
  loadRoutes();
});

async function submit() {
  if (!canSubmit.value) {
    result.value = '请选择总包和接收机构后再提交。';
    return;
  }

  submitting.value = true;
  try {
    const payload = await dispatchApi.createHandoff({
      bagNo: form.bagNo,
      toFacilityCode: form.toFacilityCode,
      receiverId: form.receiverId || null,
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
    await loadBags();
  } catch (error) {
    result.value = `交接失败：${error.message}`;
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadFacilities(), loadBags(), loadRoutes()]);
});
</script>