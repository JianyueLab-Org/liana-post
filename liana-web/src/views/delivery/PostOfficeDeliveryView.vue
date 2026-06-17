<template>
  <div class="grid gap-6 lg:grid-cols-2">
    <section class="card p-5">
      <h3 class="card-title">Post Office Delivery</h3>
      <p class="mt-1 text-sm text-gray-500">终投递与国际离开互换局最小实现。</p>

      <div class="mt-4 space-y-3">
        <input v-model="form.waybillNo" class="input" placeholder="邮件单号" />
        <select v-model="form.action" class="select">
          <option value="DELIVER">终投递</option>
          <option value="DEPART_EXCHANGE">国际离开互换局</option>
        </select>
        <input v-model="form.facilityCode" class="input" placeholder="机构编码（可选）" />
        <button class="btn btn-primary w-full" :disabled="loading || !form.waybillNo" @click="submit">
          {{ loading ? '提交中...' : '提交' }}
        </button>
      </div>
    </section>

    <section class="card p-5">
      <h3 class="card-title">结果</h3>
      <pre class="mt-4 whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { mailApi } from '../../lib/api';
import { getUpuBarcodeError, normalizeUpuBarcode } from '../../lib/upuBarcode';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const loading = ref(false);
const result = ref('');
const form = reactive({ waybillNo: '', facilityCode: session.user?.facilityCode || '', action: 'DELIVER' });

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
      ? await mailApi.deliverMail(waybillNo, form.facilityCode.trim(), session.token)
      : await mailApi.departExchangeMail(waybillNo, form.facilityCode.trim(), session.token);
    result.value = JSON.stringify(payload, null, 2);
  } catch (error) {
    result.value = error.message;
  } finally {
    loading.value = false;
  }
}
</script>
