<template>
  <div class="space-y-6">
    <div class="card p-5 hero-panel">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">收寄录入</h3>
          <p class="mt-1 text-sm text-gray-500">只录入收寄必需信息，邮件范围区分国内和国际。</p>
        </div>
      </div>
      <form class="mt-4 grid-two" @submit.prevent="submit">
        <select class="select col-span-2" v-model="form.mailTypeCode">
          <option value="">请选择邮件类型</option>
          <option v-if="!mailTypes.length" value="" disabled>正在加载邮件类型...</option>
          <option v-for="item in mailTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select>
        <select class="select col-span-2" v-model="form.mailScope">
          <option value="DOMESTIC">国内邮件</option>
          <option value="INTERNATIONAL">国际邮件</option>
        </select>
        <div v-if="isInternational" class="col-span-2 space-y-1">
          <input
            class="input"
            v-model="destCountryInput"
            list="country-suggestions"
            placeholder="请输入寄达国，支持国家名称或国家代码"
            @input="onDestCountryInput"
            @change="onDestCountryCommit"
          />
          <datalist id="country-suggestions">
            <option v-for="item in filteredCountryHints" :key="item.code" :value="countryHintValue(item)">
              {{ item.name }} ({{ item.code }})
            </option>
          </datalist>
          <p class="text-xs text-gray-500">关键词既可以是国名，也可以是国家代码。</p>
        </div>
        <input class="input" v-model="form.senderFullName" placeholder="寄件人姓名" />
        <input class="input" v-model="form.senderPhone" placeholder="寄件人电话" />
        <input class="input col-span-2" v-model="form.senderAddress" placeholder="寄件人地址" />
        <input class="input" v-model="form.senderPostcode" placeholder="寄件人邮编" />
        <input class="input" v-model="form.senderIdType" placeholder="证件类型" />
        <input class="input" v-model="form.senderIdNumber" placeholder="证件号码" />
        <input class="input" v-model="form.recipientFullName" placeholder="收件人姓名" />
        <input class="input" v-model="form.recipientPhone" placeholder="收件人电话" />
        <input class="input col-span-2" v-model="form.recipientAddress" placeholder="收件人地址" />
        <input class="input" v-model="form.recipientPostcode" placeholder="收件人邮编" />
        <input class="input" v-model.number="form.weightGrams" type="number" placeholder="重量(g)" />
        <select class="select" v-model="form.serviceType">
          <option value="">请选择服务类型</option>
          <option v-if="!serviceTypes.length" value="" disabled>正在加载服务类型...</option>
          <option v-for="item in serviceTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select>
        <input class="input" v-model.number="form.declaredValue" type="number" placeholder="申报价值(可选)" />
        <div class="col-span-2 flex gap-2">
          <button class="btn btn-primary" :disabled="submitting">{{ submitting ? '提交中...' : '提交收寄' }}</button>
          <button class="btn btn-secondary" type="button" @click="fillDemo">填写示例</button>
        </div>
      </form>
    </div>

    <div class="card p-5">
      <h3 class="card-title">提交结果</h3>
      <pre class="mt-4 overflow-auto whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const result = ref('');
const submitting = ref(false);
const mailTypes = ref([]);
const countries = ref([]);
const serviceTypes = ref([]);
const destCountryInput = ref('');
const form = reactive({
  mailTypeCode: '',
  mailScope: 'DOMESTIC',
  destCountryCode: '',
  senderFullName: '',
  senderPhone: '',
  senderIdType: 'NID',
  senderIdNumber: '',
  senderAddress: '',
  senderPostcode: '',
  recipientFullName: '',
  recipientPhone: '',
  recipientAddress: '',
  recipientPostcode: '',
  weightGrams: 120,
  declaredValue: null,
  serviceType: '',
});

const isInternational = computed(() => form.mailScope === 'INTERNATIONAL');

async function loadMailTypes() {
  try {
    mailTypes.value = await mailApi.listMailTypes(session.token);
    if (!form.mailTypeCode && mailTypes.value.length) {
      form.mailTypeCode = mailTypes.value[0].code;
    }
  } catch (error) {
    result.value = `邮件类型加载失败：${error.message}`;
  }
}

async function loadCountries() {
  try {
    countries.value = await mailApi.listCountries(session.token);
  } catch (error) {
    result.value = `寄达国加载失败：${error.message}`;
  }
}

async function loadServiceTypes() {
  try {
    serviceTypes.value = await mailApi.listServiceTypes(session.token);
    if (!form.serviceType && serviceTypes.value.length) {
      form.serviceType = serviceTypes.value[0].code;
    }
  } catch (error) {
    result.value = `服务类型加载失败：${error.message}`;
  }
}

async function refreshServiceTypes() {
  if (!isInternational.value || !form.destCountryCode) {
    await loadServiceTypes();
    return;
  }
  try {
    serviceTypes.value = await mailApi.listServiceTypesByCountry(form.destCountryCode, session.token);
    if (!serviceTypes.value.find((item) => item.code === form.serviceType)) {
      form.serviceType = serviceTypes.value[0]?.code || '';
    }
  } catch (error) {
    result.value = `联动服务类型加载失败：${error.message}`;
  }
}

watch(() => form.mailScope, async (value) => {
  if (value !== 'INTERNATIONAL') {
    form.destCountryCode = '';
    destCountryInput.value = '';
    await loadServiceTypes();
    return;
  }
  await refreshServiceTypes();
});

const filteredCountryHints = computed(() => {
  const keyword = destCountryInput.value.trim().toLowerCase();
  if (!keyword) {
    return countries.value;
  }
  return countries.value.filter((item) => {
    const name = String(item.name || '').toLowerCase();
    const code = String(item.code || '').toLowerCase();
    const englishName = String(item.englishName || '').toLowerCase();
    return name.includes(keyword) || code.includes(keyword) || englishName.includes(keyword);
  });
});

function countryHintValue(item) {
  return `${item.name} (${item.code})`;
}

function resolveCountryInput(value) {
  const keyword = String(value || '').trim().toLowerCase();
  if (!keyword) {
    form.destCountryCode = '';
    return null;
  }
  const matched = countries.value.find((item) => {
    const name = String(item.name || '').toLowerCase();
    const code = String(item.code || '').toLowerCase();
    const englishName = String(item.englishName || '').toLowerCase();
    return name === keyword || code === keyword || englishName === keyword;
  }) || countries.value.find((item) => {
    const name = String(item.name || '').toLowerCase();
    const code = String(item.code || '').toLowerCase();
    const englishName = String(item.englishName || '').toLowerCase();
    return name.includes(keyword) || code.includes(keyword) || englishName.includes(keyword);
  });
  if (!matched) {
    form.destCountryCode = '';
    return null;
  }
  form.destCountryCode = matched.code;
  destCountryInput.value = countryHintValue(matched);
  return matched;
}

function onDestCountryInput() {
  form.destCountryCode = '';
}

async function onDestCountryCommit() {
  const matched = resolveCountryInput(destCountryInput.value);
  if (matched) {
    await refreshServiceTypes();
  }
}

function fillDemo() {
  Object.assign(form, {
    mailTypeCode: 'R',
    mailScope: 'DOMESTIC',
    destCountryCode: '',
    destCountryInput: '',
    senderFullName: 'Demo Sender',
    senderPhone: '0900123456',
    senderAddress: 'A1 Plaza, Namoa',
    senderPostcode: '10001',
    senderIdNumber: 'NID0001',
    recipientFullName: 'Demo Recipient',
    recipientPhone: '0900654321',
    recipientAddress: 'B1 Central, Namoa',
    recipientPostcode: '10002',
    weightGrams: 120,
    serviceType: 'AIR',
    declaredValue: null,
  });
  destCountryInput.value = '';
}

async function submit() {
  submitting.value = true;
  try {
    const payload = await mailApi.create({
      ...form,
      originFacilityCode: session.user?.facilityCode || '',
      currentFacilityCode: session.user?.facilityCode || '',
      serviceType: form.serviceType,
    }, session.token);
    result.value = JSON.stringify(payload, null, 2);
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadMailTypes(), loadCountries(), loadServiceTypes()]);
});
</script>
