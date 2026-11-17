<template>
  <section class="card mt-5 p-5">
    <div class="flex items-center justify-between gap-3">
      <h3 class="card-title">直封转运</h3>
      <button class="btn btn-secondary" :disabled="busy" @click="refresh">刷新</button>
    </div>
    <p class="mt-2 text-sm text-gray-500">保留原总包条码，选择本站已收妥且未开拆的总包，编制下一段路单。按海岛网络选择陆运、航班或船班对应邮路。</p>
    <form class="mt-4 space-y-3" @submit.prevent="submit">
      <fieldset class="space-y-3" :disabled="busy || Boolean(pending)">
        <select v-model="selectedManifest" class="select w-full" @change="selectedBags = []">
          <option value="">选择上一段路单</option>
          <option v-for="no in manifestNos" :key="no" :value="no">{{ no }}</option>
        </select>
        <div class="flex flex-wrap gap-3">
          <label v-for="bag in candidates.filter(b => b.manifestNo === selectedManifest)" :key="bag.bagNo" class="text-sm">
            <input v-model="selectedBags" type="checkbox" :value="bag.bagNo" /> {{ bag.bagNo }}
          </label>
          <span v-if="!candidates.length" class="text-sm text-gray-500">暂无可转运总包；接收确认完成后可刷新。</span>
        </div>
        <select v-model="selectedRoute" class="select w-full">
          <option value="">选择下一段邮路</option>
          <option v-for="route in routes" :key="route.routeCode" :value="route.routeCode">
            {{ route.destinationFacilityCode }} · {{ route.transportMode }} · {{ route.routeCode }}
          </option>
        </select>
      </fieldset>
      <p v-if="pending" class="text-sm text-amber-800">请求已固定，重试将使用同一批总包及邮路。</p>
      <button class="btn btn-primary" :disabled="busy || (!pending && (!selectedBags.length || !selectedRoute))">{{ pending ? '重试原转运请求' : '编制下一段路单' }}</button>
    </form>
    <p v-if="message" class="mt-3 text-sm whitespace-pre-wrap">{{ message }}</p>
    <div class="mt-4 overflow-auto">
      <table class="table">
        <thead><tr><th>下一段路单</th><th>接收机构</th><th>编制状态</th><th>说明</th></tr></thead>
        <tbody>
          <tr v-for="row in history" :key="row.command.requestNo">
            <td>{{ row.command.manifestNo }}</td><td>{{ row.command.destinationOrgCode }}</td>
            <td>{{ labels[row.status] || row.status }}</td><td>{{ row.lastError || (row.status === 'SENT' ? '请到批次管理封单，再安排运输及交接' : '等待确认') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { facilityApi, sortingApi } from '../../lib/api';
import { useSessionStore } from '../../stores/session';

const session = useSessionStore();
const candidates = ref([]), history = ref([]), routes = ref([]);
const selectedManifest = ref(''), selectedBags = ref([]), selectedRoute = ref('');
const busy = ref(false), message = ref('');
const storageKey = `sorting-forward:${session.user?.facilityCode}:${session.user?.userId || session.user?.username}`;
const pending = ref(null);
try { pending.value = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch { sessionStorage.removeItem(storageKey); }
const manifestNos = computed(() => [...new Set(candidates.value.map(b => b.manifestNo))]);
const labels = { PENDING: '待编制', SENDING: '编制中', RETRY: '等待重试', SENT: '已编制', CONFLICT: '编制冲突（保留占用）' };

async function refresh() {
  busy.value = true;
  try {
    const facility = session.user?.facilityCode;
    [candidates.value, history.value] = await Promise.all([
      sortingApi.forwardingCandidates(facility, session.token), sortingApi.listForwarding(facility, session.token),
    ]);
    routes.value = (await facilityApi.listRoutes(session.token)).filter(r => r.status === 1 && r.originFacilityCode === facility);
  } catch (error) { message.value = error.message; }
  finally { busy.value = false; }
}
async function submit() {
  busy.value = true;
  try {
    const route = routes.value.find(r => r.routeCode === selectedRoute.value);
    if (!pending.value) pending.value = { requestNo: crypto.randomUUID(), previousManifestNo: selectedManifest.value,
      facilityCode: session.user?.facilityCode, destinationOrgCode: route.destinationFacilityCode,
      facilityRouteCode: route.routeCode, bagNos: [...selectedBags.value] };
    sessionStorage.setItem(storageKey, JSON.stringify(pending.value));
    const state = await sortingApi.forward(pending.value, session.token);
    message.value = `下一段路单 ${state.command.manifestNo}：${labels[state.status] || state.status}。`;
    pending.value = null; sessionStorage.removeItem(storageKey); selectedBags.value = [];
    await refresh();
  } catch (error) {
    message.value = error.message;
    if ([400, 401, 403, 404, 409].includes(Number(error.code))) { pending.value = null; sessionStorage.removeItem(storageKey); }
  } finally { busy.value = false; }
}
onMounted(refresh);
</script>
