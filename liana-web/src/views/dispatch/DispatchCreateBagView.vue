<template>
  <div class="card p-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h3 class="card-title">建袋作业</h3>
        <p class="mt-2 text-sm text-gray-500">按邮件类型和国内/国际范围建袋，国际邮件需按寄达国分别成袋。</p>
      </div>
      <button class="btn btn-secondary" type="button" :disabled="loadingSummary" @click="refreshAll">
        {{ loadingSummary ? '刷新中...' : '刷新' }}
      </button>
    </div>

    <div class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      <button
        v-for="group in candidateGroups"
        :key="group.key"
        type="button"
        class="rounded-lg border p-4 text-left transition"
        :class="selectedGroupKey === group.key ? 'border-blue-500 bg-blue-50' : 'border-gray-200 bg-white hover:border-blue-300'"
        @click="selectGroup(group)"
      >
        <div class="flex items-center justify-between gap-3">
          <div>
            <p class="text-sm font-semibold text-gray-900">{{ mailTypeLabel(group.mailTypeCode) }}</p>
            <p class="mt-1 text-xs text-gray-500">{{ scopeLabel(group.mailScope) }}{{ group.destCountryCode ? ` / ${group.destCountryCode}` : '' }}</p>
          </div>
          <span class="rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-700">{{ group.count }}</span>
        </div>
        <p class="mt-2 text-xs text-gray-500">待发重量 {{ group.weightGrams }}g</p>
      </button>
      <div v-if="!loadingSummary && !candidateGroups.length" class="rounded-lg border border-dashed border-gray-300 p-4 text-sm text-gray-400">
        当前机构暂无待建袋邮件
      </div>
    </div>

    <form class="mt-4 space-y-4" @submit.prevent="submit">
      <div class="grid gap-3 md:grid-cols-3">
        <select class="select w-full" v-model="form.mailTypeCode">
          <option value="">请选择邮件类型</option>
          <option v-if="!mailTypes.length" value="" disabled>正在加载邮件类型...</option>
          <option v-for="item in mailTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select>

        <select class="select w-full" v-model="form.mailScope">
          <option value="DOMESTIC">国内邮件</option>
          <option value="INTERNATIONAL">国际邮件</option>
        </select>

        <select v-if="form.mailScope === 'INTERNATIONAL'" class="select w-full" v-model="form.destCountryCode">
          <option value="">请选择寄达国</option>
          <option v-for="item in countryOptions" :key="item.code" :value="item.code">
            {{ item.code }}（{{ item.count }} 封）
          </option>
        </select>
      </div>

      <div class="rounded-lg border border-gray-200 bg-gray-50 p-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-sm font-medium text-gray-800">候选邮件</div>
            <div class="text-xs text-gray-500">仅展示当前机构、所选邮件类型、所选国内/国际范围且处于收寄状态的邮件</div>
          </div>
          <div class="text-sm text-gray-500">已选 {{ selectedMailNos.length }} 封</div>
        </div>

        <div class="mt-4 overflow-hidden rounded-lg border border-gray-200 bg-white">
          <table class="table">
            <thead>
              <tr>
                <th class="w-16">
                  <input
                    type="checkbox"
                    :checked="allSelected"
                    :disabled="!candidateRows.length"
                    @change="toggleAll($event.target.checked)"
                  />
                </th>
                <th>邮件号</th>
                <th>邮件类型</th>
                <th>范围</th>
                <th>寄达国</th>
                <th>当前机构</th>
                <th>重量(g)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loadingCandidates">
                <td colspan="7" class="py-8 text-center text-sm text-gray-500">正在加载候选邮件...</td>
              </tr>
              <tr v-else-if="!candidateRows.length">
                <td colspan="7" class="py-8 text-center text-sm text-gray-500">暂无可建袋邮件</td>
              </tr>
              <tr v-for="item in candidateRows" :key="item.waybillNo">
                <td><input type="checkbox" :value="item.waybillNo" v-model="selectedMailNos" /></td>
                <td class="font-medium text-gray-800">{{ item.waybillNo }}</td>
                <td>{{ mailTypeLabel(item.mailTypeCode) }}</td>
                <td>{{ scopeLabel(normalizeScope(item)) }}</td>
                <td>{{ normalizeScope(item) === 'INTERNATIONAL' ? normalizeCountry(item) : '-' }}</td>
                <td>{{ item.currentFacilityCode }}</td>
                <td>{{ item.weightGrams ?? '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="flex gap-2">
        <button class="btn btn-primary" type="submit" :disabled="submitting || !canSubmit">
          {{ submitting ? '创建中...' : '创建总包' }}
        </button>
      </div>
    </form>

    <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { dispatchApi, mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const result = ref('');
const submitting = ref(false);
const loadingSummary = ref(false);
const loadingCandidates = ref(false);
const mailTypes = ref([]);
const allCandidates = ref([]);
const candidateRows = ref([]);
const selectedMailNos = ref([]);
const form = reactive({
  mailTypeCode: '',
  mailScope: 'DOMESTIC',
  destCountryCode: '',
});

const originFacilityCode = computed(() => session.user?.facilityCode || '');
const selectedCandidates = computed(() => candidateRows.value.filter((item) => selectedMailNos.value.includes(item.waybillNo)));
const selectedDestinationCode = computed(() => selectedCandidates.value[0]?.destFacilityCode || '');
const selectedGroupKey = computed(() => groupKey(form.mailTypeCode, form.mailScope, form.mailScope === 'INTERNATIONAL' ? form.destCountryCode : ''));
const canLoadDetails = computed(() => Boolean(form.mailTypeCode) && (form.mailScope === 'DOMESTIC' || Boolean(form.destCountryCode)));
const canSubmit = computed(() => canLoadDetails.value && selectedMailNos.value.length > 0 && !loadingCandidates.value && !submitting.value);
const allSelected = computed(() => candidateRows.value.length > 0 && selectedMailNos.value.length === candidateRows.value.length);

const candidateGroups = computed(() => {
  const groups = new Map();
  for (const item of allCandidates.value) {
    if (item.status !== 'CREATED') continue;
    const scope = normalizeScope(item);
    const country = scope === 'INTERNATIONAL' ? normalizeCountry(item) : '';
    const key = groupKey(item.mailTypeCode, scope, country);
    const current = groups.get(key) || {
      key,
      mailTypeCode: item.mailTypeCode,
      mailScope: scope,
      destCountryCode: country,
      count: 0,
      weightGrams: 0,
    };
    current.count += 1;
    current.weightGrams += Number(item.weightGrams || 0);
    groups.set(key, current);
  }
  return Array.from(groups.values()).sort((a, b) => a.key.localeCompare(b.key));
});

const countryOptions = computed(() => candidateGroups.value
  .filter((item) => item.mailTypeCode === form.mailTypeCode && item.mailScope === 'INTERNATIONAL')
  .map((item) => ({ code: item.destCountryCode, count: item.count })));

function groupKey(mailTypeCode, mailScope, destCountryCode) {
  return `${mailTypeCode || ''}|${mailScope || ''}|${destCountryCode || ''}`;
}

function normalizeScope(item) {
  if (item.mailScope) return item.mailScope.toUpperCase();
  const country = normalizeCountry(item);
  return country && country !== 'LN' ? 'INTERNATIONAL' : 'DOMESTIC';
}

function normalizeCountry(item) {
  return (item.destCountryCode || '').trim().toUpperCase();
}

function scopeLabel(scope) {
  return scope === 'INTERNATIONAL' ? '国际' : '国内';
}

function mailTypeLabel(code) {
  return mailTypes.value.find((item) => item.code === code)?.name || code || '-';
}

function selectGroup(group) {
  form.mailTypeCode = group.mailTypeCode;
  form.mailScope = group.mailScope;
  form.destCountryCode = group.mailScope === 'INTERNATIONAL' ? group.destCountryCode : '';
}

async function loadMailTypes() {
  mailTypes.value = await mailApi.listMailTypes(session.token);
}

async function loadSummary() {
  loadingSummary.value = true;
  try {
    allCandidates.value = await mailApi.dispatchCandidates({
      currentFacilityCode: originFacilityCode.value,
    }, session.token);
    if (!candidateGroups.value.find((item) => item.key === selectedGroupKey.value) && candidateGroups.value.length) {
      selectGroup(candidateGroups.value[0]);
    }
  } catch (error) {
    allCandidates.value = [];
    result.value = `候选邮件汇总加载失败：${error.message}`;
  } finally {
    loadingSummary.value = false;
  }
}

async function loadCandidates() {
  if (!canLoadDetails.value) {
    candidateRows.value = [];
    selectedMailNos.value = [];
    return;
  }

  loadingCandidates.value = true;
  try {
    const request = {
      currentFacilityCode: originFacilityCode.value,
      mailTypeCode: form.mailTypeCode,
      mailScope: form.mailScope,
    };
    if (form.mailScope === 'INTERNATIONAL') {
      request.destCountryCode = form.destCountryCode;
    }
    const candidates = await mailApi.dispatchCandidates(request, session.token);
    candidateRows.value = candidates.filter((item) => item.status === 'CREATED');
    selectedMailNos.value = [];
    result.value = candidateRows.value.length ? `已加载 ${candidateRows.value.length} 封候选邮件` : '暂无可建袋邮件';
  } catch (error) {
    candidateRows.value = [];
    selectedMailNos.value = [];
    result.value = `候选邮件加载失败：${error.message}`;
  } finally {
    loadingCandidates.value = false;
  }
}

async function refreshAll() {
  await loadSummary();
  await loadCandidates();
}

function toggleAll(checked) {
  selectedMailNos.value = checked ? candidateRows.value.map((item) => item.waybillNo) : [];
}

watch(() => form.mailScope, (scope) => {
  if (scope === 'DOMESTIC') {
    form.destCountryCode = '';
  } else if (!form.destCountryCode && countryOptions.value.length) {
    form.destCountryCode = countryOptions.value[0].code;
  }
});

watch(() => [form.mailTypeCode, form.mailScope, form.destCountryCode], () => {
  selectedMailNos.value = [];
  loadCandidates();
});

async function submit() {
  if (!canSubmit.value) {
    result.value = '请选择同一邮件类型、同一范围的邮件后再创建总包。';
    return;
  }

  submitting.value = true;
  try {
    const request = {
      originFacilityCode: originFacilityCode.value,
      destinationFacilityCode: selectedDestinationCode.value,
      mailTypeCode: form.mailTypeCode,
      mailScope: form.mailScope,
      mailNoList: selectedMailNos.value,
    };
    if (form.mailScope === 'INTERNATIONAL') {
      request.destCountryCode = form.destCountryCode;
    }
    const payload = await dispatchApi.createBag(request, session.token);

    result.value = JSON.stringify(payload, null, 2);
    await refreshAll();
  } catch (error) {
    result.value = `创建总包失败：${error.message}`;
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await loadMailTypes();
  await refreshAll();
});
</script>
