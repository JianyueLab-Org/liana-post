<template>
  <div class="space-y-6">
    <section class="card hero-panel p-5"><h3 class="card-title">收寄结算记录与恢复</h3><p class="mt-2 text-sm muted">异常后查询原结算，继续确认创建结果。取消会先确认邮件未创建再释放邮资；若邮件已创建，将完成原结算。不要再次向客户收款。</p></section>
    <p v-if="error" role="alert" class="rounded bg-red-50 p-4 text-red-800">{{ error }}</p>
    <section class="card p-5 space-y-4">
      <form class="flex flex-wrap gap-2" @submit.prevent="open(id)"><input v-model.trim="id" class="input min-w-0 flex-1" placeholder="输入结算编号" required /><button class="btn btn-secondary" :disabled="busy">查询</button><button type="button" class="btn btn-secondary" :disabled="busy" @click="load">刷新记录</button></form>
      <div class="overflow-auto"><table class="table"><thead><tr><th>结算编号</th><th>状态</th><th>应付</th><th>运单</th><th>时间</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.acceptanceId"><td class="break-all">{{ row.acceptanceId }}</td><td>{{ acceptanceStatus(row.status) }}</td><td>{{ money(row.quote.calculation.totalAmount) }}</td><td>{{ row.waybillNo || '—' }}</td><td>{{ formatTime(row.createdAt) }}</td><td><button class="btn btn-ghost" :disabled="busy" @click="open(row.acceptanceId)">查看</button></td></tr></tbody></table></div>
      <div class="flex gap-3"><button class="btn btn-secondary" :disabled="busy || page===0" @click="page--;load()">上一页</button><span>第 {{ page+1 }} 页</span><button class="btn btn-secondary" :disabled="busy || rows.length<20" @click="page++;load()">下一页</button></div>
    </section>
    <section v-if="detail" class="card p-5 space-y-4"><PostageSettlementDetail :value="detail" /><div v-if="detail.status==='PREPARED'" class="space-y-3"><p class="text-sm text-amber-800">邮资已预占，其他邮件无法使用这些邮票。请继续确认收寄或取消本次请求。</p><button class="btn btn-primary" :disabled="busy" @click="resume">继续确认收寄</button><label class="flex gap-2 text-sm"><input v-model="cancelChecked" type="checkbox" />确认取消未完成收寄并释放凭证</label><button class="btn btn-secondary" :disabled="busy || !cancelChecked" @click="cancel">确认取消</button></div><RouterLink v-if="detail.waybillNo && session.hasAction('MAIL_QUERY')" class="btn btn-secondary" :to="`/mail/detail/${detail.waybillNo}`">查看邮件</RouterLink></section>
  </div>
</template>
<script setup>
import {onMounted,ref} from 'vue';
import {useSessionStore} from '../../stores/session';
import {acceptanceApi,acceptanceStatus} from '../../lib/postageAcceptance';
import {money,formatTime} from '../../lib/postage';
import PostageSettlementDetail from '../../components/PostageSettlementDetail.vue';
const session=useSessionStore(),rows=ref([]),detail=ref(null),page=ref(0),id=ref(''),busy=ref(false),error=ref(''),cancelChecked=ref(false);
async function run(fn) { if(busy.value)return;busy.value=true;error.value='';try{await fn();}catch(e){error.value=e.message;}finally{busy.value=false;} }
async function load(){await run(async()=>{rows.value=await acceptanceApi.list(page.value,session.token);});}
async function open(value){await run(async()=>{detail.value=await acceptanceApi.get(value,session.token);id.value=value;cancelChecked.value=false;});}
async function resume(){await run(async()=>{detail.value=await acceptanceApi.resume(detail.value.acceptanceId,session.token);rows.value=await acceptanceApi.list(page.value,session.token);});}
async function cancel(){await run(async()=>{detail.value=await acceptanceApi.cancel(detail.value.acceptanceId,session.token);cancelChecked.value=false;rows.value=await acceptanceApi.list(page.value,session.token);});}
onMounted(load);
</script>
