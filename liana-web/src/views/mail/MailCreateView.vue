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

      <p v-if="topupAttempt && !quote" class="mt-4 text-amber-800">上次差额购买结果待确认。请在下方恢复原购买，不要重新收款。</p>
      <div v-if="topupAttempt && !quote" class="mt-3 space-y-3"><label class="flex gap-2 text-sm"><input v-model="topupCashChecked" type="checkbox" />确认此前已收到 {{ money(topupAttempt.lines.reduce((n,l)=>n+Number(l.faceValue)*l.quantity,0)) }} LNR 现金</label><button class="btn btn-primary" :disabled="submitting || !topupCashChecked" @click="buyDeficit">恢复原差额购买</button></div>
      <form class="mt-4" @submit.prevent="submit">
        <fieldset class="grid-two" :disabled="submitting || (Boolean(attempt) && !reentering) || Boolean(settlement) || Boolean(topupAttempt)">
        <select class="select col-span-2" v-model="form.mailTypeCode">
          <option value="">请选择邮件类型</option>
          <option v-for="item in mailTypes" :key="item.code" :value="item.code">{{ item.name }}</option>
        </select>

        <AdditionalServicesInput class="col-span-2" v-model="form.additionalServices" :options="serviceOptions" :disabled="submitting" />
        <p class="col-span-2 text-sm text-gray-500">信函不选挂号使用 U 条码；选择挂号使用 R 条码并计入挂号费。标签属于业务选项，不是自由文本。</p>

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
          <button type="button" class="btn btn-secondary" @click="getQuote">计算邮资</button>
        </div>
        </fieldset>
        <section v-if="quote && !settlement && (!attempt || reentering)" class="mt-5 space-y-4 border-t pt-4">
          <h4 class="font-bold">已贴用邮资</h4>
          <p class="text-sm muted">每张数码邮票一次性用于当前邮件，可与传统邮票组合。溢贴不找零、不保留余额。</p>
          <fieldset :disabled="submitting || Boolean(topupAttempt)" class="space-y-3">
            <label class="block">数码邮票代码（每行一张）<textarea v-model="codesText" class="textarea mt-1 font-mono" rows="3" /></label>
            <div v-for="(line,i) in traditional" :key="i" class="grid gap-2 sm:grid-cols-[1fr_1fr_auto]"><label>传统邮票面额<input v-model="line.faceValue" class="input mt-1" type="number" min="0.01" max="999999.99" step="0.01" required /></label><label>张数<input v-model="line.quantity" class="input mt-1" type="number" min="1" max="1000" step="1" required /></label><button type="button" class="btn btn-ghost self-end" @click="traditional.splice(i,1)">删除</button></div>
            <button type="button" class="btn btn-secondary" :disabled="traditional.length>=50" @click="traditional.push({faceValue:'1.20',quantity:1})">添加传统邮票</button>
            <label v-if="traditional.length" class="flex gap-2 text-sm"><input v-model="traditionalConfirmed" type="checkbox" />我已人工鉴别并确认上述传统邮票贴在当前邮件上</label>
            <button type="button" class="btn btn-secondary" @click="checkPostage">核对已贴邮资</button>
          </fieldset>
          <div v-if="checked" class="rounded border p-4 space-y-3"><p>已贴用 {{ money(checked.applied/100) }} LNR · 应付 {{ money(quote.calculation.totalAmount) }} LNR · {{ checked.deficit>0 ? `差额 ${money(checked.deficit/100)}` : `溢贴 ${money(-checked.deficit/100)}` }} LNR</p>
            <div v-if="checked.deficit>0" class="space-y-3"><p class="text-sm muted">差额补购复用柜台零售。确认收款后才发行并加入当前邮件；如收寄失败，已购邮票可继续使用。</p><label class="flex gap-2 text-sm"><input v-model="topupCashChecked" type="checkbox" :disabled="submitting" />我已收到差额现金 {{ money(checked.deficit/100) }} LNR</label><button type="button" class="btn btn-primary" :disabled="submitting || !topupCashChecked" @click="buyDeficit">{{ topupAttempt ? '恢复差额购买并贴用' : '购买差额邮票并贴用' }}</button></div>
          </div>
          <button type="submit" class="btn btn-primary" :disabled="submitting || Boolean(topupAttempt) || !checked || checked.deficit>0">确认核销邮资并收寄</button>
        </section>
        <div v-if="attempt && !settlement" class="mt-4 space-y-3"><p class="text-amber-800">前次收寄结果待确认，原请求编号 {{ attempt.requestId }}。请先恢复结果，不要重新收款。</p><button type="button" class="btn btn-primary" :disabled="submitting" @click="recover">恢复原收寄请求</button><RouterLink class="btn btn-secondary" to="/postage/acceptances">查询结算记录</RouterLink></div>
        <div v-if="settlement" class="mt-4 space-y-4"><PostageSettlementDetail :value="settlement" /><div v-if="settlement.status==='PREPARED'" class="space-y-3"><p class="text-amber-800">邮资已预占，请继续确认邮件创建结果。</p><button type="button" class="btn btn-primary" :disabled="submitting" @click="resumeSettlement">继续确认收寄</button><label class="flex gap-2 text-sm"><input v-model="cancelChecked" type="checkbox" />确认取消尚未创建的收寄</label><button type="button" class="btn btn-secondary" :disabled="submitting || !cancelChecked" @click="cancelSettlement">取消并释放预占</button></div><button v-else type="button" class="btn btn-secondary" :disabled="submitting" @click="nextMail">录入下一件</button></div>
      </form>
    </div>

    <div class="card p-5">
      <PostageQuoteDetail v-if="quote && !settlement" :snapshot="quote" :valid="Date.now() < Date.parse(quote.expiresAt)" />
      <h3 class="card-title">提交结果</h3>
      <pre class="mt-4 overflow-auto whitespace-pre-wrap text-sm text-gray-700">{{ result }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { mailApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';
import { postageApi, letterProducts, letterRuleProduct, additionalServiceOptions } from '../../lib/postage';
import AdditionalServicesInput from '../../components/AdditionalServicesInput.vue';
import PostageQuoteDetail from '../../components/PostageQuoteDetail.vue';
import PostageSettlementDetail from '../../components/PostageSettlementDetail.vue';
import { money } from '../../lib/postage';
import { retailApi, retailAttemptStore } from '../../lib/postageRetail';
import { acceptanceApi, traditionalLines, codeLines, appliedCents, acceptanceAttemptStore } from '../../lib/postageAcceptance';

const session = useSessionStore();
const tariff = ref(null), quote = ref(null);
const codesText=ref(''),traditional=ref([]),traditionalConfirmed=ref(false),checked=ref(null),topupCashChecked=ref(false),settlement=ref(null),cancelChecked=ref(false);
const attempts=acceptanceAttemptStore(window.localStorage,session.user?.username),attempt=ref(attempts.read());
const reentering=ref(false);
const topups=retailAttemptStore(window.localStorage,`${session.user?.username}:acceptance-topup`),topupAttempt=ref(topups.read());
let originalBody=null;
const serviceOptions = computed(() => {
  const d = tariff.value?.definition;
  if (!d) return [];
  const zone = form.mailScope === 'DOMESTIC' ? 'DOM' : d.countryZones[form.destCountryCode] || d.fallbackZone;
  const rule = d.rules.find(r => r.product === letterRuleProduct(d, form.mailTypeCode) && r.scope === form.mailScope && r.zone === zone && r.mode === (isInternational.value ? form.serviceType : DEFAULT_DOMESTIC_SERVICE_TYPE));
  return additionalServiceOptions(rule, form.mailTypeCode);
});
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
  additionalServices: [],
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
  if (submitting.value || (attempt.value && !reentering.value) || settlement.value || topupAttempt.value) { result.value='请先恢复当前业务再录入下一件';return; }
  form.mailScope = scope;

  if (scope === 'INTERNATIONAL') {
    await loadServiceTypes();
  }

  const isDomesticExample = scope === 'DOMESTIC';
  Object.assign(form, {
    additionalServices: [],
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
  mailTypes.value = letterProducts(await mailApi.listMailTypes(session.token));
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

function mailPayload() {
  if (!form.mailTypeCode || !form.senderFullName || !form.senderPhone || !form.senderAddress || !form.recipientFullName || !form.recipientPhone || !form.recipientAddress) throw new Error('请补全收寄必填信息');
  if (!Number.isInteger(form.weightGrams) || form.weightGrams<1) throw new Error('重量须为正整数克');
  if (isInternational.value && (!form.destCountryCode || !form.serviceType)) throw new Error('请选择寄达国和服务类型');
  return {...form,additionalServices:[...form.additionalServices],serviceType:isInternational.value?form.serviceType:DEFAULT_DOMESTIC_SERVICE_TYPE,
    destCountryCode:isInternational.value?form.destCountryCode:null,originFacilityCode:session.user?.facilityCode,currentFacilityCode:session.user?.facilityCode};
}
async function getQuote() {
  if (submitting.value || (attempt.value && !reentering.value) || settlement.value || topupAttempt.value)return;
  submitting.value=true;result.value='';
  try {
    const mail=await acceptanceApi.validate(mailPayload(),session.token);
    quote.value=await postageApi.quote({product:mail.mailTypeCode,scope:mail.mailScope,mode:mail.serviceType,destinationCountry:mail.destCountryCode||'LN',weightGrams:mail.weightGrams,additionalServices:mail.additionalServices},session.token);
    checked.value=null;
  } catch(e){result.value=e.message;}finally{submitting.value=false;}
}
async function inspectPostage() {
  if (!quote.value)throw new Error('请先计算邮资');
  const codes=codeLines(codesText.value),lines=traditionalLines(traditional.value);
  if (lines.length && !traditionalConfirmed.value) throw new Error('请人工鉴别并确认传统邮票');
  const stamps=await Promise.all(codes.map(code=>retailApi.lookup(code,session.token)));
  if (stamps.some(s=>s.status!=='ISSUED' || s.displayStatus!=='ISSUED')) throw new Error('有邮票已经预占、核销、过期或作废');
  const applied=appliedCents(lines,stamps),deficit=Math.round(Number(quote.value.calculation.totalAmount)*100)-applied;
  checked.value={codes,lines,stamps,applied,deficit};return checked.value;
}
async function checkPostage(){if(submitting.value)return;submitting.value=true;result.value='';try{await inspectPostage();}catch(e){checked.value=null;result.value=e.message;}finally{submitting.value=false;}}
async function buyDeficit(){
  if(submitting.value || !topupCashChecked.value)return;submitting.value=true;result.value='';
  try {
    if (!topupAttempt.value) {
      const value=await inspectPostage();if(value.deficit<=0)throw new Error('当前没有差额');
      await acceptanceApi.validate(mailPayload(),session.token);
      const policy=await retailApi.policy(session.token),max=Math.round(Number(policy.maxFaceValue)*100),min=Math.round(Number(policy.minFaceValue)*100);
      const count=Math.ceil(value.deficit/max);if(count>100 || count+value.codes.length>100)throw new Error('差额所需邮票超过 100 张，请在零售工作台处理');
      const base=Math.floor(value.deficit/count),remainder=value.deficit%count;
      if(base<min)throw new Error('差额低于当前最小可售面额，请在零售工作台选择合法面额并按溢贴处理');
      const lines=[];if(remainder)lines.push({faceValue:money((base+1)/100),quantity:remainder});if(count-remainder)lines.push({faceValue:money(base/100),quantity:count-remainder});
      topupAttempt.value=topups.prepare(lines);
    }
    const a=topupAttempt.value;
    let sale;
    try { sale=a.saleId?await retailApi.sale(a.saleId,session.token):await retailApi.create({requestId:a.requestId,lines:a.lines},session.token); }
    catch(e){if(e.status===400 && !a.saleId){topupAttempt.value=null;topups.clear();topupCashChecked.value=false;throw new Error(`${e.message}；售卖未创建，请核对已收现金并到零售工作台处理，不要再次收款。`);}throw e;}
    a.saleId=sale.sale.saleId;topups.write(a);
    if(sale.sale.status==='CANCELLED'){topupAttempt.value=null;topups.clear();topupCashChecked.value=false;throw new Error('原差额售卖已取消，未发行邮票。请核对现金并到零售工作台处理。');}
    const paid=sale.sale.status==='PAID'?sale:await retailApi.confirm(a.saleId,{requestId:`cash-${a.saleId}`,amountReceived:sale.sale.totalAmount},session.token);
    const existing=codeLines(codesText.value);codesText.value=[...new Set([...existing,...paid.stamps.map(s=>s.code)])].join('\n');
    topupAttempt.value=null;topups.clear();topupCashChecked.value=false;if(quote.value)await inspectPostage();result.value='差额邮票已发行并加入当前邮件。请核对后确认收寄。';
  }catch(e){result.value=e.message;}finally{submitting.value=false;}
}
function installSettlement(value){reentering.value=false;settlement.value=value;quote.value=value.quote;attempt.value={requestId:attempt.value?.requestId||'',acceptanceId:value.acceptanceId};if(value.status==='PREPARED')attempts.write(attempt.value);else{attempt.value=null;attempts.clear();}cancelChecked.value=false;}
async function submit() {
  if(submitting.value || (attempt.value && !reentering.value) || settlement.value)return;
  submitting.value = true;
  try {
    const postage=await inspectPostage();if(postage.deficit>0)throw new Error('邮资不足，请先补购差额');
    originalBody={requestId:attempt.value?.requestId||crypto.randomUUID(),quoteId:quote.value.quoteId,mail:mailPayload(),stampCodes:postage.codes,traditionalStamps:postage.lines,traditionalConfirmed:traditionalConfirmed.value};
    reentering.value=false;
    attempt.value={requestId:originalBody.requestId};attempts.write(attempt.value);
    installSettlement(await acceptanceApi.accept(originalBody,session.token));result.value=settlement.value.status==='COMPLETED'?'收寄结算已完成。':'原收寄已取消，请录入下一件。';
  } catch (error) {
    if ([400,409].includes(error.status) && attempt.value && !settlement.value) {
      try { installSettlement(await acceptanceApi.byRequest(attempt.value.requestId,session.token)); }
      catch(e) { if(e.status===404){reentering.value=true;} }
    }
    result.value = error?.message || String(error);
  } finally {
    submitting.value = false;
  }
}
async function recover(){if(submitting.value)return;submitting.value=true;try{
  try{installSettlement(await (attempt.value.acceptanceId?acceptanceApi.get(attempt.value.acceptanceId,session.token):acceptanceApi.byRequest(attempt.value.requestId,session.token)));}
  catch(e){if(e.status!==404)throw e;if(originalBody)installSettlement(await acceptanceApi.accept(originalBody,session.token));else{reentering.value=true;result.value='尚无已预占的结算，请重新录入邮件并核对已有邮票。系统保留原请求编号，若原请求随后完成，可在结算记录中恢复。不要重复收款。';}}
}catch(e){result.value=e.message;}finally{submitting.value=false;}}
async function resumeSettlement(){if(submitting.value)return;submitting.value=true;try{installSettlement(await acceptanceApi.resume(settlement.value.acceptanceId,session.token));}catch(e){result.value=e.message;}finally{submitting.value=false;}}
async function cancelSettlement(){if(submitting.value || !cancelChecked.value)return;submitting.value=true;try{installSettlement(await acceptanceApi.cancel(settlement.value.acceptanceId,session.token));}catch(e){result.value=e.message;}finally{submitting.value=false;}}
function nextMail(){settlement.value=null;quote.value=null;checked.value=null;codesText.value='';traditional.value=[];traditionalConfirmed.value=false;originalBody=null;result.value='';}
watch(form,()=>{if((!attempt.value||reentering.value)&&!settlement.value&&!topupAttempt.value){quote.value=null;checked.value=null;}},{deep:true});
watch([codesText,traditional,traditionalConfirmed],()=>{if(!topupAttempt.value){checked.value=null;topupCashChecked.value=false;}},{deep:true});

onMounted(async () => {
  try { await Promise.all([loadMailTypes(), loadCountries(), postageApi.current(session.token).then(v => { tariff.value = v; })]); }
  catch (e) { result.value = e.message; }
  if(attempt.value)await recover();
  if(topupAttempt.value?.saleId){try{const sale=await retailApi.sale(topupAttempt.value.saleId,session.token);if(sale.sale.status==='PAID'){codesText.value=sale.stamps.filter(s=>s.status==='ISSUED').map(s=>s.code).join('\n');topupAttempt.value=null;topups.clear();result.value='已恢复上次补购邮票，请重新录入邮件及其他已贴邮资后核对。';}}catch(e){result.value=e.message;}}
});
</script>
