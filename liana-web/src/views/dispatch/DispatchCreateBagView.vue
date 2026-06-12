<template>
  <div class="card p-5">
    <h3 class="card-title">建袋作业</h3>
    <p class="mt-2 text-sm text-gray-500">先选邮件类型，再勾选当前处于收寄状态的邮件，直接生成总包。</p>

    <form class="mt-4 space-y-4" @submit.prevent="submit">
      <select class="select w-full" v-model="form.mailTypeCode">
        <option value="">请选择邮件类型</option>
        <option v-if="!mailTypes.length" value="" disabled>正在加载邮件类型...</option>
        <option v-for="item in mailTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
      </select>

      <div class="rounded-xl border border-gray-200 bg-gray-50 p-4">
        <div class="flex items-center justify-between gap-3">
          <div>
            <div class="text-sm font-medium text-gray-800">候选邮件</div>
            <div class="text-xs text-gray-500">仅展示当前机构、所选邮件类型且处于收寄状态（CREATED）的邮件</div>
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
                <th>状态</th>
                <th>当前机构</th>
                <th>重量(g)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loadingCandidates">
                <td colspan="6" class="py-8 text-center text-sm text-gray-500">正在加载候选邮件...</td>
              </tr>
              <tr v-else-if="!candidateRows.length">
                <td colspan="6" class="py-8 text-center text-sm text-gray-500">暂无可建袋邮件</td>
              </tr>
              <tr v-for="item in candidateRows" :key="item.waybillNo">
                <td><input type="checkbox" :value="item.waybillNo" v-model="selectedMailNos" /></td>
                <td class="font-medium text-gray-800">{{ item.waybillNo }}</td>
                <td>{{ mailTypeLabel(item.mailTypeCode) }}</td>
                <td>{{ item.status }}</td>
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
const loadingCandidates = ref(false);
const mailTypes = ref([]);
const candidateRows = ref([]);
const selectedMailNos = ref([]);
const form = reactive({ mailTypeCode: '' });

const originFacilityCode = computed(() => session.user?.facilityCode || '');
const selectedCandidates = computed(() => candidateRows.value.filter((item) => selectedMailNos.value.includes(item.waybillNo)));
const selectedDestinationCode = computed(() => selectedCandidates.value[0]?.destFacilityCode || '');
const canSubmit = computed(() => Boolean(form.mailTypeCode) && selectedMailNos.value.length > 0 && !loadingCandidates.value && !submitting.value);
const allSelected = computed(() => candidateRows.value.length > 0 && selectedMailNos.value.length === candidateRows.value.length);

function mailTypeLabel(code) {
  return mailTypes.value.find((item) => item.code === code)?.name || code || '-';
}

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

async function loadCandidates() {
  if (!form.mailTypeCode) {
    candidateRows.value = [];
    selectedMailNos.value = [];
    return;
  }

  loadingCandidates.value = true;
  try {
    const candidates = await mailApi.dispatchCandidates({
      currentFacilityCode: originFacilityCode.value,
      mailTypeCode: form.mailTypeCode,
    }, session.token);
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

function toggleAll(checked) {
  selectedMailNos.value = checked ? candidateRows.value.map((item) => item.waybillNo) : [];
}

watch(() => form.mailTypeCode, () => {
  selectedMailNos.value = [];
  loadCandidates();
});

async function submit() {
  if (!canSubmit.value) {
    result.value = '请选择邮件后再创建总包。';
    return;
  }

  submitting.value = true;
  try {
    const payload = await dispatchApi.createBag({
      originFacilityCode: originFacilityCode.value,
      destinationFacilityCode: selectedDestinationCode.value,
      mailTypeCode: form.mailTypeCode,
      mailNoList: selectedMailNos.value,
    }, session.token);

    result.value = JSON.stringify(payload, null, 2);
    await loadCandidates();
  } catch (error) {
    result.value = `创建总包失败：${error.message}`;
  } finally {
    submitting.value = false;
  }
}

onMounted(loadMailTypes);
</script>