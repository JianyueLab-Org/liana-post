<template>
  <div class="space-y-6 print-page">
    <div class="card p-5 no-print">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="card-title">打印投递单</h3>
          <p class="mt-1 text-sm text-gray-500">展示当前机构待投邮件，可直接打印给投递员。</p>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-secondary" @click="load">刷新</button>
          <button class="btn btn-primary" :disabled="loading || !rows.length" @click="printPage">打印</button>
        </div>
      </div>
    </div>

    <div class="paper">
      <div class="paper-header">
        <div>
          <h1>Liana Delivery Sheet</h1>
          <p>机构：{{ currentFacilityCode || '-' }}</p>
        </div>
        <div>
          <p>生成时间：{{ generatedAt }}</p>
          <p>数量：{{ rows.length }}</p>
        </div>
      </div>

      <div class="grid">
        <div v-for="item in rows" :key="item.waybillNo" class="mail-card">
          <div class="mail-head">
            <strong>{{ item.waybillNo }}</strong>
            <span>{{ item.status }}</span>
          </div>
          <div class="mail-meta">
            <div>收件人：{{ item.recipientFullName || '-' }}</div>
            <div>寄件人：{{ item.senderFullName || '-' }}</div>
            <div>目的地：{{ item.destFacilityCode || item.destinationNode || '-' }}</div>
            <div>总包号：{{ item.packageId || item.bagNo || '-' }}</div>
          </div>
          <div class="barcode">{{ item.waybillNo }}</div>
        </div>
      </div>
    </div>

    <div class="no-print card p-5">
      <pre class="whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const rows = ref([]);
const loading = ref(false);
const result = ref('');
const generatedAt = computed(() => new Date().toLocaleString());
const currentFacilityCode = computed(() => session.user?.facilityCode || '');

async function load() {
  loading.value = true;
  try {
    rows.value = await mailApi.listPendingDeliveryMails(currentFacilityCode.value, session.token);
    result.value = `loaded ${rows.value.length} pending mail(s)`;
  } catch (error) {
    result.value = error.message;
  } finally {
    loading.value = false;
  }
}

function printPage() {
  window.print();
}

onMounted(load);
</script>

<style scoped>
.paper {
  background: #fff;
  color: #111827;
  padding: 24px;
  border: 1px solid #e5e7eb;
}

.paper-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 12px;
}

.paper-header h1 {
  font-size: 24px;
  font-weight: 800;
}

.grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mail-card {
  border: 1px dashed #9ca3af;
  border-radius: 14px;
  padding: 12px;
  break-inside: avoid;
}

.mail-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.mail-meta {
  display: grid;
  gap: 4px;
  font-size: 12px;
}

.barcode {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid #111827;
  font-family: monospace;
  font-size: 18px;
  letter-spacing: 1px;
  text-align: center;
}

@media print {
  .no-print {
    display: none !important;
  }

  .paper {
    border: none;
    padding: 0;
  }

  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
