<template>
  <div class="space-y-6">
    <section class="card hero-panel p-5"><h3 class="card-title">邮资报价</h3><p class="mt-2 text-sm muted">选择产品、目的地和实际重量，查询基本邮资及附加费。</p></section>
    <p v-if="error" role="alert" class="rounded-lg bg-red-50 p-4 text-red-800">{{ error }}</p>
    <section class="card p-5">
      <div class="flex items-center justify-between gap-3"><h3 class="card-title">当前资费</h3><button class="btn btn-secondary" :disabled="busy" @click="loadTariff">刷新资费</button></div>
      <p v-if="tariff" class="mt-3 text-sm muted">{{ tariff.versionId }} · {{ formatTime(tariff.effectiveFrom) }} 生效 · {{ tariff.definition.currency }}</p>
      <p v-else class="mt-3 text-sm muted">{{ busy ? '加载中…' : '暂无可用的生效资费，可以查询已有报价。' }}</p>
      <form v-if="tariff" class="mt-5 grid gap-4 md:grid-cols-2" @submit.prevent="createQuote">
        <label>邮件产品<select v-model="form.product" class="select mt-1" required :disabled="busy"><option v-for="p in products" :key="p.code" :value="p.code">{{ p.name }}（{{ p.code }}）</option></select></label>
        <AdditionalServicesInput v-model="form.additionalServices" :options="additionalServiceOptions(selectedRule, form.product)" :disabled="busy" />
        <label>寄递范围<select v-model="form.scope" class="select mt-1" :disabled="busy"><option value="DOMESTIC">国内</option><option value="INTERNATIONAL">国际</option></select></label>
        <label>目的地代码<input v-model="form.destinationCountry" class="input mt-1" list="postage-countries" maxlength="2" pattern="[A-Z]{2}" required :disabled="busy || form.scope === 'DOMESTIC'" @input="form.destinationCountry = form.destinationCountry.toUpperCase()" /><datalist id="postage-countries"><option v-for="code in countryCodes" :key="code" :value="code" /></datalist></label>
        <label>运输方式<select v-model="form.mode" class="select mt-1" required :disabled="busy"><option v-for="mode in modes" :key="mode" :value="mode">{{ mode }}</option></select></label>
        <label>实际重量（g）<input v-model.number="form.weightGrams" class="input mt-1" type="number" min="1" :max="selectedRule?.maxWeightGrams || 1000000" step="1" required :disabled="busy" /></label>
        <div class="self-end"><button class="btn btn-primary" :disabled="busy || !selectedRule">{{ busy ? '处理中…' : '计算并保存报价' }}</button></div>
      </form>
      <p v-if="selectedRule" class="mt-4 text-sm muted">分区 {{ zone }} · 重量上限 {{ selectedRule.maxWeightGrams }} g · {{ selectedRule.algorithm === 'TIERS' ? '按重量档计价' : `首重 ${selectedRule.firstWeightGrams} g，续重 ${selectedRule.stepWeightGrams} g` }}</p>
      <p v-if="tariff && !selectedRule" class="mt-4 text-sm text-amber-800">此组合暂无资费，请更换产品、范围或运输方式。</p>
      <p class="mt-3 text-sm muted">国际可输入尚未录入系统的两位国家代码，未配置的国家使用默认分区。有报价不代表已开放收寄。</p>
      <details v-if="tariff" class="mt-5"><summary class="cursor-pointer text-sm font-bold text-cyan-800">查看当前价目及国家分区</summary><div class="mt-4"><TariffDefinitionEditor v-model="tariff.definition" readonly /></div></details>
    </section>
    <section class="card p-5"><h3 class="card-title">查询已有报价</h3><form class="mt-4 flex flex-wrap gap-3" @submit.prevent="findQuote"><input v-model.trim="quoteId" class="input min-w-0 flex-1" aria-label="报价编号" placeholder="输入完整报价编号" required /><button class="btn btn-secondary" :disabled="busy">查询并检查有效性</button></form></section>
    <div v-if="view" class="space-y-3"><button class="btn btn-secondary" :disabled="busy" @click="recheck">重新检查此报价有效性</button><PostageQuoteDetail :snapshot="view.snapshot" :valid="view.valid && now < Date.parse(view.snapshot.expiresAt)" /></div>
  </div>
</template>
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { postageApi, formatTime, letterProducts, letterRuleProduct, additionalServiceOptions } from '../../lib/postage';
import AdditionalServicesInput from '../../components/AdditionalServicesInput.vue';
import { useSessionStore } from '../../stores/session';
import PostageQuoteDetail from '../../components/PostageQuoteDetail.vue';
import TariffDefinitionEditor from '../../components/TariffDefinitionEditor.vue';
const session = useSessionStore();
const tariff = ref(null), busy = ref(false), error = ref(''), view = ref(null), quoteId = ref(''), now = ref(Date.now());
const form = reactive({ product: '', scope: 'DOMESTIC', mode: 'AIR', destinationCountry: 'LN', weightGrams: 1, additionalServices: [] });
const products = computed(() => letterProducts(tariff.value?.definition.products));
const countryCodes = computed(() => Object.keys(tariff.value?.definition.countryZones || {}).sort());
const zone = computed(() => form.scope === 'DOMESTIC' ? 'DOM' : tariff.value?.definition.countryZones[form.destinationCountry] || tariff.value?.definition.fallbackZone);
const rules = computed(() => (tariff.value?.definition.rules || []).filter(r => r.product === letterRuleProduct(tariff.value.definition, form.product) && r.scope === form.scope && r.zone === zone.value));
const modes = computed(() => [...new Set(rules.value.map(r => r.mode))]);
const selectedRule = computed(() => rules.value.find(r => r.mode === form.mode));
watch(modes, (items) => { if (!items.includes(form.mode)) form.mode = items[0] || ''; });
watch(() => form.scope, (scope) => { form.destinationCountry = scope === 'DOMESTIC' ? (tariff.value?.definition.domesticCountry || 'LN') : ''; });
async function run(action) { busy.value = true; error.value = ''; try { await action(); } catch (e) { error.value = e.message; } finally { busy.value = false; } }
async function loadTariff() {
  await run(async () => {
    tariff.value = null;
    tariff.value = await postageApi.current(session.token);
    if (!products.value.some(p => p.code === form.product)) form.product = products.value[0].code;
    if (view.value) view.value = await postageApi.findQuote(view.value.snapshot.quoteId, session.token);
  });
}
async function createQuote() {
  await run(async () => {
    if (!Number.isInteger(form.weightGrams) || form.weightGrams <= 0) throw new Error('重量必须为正整数克');
    view.value = null;
    const snapshot = await postageApi.quote({ ...form }, session.token);
    quoteId.value = snapshot.quoteId;
    view.value = await postageApi.findQuote(snapshot.quoteId, session.token);
  });
}
async function findQuote() { await run(async () => { view.value = null; view.value = await postageApi.findQuote(quoteId.value, session.token); }); }
async function recheck() { const id = view.value.snapshot.quoteId; await run(async () => { view.value = null; view.value = await postageApi.findQuote(id, session.token); }); }
let timer;
onMounted(() => { loadTariff(); timer = setInterval(() => { now.value = Date.now(); }, 1000); });
onUnmounted(() => clearInterval(timer));
</script>
