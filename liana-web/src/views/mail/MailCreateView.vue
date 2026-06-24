<template>
  <div class="space-y-6">
    <div class="card p-5 hero-panel">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">收寄录入</h3>
          <p class="mt-1 text-sm text-gray-500">录入寄件人、收件人、邮件类型和重量信息，提交后生成邮件台账。</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button type="button" class="btn btn-secondary" @click="fillExample('DOMESTIC')">国内示例</button>
          <button type="button" class="btn btn-secondary" @click="fillExample('INTERNATIONAL')">国际示例</button>
        </div>
      </div>

      <form class="mt-4 grid-two" @submit.prevent="submit">
        <select class="select col-span-2" v-model="form.mailTypeCode">
          <option value="">请选择邮件类型</option>
          <option v-for="item in mailTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select>

        <select class="select col-span-2" v-model="form.mailScope">
          <option value="DOMESTIC">国内邮件</option>
          <option value="INTERNATIONAL">国际邮件</option>
        </select>

        <select v-if="isInternational" class="select col-span-2" v-model="form.destCountryCode">
          <option value="">请选择寄达国家</option>
          <option v-for="item in countries" :key="item.code" :value="item.code">{{ item.name }} ({{ item.code }})</option>
        </select>

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

        <select v-if="isInternational" class="select" v-model="form.serviceType">
          <option value="">请选择服务类型</option>
          <option v-if="loadingServiceTypes" value="" disabled>加载中...</option>
          <option v-for="item in serviceTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select>

        <input class="input" v-model.number="form.declaredValue" type="number" placeholder="申报价值（可选）" />

        <div class="col-span-2 flex gap-2">
          <button class="btn btn-primary" :disabled="submitting">
            {{ submitting ? '提交中...' : '提交收寄' }}
          </button>
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
const loadingServiceTypes = ref(false);
const mailTypes = ref([]);
const countries = ref([]);
const serviceTypes = ref([]);
const serviceTypesLoaded = ref(false);
const DEFAULT_DOMESTIC_SERVICE_TYPE = 'AIR';

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

function chooseCode(items, preferredCodes, fallback) {
  for (const code of preferredCodes) {
    if (items.find((item) => item.code === code)) {
      return code;
    }
  }
  return items[0]?.code || fallback;
}

async function fillExample(scope) {
  form.mailScope = scope;

  if (scope === 'INTERNATIONAL') {
    await loadServiceTypes();
  }

  const isDomesticExample = scope === 'DOMESTIC';
  Object.assign(form, {
    mailTypeCode: chooseCode(mailTypes.value, isDomesticExample ? ['C', 'R', 'E'] : ['E', 'R', 'C'], isDomesticExample ? 'C' : 'E'),
    mailScope: scope,
    destCountryCode: isDomesticExample ? '' : chooseCode(countries.value.filter((item) => item.code !== 'LN'), ['JP', 'US', 'HK'], 'JP'),
    senderFullName: isDomesticExample ? '林安' : '陈嘉明',
    senderPhone: isDomesticExample ? '0900123456' : '0900888666',
    senderIdType: 'NID',
    senderIdNumber: isDomesticExample ? 'LN110101199001010018' : 'LN440305198806060022',
    senderAddress: isDomesticExample ? '利亚纳纳莫阿市中央大道 18 号' : '利亚纳纳莫阿市海港路 6 号',
    senderPostcode: isDomesticExample ? '10001' : '10003',
    recipientFullName: isDomesticExample ? '周明' : 'Yuki Tanaka',
    recipientPhone: isDomesticExample ? '0900654321' : '+81-3-1234-5678',
    recipientAddress: isDomesticExample ? '利亚纳塔维里市邮政街 12 号' : '1-2-3 Marunouchi, Chiyoda-ku, Tokyo',
    recipientPostcode: isDomesticExample ? '10002' : '1000005',
    weightGrams: isDomesticExample ? 320 : 480,
    declaredValue: isDomesticExample ? null : 120,
    serviceType: isDomesticExample ? '' : chooseCode(serviceTypes.value, ['AIR', 'SAL', 'SEA'], 'AIR'),
  });
  result.value = `已填充${isDomesticExample ? '国内' : '国际'}示例，可直接提交收寄。`;
}

async function loadMailTypes() {
  mailTypes.value = await mailApi.listMailTypes(session.token);
  if (!form.mailTypeCode && mailTypes.value.length) {
    form.mailTypeCode = mailTypes.value[0].code;
  }
}

async function loadCountries() {
  countries.value = await mailApi.listCountries(session.token);
}

async function loadServiceTypes() {
  if (!isInternational.value || serviceTypesLoaded.value) {
    return;
  }

  loadingServiceTypes.value = true;
  try {
    serviceTypes.value = await mailApi.listServiceTypes(session.token);
    serviceTypesLoaded.value = true;
    if (!serviceTypes.value.find((item) => item.code === form.serviceType)) {
      form.serviceType = serviceTypes.value[0]?.code || '';
    }
  } finally {
    loadingServiceTypes.value = false;
  }
}

watch(() => form.mailScope, async (value) => {
  form.destCountryCode = '';
  if (value === 'INTERNATIONAL') {
    await loadServiceTypes();
  }
});

async function submit() {
  submitting.value = true;
  try {
    if (!form.mailTypeCode || !form.senderFullName || !form.senderPhone || !form.senderAddress || !form.recipientFullName || !form.recipientPhone || !form.recipientAddress) {
      result.value = '请补全收寄必填信息';
      return;
    }
    if (isInternational.value && !form.destCountryCode) {
      result.value = '国际邮件必须选择寄达国家';
      return;
    }

    if (isInternational.value && !form.serviceType) {
      result.value = '国际邮件必须选择服务类型';
      return;
    }

    const requestPayload = {
      mailTypeCode: form.mailTypeCode,
      mailScope: form.mailScope,
      senderFullName: form.senderFullName,
      senderPhone: form.senderPhone,
      senderIdType: form.senderIdType,
      senderIdNumber: form.senderIdNumber,
      senderAddress: form.senderAddress,
      senderPostcode: form.senderPostcode,
      recipientFullName: form.recipientFullName,
      recipientPhone: form.recipientPhone,
      recipientAddress: form.recipientAddress,
      recipientPostcode: form.recipientPostcode,
      weightGrams: form.weightGrams,
      declaredValue: form.declaredValue,
      serviceType: isInternational.value ? form.serviceType : DEFAULT_DOMESTIC_SERVICE_TYPE,
      originFacilityCode: session.user?.facilityCode || '',
      currentFacilityCode: session.user?.facilityCode || '',
    };

    if (isInternational.value) {
      requestPayload.destCountryCode = form.destCountryCode;
    }

    const response = await mailApi.create(requestPayload, session.token);
    result.value = JSON.stringify(response, null, 2);
  } catch (error) {
    result.value = error?.message || String(error);
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadMailTypes(), loadCountries()]);
});
</script>
