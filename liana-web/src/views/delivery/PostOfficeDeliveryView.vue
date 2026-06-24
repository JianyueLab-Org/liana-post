<template>
  <div class="space-y-6">
    <section class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">投递工作台</h3>
          <p class="mt-1 text-sm text-gray-500">当前机构：{{ currentFacilityCode || '-' }}</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="btn btn-secondary" :disabled="loading" @click="loadPending">刷新待投</button>
          <button class="btn btn-secondary" @click="router.push('/delivery/print')">打印投递单</button>
        </div>
      </div>
    </section>

    <div class="grid gap-6 lg:grid-cols-[minmax(0,1.2fr)_minmax(360px,0.8fr)]">
      <section class="card p-5">
        <div class="flex items-center justify-between gap-3">
          <h4 class="text-base font-semibold text-gray-900">待投邮件</h4>
          <span class="badge badge-info">{{ pendingRows.length }} 件</span>
        </div>
        <div class="mt-4 overflow-hidden rounded-lg border border-gray-200">
          <table class="table">
            <thead>
              <tr>
                <th>邮件单号</th>
                <th>收件人</th>
                <th>总包/邮袋</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in pendingRows"
                :key="item.waybillNo"
                class="cursor-pointer hover:bg-blue-50"
                @click="selectMail(item)"
              >
                <td class="font-medium text-gray-900">{{ item.waybillNo }}</td>
                <td>{{ item.recipientFullName || '-' }}</td>
                <td>{{ item.packageId || item.bagNo || '-' }}</td>
                <td><span class="badge badge-warning">{{ formatStatus(item.status) }}</span></td>
              </tr>
              <tr v-if="!pendingRows.length">
                <td colspan="4" class="text-sm text-gray-500">当前机构暂无已开拆未投递邮件</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="card p-5">
        <h4 class="text-base font-semibold text-gray-900">操作面板</h4>
        <div class="mt-4 space-y-3">
          <input v-model="form.waybillNo" class="input" placeholder="扫描或选择邮件单号" />
          <select v-model="form.action" class="select">
            <option value="DELIVER">终投递</option>
            <option value="DEPART_EXCHANGE">国际离开互换局</option>
          </select>
          <div class="rounded-lg bg-slate-50 px-4 py-3 text-sm text-gray-600">
            本次操作将使用 JWT 中的机构编码：<span class="font-semibold text-gray-900">{{ currentFacilityCode || '-' }}</span>
          </div>
          <button class="btn btn-primary w-full" :disabled="loading || !form.waybillNo" @click="submit">
            {{ loading ? '提交中...' : '提交投递结果' }}
          </button>
          <pre class="max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm text-gray-700">{{ result }}</pre>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { mailApi } from '../../lib/api';
import { getUpuBarcodeError, normalizeUpuBarcode } from '../../lib/upuBarcode';
import { useSessionStore } from '../../stores/session';

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const result = ref('');
const pendingRows = ref([]);
const currentFacilityCode = computed(() => session.user?.facilityCode || '');
const form = reactive({ waybillNo: '', action: 'DELIVER' });

function selectMail(item) {
  form.waybillNo = item.waybillNo || '';
}

function isPendingDeliveryMail(item) {
  if (!item || !['READY_FOR_DELIVERY', 'ARRIVED'].includes(item.status)) {
    return false;
  }
  const facilityCode = (currentFacilityCode.value || '').trim().toUpperCase();
  const rowFacilityCode = (item.currentFacilityCode || '').trim().toUpperCase();
  return !facilityCode || rowFacilityCode === facilityCode;
}

function formatStatus(status) {
  return ['READY_FOR_DELIVERY', 'ARRIVED'].includes(status) ? '待投递' : status;
}

async function loadPending() {
  loading.value = true;
  try {
    const rows = await mailApi.listPendingDeliveryMails(currentFacilityCode.value, session.token);
    pendingRows.value = rows.filter(isPendingDeliveryMail);
  } catch (error) {
    result.value = error.message;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  loading.value = true;
  try {
    const waybillNo = normalizeUpuBarcode(form.waybillNo);
    const error = getUpuBarcodeError(waybillNo, '邮件单号');
    if (error) {
      result.value = error;
      return;
    }
    form.waybillNo = waybillNo;
    const payload = form.action === 'DELIVER'
      ? await mailApi.deliverMail(waybillNo, currentFacilityCode.value, session.token)
      : await mailApi.departExchangeMail(waybillNo, currentFacilityCode.value, session.token);
    result.value = JSON.stringify(payload, null, 2);
    await loadPending();
  } catch (error) {
    result.value = error.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadPending);
</script>
