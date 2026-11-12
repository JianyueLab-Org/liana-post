<template>
  <div class="space-y-6">
    <section class="card hero-panel p-5"><h3 class="card-title">数码邮票发行规则</h3><p class="mt-2 text-sm muted">配置工作人员可售面额及有效天数。调整后用于新售卖，已创建售卖和已发行邮票保留原规则。</p></section>
    <p v-if="error" role="alert" class="rounded-lg bg-red-50 p-4 text-red-800">{{ error }}</p><p v-if="notice" role="status" class="rounded-lg bg-emerald-50 p-4 text-emerald-800">{{ notice }}</p>
    <section v-if="policy" class="card p-5"><p class="text-sm muted">LNR · 修订 {{ policy.revision }} · {{ policy.updatedBy }} 于 {{ policy.updatedAt && Date.parse(policy.updatedAt) ? formatTime(policy.updatedAt) : '初始导入' }} 更新</p>
      <form class="mt-4 space-y-4" @submit.prevent="save"><fieldset :disabled="busy" class="grid gap-4 md:grid-cols-3"><label>最小面额<input v-model="form.minFaceValue" class="input mt-1" type="number" min="0.01" max="999999.99" step="0.01" required /></label><label>最大面额<input v-model="form.maxFaceValue" class="input mt-1" type="number" min="0.01" max="999999.99" step="0.01" required /></label><label>有效天数<input v-model.number="form.validityDays" class="input mt-1" type="number" min="1" max="36500" step="1" required /></label></fieldset><label class="flex items-center gap-2 text-sm"><input v-model="confirmed" type="checkbox" required :disabled="busy" />确认将此规则用于新售卖</label><div class="flex gap-3"><button class="btn btn-primary" :disabled="busy || !confirmed">保存发行规则</button><button type="button" class="btn btn-secondary" :disabled="busy" @click="load">重新加载</button></div></form>
    </section>
    <section class="card p-5"><h3 class="card-title">规则调整记录</h3><p v-if="!audit.length" class="mt-3 text-sm muted">暂无调整记录</p><div v-for="event in audit" :key="event.eventId" class="mt-4 rounded border p-4"><p class="text-sm">{{ event.actor }} · {{ formatTime(event.occurredAt) }}</p><pre class="mt-2 overflow-auto whitespace-pre-wrap text-sm muted">{{ event.detail }}</pre></div></section>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useSessionStore } from '../../stores/session';
import { formatTime } from '../../lib/postage';
import { retailApi } from '../../lib/postageRetail';
const session=useSessionStore();
const policy=ref(null), audit=ref([]), busy=ref(false), error=ref(''), notice=ref(''), confirmed=ref(false);
const form=reactive({minFaceValue:'0.01',maxFaceValue:'100.00',validityDays:365});
function install(value) { policy.value=value; Object.assign(form,{minFaceValue:value.minFaceValue,maxFaceValue:value.maxFaceValue,validityDays:value.validityDays}); confirmed.value=false; }
async function run(action) { if (busy.value) return; busy.value=true; error.value=''; notice.value=''; try { await action(); } catch(e) { error.value=e.message; } finally { busy.value=false; } }
async function load() { await run(async()=>{ install(await retailApi.policy(session.token)); audit.value=await retailApi.policyAudit(session.token); }); }
async function save() { await run(async()=>{ install(await retailApi.updatePolicy({revision:policy.value.revision,...form},session.token)); audit.value=await retailApi.policyAudit(session.token); notice.value='发行规则已保存，新售卖立即使用。'; }); }
onMounted(load);
</script>
