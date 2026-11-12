<template>
  <section class="space-y-3" aria-label="邮资结算明细">
    <h3 class="card-title">邮资结算 · {{ acceptanceStatus(value.status) }}</h3>
    <p class="break-all text-sm">结算编号 {{ value.acceptanceId }}<span v-if="value.waybillNo"> · 运单 {{ value.waybillNo }}</span></p>
    <div class="grid gap-3 sm:grid-cols-4"><p>应付 {{ value.quote.currency }} {{ money(value.quote.calculation.totalAmount) }}</p><p>传统邮票 {{ money(value.traditionalAmount) }}</p><p>数码邮票 {{ money(value.digitalAmount) }}</p><p>溢贴 {{ money(value.excessAmount) }}</p></div>
    <p class="text-sm muted">{{ value.createdBy }} / {{ value.facilityCode }} · {{ formatTime(value.completedAt || value.createdAt) }} · 溢贴不找零，不保留余额</p>
    <p v-if="value.traditionalStamps.length" class="text-sm">人工鉴别传统邮票：{{ value.traditionalStamps.map(l=>`${money(l.faceValue)} × ${l.quantity} 张`).join('；') }}</p>
    <PostageQuoteDetail :snapshot="value.quote" settled />
  </section>
</template>
<script setup>
import { money,formatTime } from '../lib/postage';
import { acceptanceStatus } from '../lib/postageAcceptance';
import PostageQuoteDetail from './PostageQuoteDetail.vue';
defineProps({value:{type:Object,required:true}});
</script>
