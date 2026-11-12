<template>
  <div class="space-y-5">
    <label class="block">选择查看或编辑的规则<select v-model.number="selectedIndex" class="select mt-1"><option v-for="(r, index) in definition.rules" :key="index" :value="index">{{ r.id || '新规则' }} · {{ r.product }} / {{ r.scope === 'DOMESTIC' ? '国内' : '国际' }} / {{ r.mode }} / {{ r.zone }}</option></select></label>
    <fieldset :disabled="readonly" class="space-y-5">
      <div class="grid gap-4 md:grid-cols-3"><label>币种<input v-model="definition.currency" class="input mt-1" required /></label><label>国内国家代码<input v-model="definition.domesticCountry" class="input mt-1" maxlength="2" required /></label><label>默认国际分区<input v-model="definition.fallbackZone" class="input mt-1" pattern="[A-Z0-9_-]{1,64}" required /></label></div>
      <section><h4 class="font-bold">产品目录</h4><p class="mt-1 text-sm muted">新增产品后，需要配置对应资费规则。</p>
        <div v-for="(product, index) in definition.products" :key="index" class="mt-3 flex flex-wrap gap-2"><input v-model="product.code" class="input w-28" :aria-label="`产品 ${index + 1} 代码`" placeholder="产品代码" pattern="[A-Z0-9_-]{1,64}" required /><input v-model="product.name" class="input min-w-0 flex-1" :aria-label="`产品 ${index + 1} 名称`" placeholder="产品名称" required maxlength="100" /><button v-if="!readonly" type="button" class="btn btn-ghost" @click="definition.products.splice(index, 1)">删除</button></div>
        <button v-if="!readonly" type="button" class="btn btn-secondary mt-3" @click="definition.products.push({ code: '', name: '' })">添加产品</button>
      </section>
      <section><h4 class="font-bold">国家分区</h4><p class="mt-1 text-sm muted">输入两位国家代码，用空格或逗号分隔。每个国家只能属于一个分区；未列出国家使用默认分区。</p>
        <div v-for="zone in zones" :key="zone" class="mt-3"><label class="text-sm font-bold">{{ zone }}<textarea class="textarea mt-1" rows="2" :value="countriesFor(zone)" :aria-label="`${zone} 国家代码`" @change="setCountries(zone, $event)" /></label></div>
        <p v-if="mappingError" role="alert" class="mt-2 text-sm text-red-700">{{ mappingError }}</p>
      </section>
      <section>
        <div class="flex flex-wrap items-center justify-between gap-3"><h4 class="font-bold">资费规则（{{ definition.rules.length }} 条）</h4><button v-if="!readonly" type="button" class="btn btn-secondary" @click="addRule">添加规则</button></div>
        <div v-if="rule" class="mt-4 space-y-4 rounded-lg border border-cyan-100 p-4">
          <div class="grid gap-4 md:grid-cols-3">
            <label>规则标识<input v-model="rule.id" class="input mt-1" required pattern="[A-Z0-9_-]{1,64}" /></label>
            <label>产品<select v-model="rule.product" class="select mt-1" required><option v-for="p in definition.products" :key="p.code" :value="p.code">{{ p.name }}（{{ p.code }}）</option></select></label>
            <label>范围<select v-model="rule.scope" class="select mt-1" @change="rule.zone = rule.scope === 'DOMESTIC' ? 'DOM' : definition.fallbackZone"><option value="DOMESTIC">国内</option><option value="INTERNATIONAL">国际</option></select></label>
            <label>运输方式<select v-model="rule.mode" class="select mt-1"><option>AIR</option><option>SAL</option><option>SEA</option></select></label>
            <label>分区<input v-model="rule.zone" class="input mt-1" required pattern="[A-Z0-9_-]{1,64}" :readonly="rule.scope === 'DOMESTIC'" /></label>
            <label>重量上限（g）<input v-model.number="rule.maxWeightGrams" class="input mt-1" type="number" required min="1" max="1000000" step="1" /></label>
            <label>计价方式<select v-model="rule.algorithm" class="select mt-1" @change="changeAlgorithm"><option value="FIRST_STEP">首重续重</option><option value="TIERS">重量档</option></select></label>
          </div>
          <div v-if="rule.algorithm === 'FIRST_STEP'" class="grid gap-4 md:grid-cols-2">
            <label>首重（g）<input v-model.number="rule.firstWeightGrams" class="input mt-1" type="number" min="1" step="1" required /></label><label>首重价<input v-model.number="rule.firstPrice" class="input mt-1" type="number" min="0" max="1000000" step="0.01" required /></label>
            <label>续重单位（g）<input v-model.number="rule.stepWeightGrams" class="input mt-1" type="number" min="1" step="1" required /></label><label>每单位续重价<input v-model.number="rule.stepPrice" class="input mt-1" type="number" min="0" max="1000000" step="0.01" required /></label>
          </div>
          <div v-else><p class="text-sm muted">按上限从小到大排列，最后一档必须等于重量上限，价格不能随重量下降。</p>
            <div v-for="(tier, index) in rule.tiers" :key="index" class="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto]"><label>档上限（g）<input v-model.number="tier.upToGrams" class="input mt-1" type="number" min="1" step="1" required /></label><label>该档基本价<input v-model.number="tier.price" class="input mt-1" type="number" min="0" max="1000000" step="0.01" required /></label><button v-if="!readonly" type="button" class="btn btn-ghost self-end" @click="rule.tiers.splice(index, 1)">删除档</button></div>
            <button v-if="!readonly" type="button" class="btn btn-secondary mt-3" @click="rule.tiers.push({ upToGrams: rule.maxWeightGrams, price: 0 })">添加重量档</button>
          </div>
          <div><h5 class="text-sm font-bold">每件附加费</h5><p v-if="!rule.fees.length" class="mt-2 text-sm muted">无附加费</p>
            <p class="mt-2 text-sm muted">信函（U）的 REGISTRATION 费用必须勾选“可选”；金额随版本配置，选择挂号时才收取。</p>
            <label v-for="fee in rule.fees" :key="`optional-${fee.code}`" class="mt-2 flex items-center gap-2"><input type="checkbox" v-model="fee.optional" />{{ fee.code || '新费用' }}：可选附加服务</label>
            <div v-for="(fee, index) in rule.fees" :key="index" class="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto]"><label>费用代码<input v-model="fee.code" class="input mt-1" pattern="[A-Z0-9_-]{1,64}" required /></label><label>金额<input v-model.number="fee.amount" class="input mt-1" type="number" min="0" max="1000000" step="0.01" required /></label><button v-if="!readonly" type="button" class="btn btn-ghost self-end" @click="rule.fees.splice(index, 1)">删除费用</button></div>
            <button v-if="!readonly" type="button" class="btn btn-secondary mt-3" @click="rule.fees.push({ code: '', amount: 0 })">添加附加费</button>
          </div>
          <button v-if="!readonly" type="button" class="btn btn-ghost text-red-700" @click="removeRule">删除当前规则</button>
        </div>
      </section>
    </fieldset>
    <details><summary class="cursor-pointer text-sm font-bold text-cyan-800">查看完整资费表</summary><div class="mt-3 overflow-x-auto"><table class="table"><thead><tr><th>规则</th><th>范围/方式/分区</th><th>基本资费</th><th>附加费</th><th>上限</th></tr></thead><tbody><tr v-for="(r, index) in definition.rules" :key="index"><td>{{ r.id }}</td><td>{{ r.scope === 'DOMESTIC' ? '国内' : '国际' }} / {{ r.mode }} / {{ r.zone }}</td><td><template v-if="r.algorithm === 'FIRST_STEP'">首 {{ r.firstWeightGrams }}g / {{ money(r.firstPrice) }}；续 {{ r.stepWeightGrams }}g / {{ money(r.stepPrice) }}</template><template v-else><div v-for="(tier, t) in r.tiers" :key="t">≤ {{ tier.upToGrams }}g：{{ money(tier.price) }}</div></template></td><td><div v-for="f in r.fees" :key="f.code">{{ f.code }}：{{ money(f.amount) }}</div><span v-if="!r.fees.length">—</span></td><td>{{ r.maxWeightGrams }}g</td></tr></tbody></table></div></details>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue';
import { money } from '../lib/postage';
const definition = defineModel({ type: Object, required: true });
defineProps({ readonly: Boolean });
const selectedIndex = ref(0), mappingError = ref('');
const rule = computed(() => definition.value.rules[selectedIndex.value]);
const zones = computed(() => [...new Set([definition.value.fallbackZone, ...Object.values(definition.value.countryZones), ...definition.value.rules.filter(r => r.scope === 'INTERNATIONAL').map(r => r.zone)].filter(Boolean))].sort());
function countriesFor(zone) { return Object.entries(definition.value.countryZones).filter(([, z]) => z === zone).map(([c]) => c).sort().join(' '); }
function setCountries(zone, event) {
  mappingError.value = '';
  const codes = [...new Set(event.target.value.toUpperCase().split(/[\s,;，；]+/).filter(Boolean))];
  if (codes.some(code => !/^[A-Z]{2}$/.test(code) || code === definition.value.domesticCountry)) { mappingError.value = '国际国家代码须为两位大写字母，不能包含国内国家。输入已还原，请修正后再保存。'; event.target.value = countriesFor(zone); return; }
  const next = { ...definition.value.countryZones };
  for (const [code, z] of Object.entries(next)) if (z === zone) delete next[code];
  for (const code of codes) next[code] = zone;
  definition.value.countryZones = next;
}
function addRule() {
  definition.value.rules.push({ id: '', product: definition.value.products[0]?.code || '', scope: 'DOMESTIC', mode: 'AIR', zone: 'DOM', algorithm: 'FIRST_STEP', maxWeightGrams: 20000, firstWeightGrams: 1000, stepWeightGrams: 1000, firstPrice: 0, stepPrice: 0, tiers: [], fees: [] });
  selectedIndex.value = definition.value.rules.length - 1;
}
function removeRule() { definition.value.rules.splice(selectedIndex.value, 1); selectedIndex.value = Math.max(0, selectedIndex.value - 1); }
function changeAlgorithm() {
  if (rule.value.algorithm === 'TIERS') { Object.assign(rule.value, { firstWeightGrams: null, stepWeightGrams: null, firstPrice: null, stepPrice: null, tiers: [{ upToGrams: rule.value.maxWeightGrams, price: 0 }] }); }
  else Object.assign(rule.value, { firstWeightGrams: 1000, stepWeightGrams: 1000, firstPrice: 0, stepPrice: 0, tiers: [] });
}
defineExpose({ validate() { if (mappingError.value) throw new Error(mappingError.value); } });
</script>
