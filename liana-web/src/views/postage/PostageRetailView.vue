<template>
  <div class="space-y-6">
    <section class="card hero-panel p-5"><h3 class="card-title">邮资零售与数码邮票</h3><p class="mt-2 text-sm muted">可独立售卖邮资，无需同时收寄邮件。先创建售卖，再确认已收到柜台现金，系统发行相应面额的数码邮票。</p></section>
    <p v-if="error" role="alert" class="rounded-lg bg-red-50 p-4 text-red-800">{{ error }}</p>
    <p v-if="notice" role="status" class="rounded-lg bg-emerald-50 p-4 text-emerald-800">{{ notice }}</p>
    <section class="card p-5">
      <div class="flex flex-wrap items-center justify-between gap-3"><h3 class="card-title">柜台售卖</h3><button class="btn btn-secondary" :disabled="busy" @click="newSale">新建售卖</button></div>
      <p v-if="policy" class="mt-3 text-sm muted">{{ policy.currency }} · 可售面额 {{ money(policy.minFaceValue) }}–{{ money(policy.maxFaceValue) }} · 发行起 {{ policy.validityDays }} 天有效 · 单次最多 100 张</p>
      <form v-if="policy && !detail" class="mt-4 space-y-4" @submit.prevent="createSale">
        <p v-if="recoveringCreate" class="text-sm text-amber-800">前次创建请求的结果待确认，请用原内容重试。系统会返回原售卖记录。</p>
        <fieldset :disabled="busy || recoveringCreate" class="space-y-3">
          <div v-for="(line, i) in lines" :key="i" class="grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
            <label>面额（{{ policy.currency }}）<input v-model="line.faceValue" class="input mt-1" type="number" :min="policy.minFaceValue" :max="policy.maxFaceValue" step="0.01" required /></label>
            <label>张数<input v-model="line.quantity" class="input mt-1" type="number" min="1" max="100" step="1" required /></label>
            <button type="button" class="btn btn-ghost self-end" :disabled="lines.length === 1" @click="lines.splice(i, 1)">删除</button>
          </div>
          <button type="button" class="btn btn-secondary" :disabled="lines.length >= 50" @click="lines.push({faceValue:'1.00',quantity:1})">添加面额</button>
        </fieldset>
        <p class="text-sm">预计应收：{{ policy.currency }} {{ previewAmount }}。最终金额以服务器售卖记录为准。</p>
        <button class="btn btn-primary" :disabled="busy">{{ busy ? '处理中…' : '创建售卖' }}</button>
      </form>
      <article v-if="detail" class="mt-4 space-y-4" aria-label="售卖详情">
        <div class="flex flex-wrap justify-between gap-3"><div><p class="break-all font-bold">{{ detail.sale.saleId }}</p><p class="mt-2 text-sm muted">{{ saleStatus(detail.sale.status) }} · {{ detail.sale.createdBy }} / {{ detail.sale.facilityCode || '未指定网点' }} · {{ formatTime(detail.sale.createdAt) }}</p></div><button class="btn btn-secondary" :disabled="busy" @click="openSale(detail.sale.saleId)">刷新状态</button></div>
        <p class="text-3xl font-black text-cyan-800">应收 {{ detail.sale.currency }} {{ money(detail.sale.totalAmount) }}</p>
        <p class="text-sm">{{ detail.sale.lines.map(l => `${money(l.faceValue)} × ${l.quantity} 张`).join('；') }} · 发行规则版本 {{ detail.sale.policyRevision }} · 自发行日起 {{ detail.sale.validityDays }} 天有效</p>
        <form v-if="detail.sale.status === 'PENDING_PAYMENT'" class="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4" @submit.prevent="confirmCash">
          <label class="block">确认收款金额（{{ detail.sale.currency }}）<input v-model="amountReceived" class="input mt-1" type="number" min="0.01" step="0.01" required :disabled="busy" /></label>
          <label class="flex items-center gap-2 text-sm"><input v-model="cashChecked" type="checkbox" required :disabled="busy" />我已收到上述柜台现金，确认后发行数码邮票</label>
          <div class="flex flex-wrap gap-3"><button class="btn btn-primary" :disabled="busy || !cashChecked">确认收款并发行</button><button type="button" class="btn btn-secondary" :disabled="busy" @click="cancelSale">取消未收款售卖</button></div>
        </form>
        <p v-if="detail.receipt" class="text-sm">柜台收款凭据 {{ detail.receipt.receiptId }} · {{ money(detail.receipt.amountReceived) }} {{ detail.sale.currency }} · {{ detail.receipt.confirmedBy }} 于 {{ formatTime(detail.receipt.confirmedAt) }} 确认</p>
        <section v-if="detail.stamps.length" class="stamp-receipt" aria-label="数码邮票凭证">
          <p class="hidden print:block text-sm">Liana Post · 售卖 {{ detail.sale.saleId }} · 已收 {{ detail.sale.currency }} {{ money(detail.receipt.amountReceived) }}</p>
          <div class="flex flex-wrap justify-between gap-3"><h4 class="font-bold">数码邮票凭证（{{ detail.stamps.length }} 张）</h4><button class="btn btn-secondary print:hidden" @click="printReceipt">打印凭证</button></div>
          <p class="mt-2 text-sm muted">代码代表已购买邮资，请妥善保管。可在收寄录入时与其他数码邮票及传统邮票组合贴用，每张代码只能一次性用于一件邮件。</p>
          <div v-for="stamp in detail.stamps" :key="stamp.stampId" class="mt-3 break-inside-avoid rounded-lg border p-4">
            <p class="text-lg font-bold">{{ stamp.currency }} {{ money(stamp.faceValue) }} · {{ stampStatus(stamp.displayStatus) }}</p>
            <p class="mt-2 break-all font-mono text-sm">{{ stamp.code }}</p>
            <p class="mt-2 text-sm muted">发行 {{ formatTime(stamp.issuedAt) }} · 到期 {{ formatTime(stamp.expiresAt) }}</p>
            <p v-if="stamp.voidReason" class="mt-2 text-sm text-red-800">作废原因：{{ stamp.voidReason }}</p>
          </div>
        </section>
        <details v-if="detail.audit.length" class="print:hidden"><summary class="cursor-pointer font-bold">审计记录（{{ detail.audit.length }} 条）</summary><div v-for="event in detail.audit" :key="event.eventId" class="mt-3 rounded border p-3 text-sm"><p>{{ auditType(event.eventType) }} · {{ event.actor }} / {{ event.facilityCode || '—' }} · {{ formatTime(event.occurredAt) }}</p><p class="mt-1 break-all muted">{{ event.detail }}</p></div></details>
      </article>
    </section>
    <section class="card p-5 print:hidden">
      <div class="flex flex-wrap justify-between gap-3"><h3 class="card-title">售卖记录</h3><button class="btn btn-secondary" :disabled="busy" @click="loadSales">刷新记录</button></div>
      <p class="mt-2 text-sm muted">{{ administrator ? '管理员查看全部售卖。' : '仅显示当前工作人员创建的售卖。' }}</p>
      <form class="mt-3 flex flex-wrap gap-2" @submit.prevent="openSale(searchSaleId)"><input v-model.trim="searchSaleId" class="input min-w-0 flex-1" placeholder="输入售卖编号恢复业务" required /><button class="btn btn-secondary" :disabled="busy">查询售卖</button></form>
      <div class="mt-4 overflow-auto"><table class="table"><thead><tr><th>售卖编号</th><th>金额</th><th>状态</th><th>工作人员</th><th>时间</th><th>操作</th></tr></thead><tbody><tr v-for="sale in sales" :key="sale.saleId"><td class="break-all">{{ sale.saleId }}</td><td>{{ sale.currency }} {{ money(sale.totalAmount) }}</td><td>{{ saleStatus(sale.status) }}</td><td>{{ sale.createdBy }}</td><td>{{ formatTime(sale.createdAt) }}</td><td><button class="btn btn-ghost" :disabled="busy" @click="openSale(sale.saleId)">查看</button></td></tr></tbody></table><p v-if="!sales.length" class="mt-3 text-sm muted">暂无售卖记录</p></div>
      <div class="mt-3 flex items-center gap-3"><button class="btn btn-secondary" :disabled="busy || page === 0" @click="changePage(-1)">上一页</button><span class="text-sm">第 {{ page + 1 }} 页</span><button class="btn btn-secondary" :disabled="busy || sales.length < 20" @click="changePage(1)">下一页</button></div>
    </section>
    <section class="card p-5 print:hidden">
      <h3 class="card-title">凭证查询{{ administrator ? '与作废' : '' }}</h3>
      <form class="mt-4 flex flex-wrap gap-3" @submit.prevent="lookupStamp"><input v-model.trim="lookupCode" class="input min-w-0 flex-1 font-mono" placeholder="输入完整数码邮票代码 LP-…" maxlength="100" required /><button class="btn btn-secondary" :disabled="busy">查询凭证</button></form>
      <div v-if="foundStamp" class="mt-4 space-y-3 rounded-lg border p-4"><p class="break-all font-mono">{{ foundStamp.code }}</p><p>{{ foundStamp.currency }} {{ money(foundStamp.faceValue) }} · {{ stampStatus(foundStamp.displayStatus) }}</p><p class="text-sm muted">{{ formatTime(foundStamp.issuedAt) }} 发行 · {{ formatTime(foundStamp.expiresAt) }} 到期</p><p v-if="foundStamp.voidReason" class="text-sm">{{ foundStamp.voidedBy }} 作废：{{ foundStamp.voidReason }}</p>
        <form v-if="administrator && foundStamp.status === 'ISSUED'" class="space-y-3 border-t pt-3" @submit.prevent="voidStamp"><label class="block">作废原因<textarea v-model.trim="voidReason" class="textarea mt-1" maxlength="500" required :disabled="busy" /></label><label class="flex items-center gap-2 text-sm"><input v-model="voidChecked" type="checkbox" required :disabled="busy" />确认作废此凭证，作废不会自动退款或重新发行</label><button class="btn btn-primary" :disabled="busy || !voidChecked">确认作废凭证</button></form>
      </div>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue';
import { useSessionStore } from '../../stores/session';
import { money, formatTime } from '../../lib/postage';
import { retailApi, saleStatus, stampStatus, auditType, retailLines, retailAttemptStore } from '../../lib/postageRetail';
const session = useSessionStore();
const administrator = computed(() => session.user?.role === 'ADMIN');
const attempts = retailAttemptStore(window.localStorage, session.user?.username);
const policy = ref(null), lines = ref([{faceValue:'1.00',quantity:1}]), detail = ref(null), sales = ref([]), page = ref(0);
const busy = ref(false), error = ref(''), notice = ref(''), amountReceived = ref(''), cashChecked = ref(false), searchSaleId = ref('');
const lookupCode = ref(''), foundStamp = ref(null), voidReason = ref(''), voidChecked = ref(false);
let attempt = attempts.read();
const recoveringCreate = ref(Boolean(attempt && !attempt.saleId));
const previewAmount = computed(() => {
  if (!policy.value) return '—';
  try { return money(retailLines(lines.value, policy.value).reduce((total, l) => total + Math.round(Number(l.faceValue) * 100) * l.quantity, 0) / 100); } catch { return '—'; }
});
async function run(action) {
  if (busy.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try { await action(); } catch (e) { error.value = e.message; } finally { busy.value = false; }
}
function install(value) { detail.value = value; amountReceived.value = money(value.sale.totalAmount); cashChecked.value = false; searchSaleId.value = value.sale.saleId; }
async function loadSales() { await run(async () => { sales.value = await retailApi.sales(page.value, session.token); }); }
async function refreshSales() { sales.value = await retailApi.sales(page.value, session.token); }
async function newSale() {
  if (detail.value?.sale.status === 'PENDING_PAYMENT') { error.value = '请先确认收款或取消当前售卖。'; return; }
  if (attempt && !attempt.saleId) { error.value = '有尚未确认结果的创建请求，请先使用原内容重试。'; return; }
  await run(async () => { policy.value = await retailApi.policy(session.token); detail.value = null; lines.value = [{faceValue:'1.00',quantity:1}]; attempt = null; recoveringCreate.value=false; attempts.clear(); });
}
async function createSale() {
  await run(async () => {
    const normalized = recoveringCreate.value && attempt ? attempt.lines : retailLines(lines.value, policy.value);
    if (!attempt || attempt.saleId || JSON.stringify(attempt.lines) !== JSON.stringify(normalized)) attempt = attempts.prepare(normalized);
    recoveringCreate.value=true;
    try { install(await retailApi.create({requestId:attempt.requestId,lines:attempt.lines},session.token)); }
    catch(e) {
      if ([400,401,403].includes(e.status)) { attempt=null; attempts.clear(); recoveringCreate.value=false; }
      throw e;
    }
    recoveringCreate.value=false;
    attempt.saleId = detail.value.sale.saleId; attempts.write(attempt);
    notice.value = '售卖已创建，请核对金额后确认柜台收款。';
    await refreshSales();
  });
}
async function openSale(id) {
  if (recoveringCreate.value) { error.value = '请先使用原内容重试，恢复尚未确认结果的创建请求。'; return; }
  await run(async () => { install(await retailApi.sale(id, session.token)); });
}
async function confirmCash() {
  await run(async () => {
    if (!cashChecked.value) throw new Error('请确认已收到柜台现金');
    const id = detail.value.sale.saleId;
    install(await retailApi.confirm(id,{requestId:`cash-${id}`,amountReceived:amountReceived.value},session.token));
    notice.value = '收款已确认，数码邮票已发行。可打印凭证交付客户。';
    if (attempt?.saleId === id) { attempt = null; attempts.clear(); }
    await refreshSales();
  });
}
async function cancelSale() {
  await run(async () => { const id = detail.value.sale.saleId; install(await retailApi.cancel(id,session.token)); if (attempt?.saleId === id) { attempt=null; attempts.clear(); } await refreshSales(); notice.value='未收款售卖已取消。'; });
}
async function changePage(delta) { page.value += delta; await loadSales(); }
async function lookupStamp() { await run(async () => { foundStamp.value = null; foundStamp.value = await retailApi.lookup(lookupCode.value,session.token); voidReason.value=''; voidChecked.value=false; }); }
async function voidStamp() {
  await run(async () => {
    if (!voidChecked.value || !voidReason.value) throw new Error('请填写原因并确认作废');
    foundStamp.value = await retailApi.voidStamp(foundStamp.value.stampId,{requestId:`void-${foundStamp.value.stampId}`,reason:voidReason.value},session.token);
    voidChecked.value = false; notice.value='凭证已作废，原收款与发行记录保留。';
    if (detail.value?.sale.saleId === foundStamp.value.saleId) install(await retailApi.sale(foundStamp.value.saleId,session.token));
  });
}
function printReceipt() { window.print(); }
onMounted(async () => {
  await run(async () => {
    policy.value = await retailApi.policy(session.token); await refreshSales();
    if (attempt?.saleId) { install(await retailApi.sale(attempt.saleId,session.token)); if (detail.value.sale.status !== 'PENDING_PAYMENT') { attempt=null; attempts.clear(); } }
    else if (attempt) { lines.value = attempt.lines; notice.value='已恢复上次创建请求，可使用原内容重试以获取售卖结果。'; }
  });
});
</script>
<style>
@media print {
  body *:not(.stamp-receipt):not(.stamp-receipt *):not(:has(.stamp-receipt)) { display: none !important; }
  body *:has(.stamp-receipt) { display: block !important; position: static !important; overflow: visible !important; margin: 0 !important; padding: 0 !important; width: auto !important; height: auto !important; min-height: 0 !important; border: 0 !important; background: white !important; box-shadow: none !important; }
  .shell::before, .card::before { display: none !important; }
  .stamp-receipt { position: static; width: 100%; }
  .stamp-receipt button { display: none; }
}
</style>
