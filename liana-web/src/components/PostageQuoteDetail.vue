<template>
  <section class="card p-5" aria-label="报价明细">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div><h3 class="card-title">{{ settled ? '结算计价依据' : '报价明细' }}</h3><p class="mt-2 break-all text-sm muted">{{ snapshot.quoteId }}</p></div>
      <span class="badge" :class="settled || valid ? 'badge-success' : 'badge-warning'">{{ settled ? '已保存计价快照' : valid ? '当前有效' : '已失效，请重新报价' }}</span>
    </div>
    <p class="mt-5 text-3xl font-black text-cyan-800">{{ snapshot.currency }} {{ money(snapshot.calculation.totalAmount) }}</p>
    <dl class="mt-5 grid gap-3 text-sm sm:grid-cols-2">
      <div>邮件产品：{{ snapshot.input.product }} · {{ snapshot.input.scope === 'DOMESTIC' ? '国内' : '国际' }}</div>
      <div>目的地 / 方式：{{ snapshot.input.destinationCountry }} / {{ snapshot.input.mode }}</div>
      <div>重量：{{ snapshot.input.weightGrams }} g</div><div>计价分区：{{ snapshot.calculation.zone }}</div>
      <div>附加服务：{{ (snapshot.input.product === 'R' ? ['REGISTRATION'] : snapshot.input.additionalServices || []).map(s => s === 'REGISTRATION' ? '挂号' : s).join('、') || '无' }}</div>
      <div>资费版本：{{ snapshot.tariffVersion }}</div><div>规则：{{ snapshot.calculation.ruleId }}</div>
      <div v-if="snapshot.calculation.algorithm === 'TIERS'">适用重量档：≤ {{ snapshot.calculation.tierUpToGrams }} g</div>
      <div v-else>续重单位数：{{ snapshot.calculation.additionalUnits }}</div>
      <div>操作员：{{ snapshot.createdBy }}</div>
      <div>报价时间：{{ formatTime(snapshot.createdAt) }}</div><div>失效时间：{{ formatTime(snapshot.expiresAt) }}</div>
    </dl>
    <table class="table mt-5"><thead><tr><th>费用项目</th><th>金额（{{ snapshot.currency }}）</th></tr></thead><tbody>
      <tr><td>基本邮资</td><td>{{ money(snapshot.calculation.baseAmount) }}</td></tr>
      <tr v-for="fee in snapshot.calculation.fees" :key="fee.code"><td>{{ fee.code === 'REGISTRATION' ? '挂号费' : fee.code }}</td><td>{{ money(fee.amount) }}</td></tr>
      <tr><td class="font-bold">应付邮资</td><td class="font-bold">{{ money(snapshot.calculation.totalAmount) }}</td></tr>
    </tbody></table>
    <p v-if="settled" class="mt-4 text-sm muted">时间均为 UTC+08。此快照保存预占时的计价依据，报价到期或后续资费调整不会改变这笔结算。</p>
    <p v-else class="mt-4 text-sm muted">时间均为 UTC+08。有效性以最近一次查询为准；资费更新后请重新检查。收寄仍需检查目的地是否开放及邮资是否结清。</p>
  </section>
</template>
<script setup>
import { money, formatTime } from '../lib/postage';
defineProps({ snapshot: { type: Object, required: true }, valid: Boolean, settled: Boolean });
</script>
