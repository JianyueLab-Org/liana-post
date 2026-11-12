<template>
  <div class="space-y-6">
    <section class="card hero-panel p-5"><div class="flex flex-wrap justify-between gap-3"><div><h3 class="card-title">资费版本管理</h3><p class="mt-2 text-sm muted">编辑草稿，确认后发布。已发布的资费保留为历史记录。</p></div><button class="btn btn-secondary" :disabled="busy" @click="refresh">刷新版本</button></div></section>
    <p v-if="error" role="alert" class="rounded-lg bg-red-50 p-4 text-red-800">{{ error }}</p><p v-if="notice" role="status" class="rounded-lg bg-emerald-50 p-4 text-emerald-800">{{ notice }}</p>
    <section class="card p-5"><div class="flex flex-wrap justify-between gap-3"><h3 class="card-title">版本列表</h3><button class="btn btn-secondary" :disabled="busy" @click="newDraft">新建空白草稿</button></div><p v-if="!versions.length" class="mt-4 text-sm muted">{{ busy ? '加载中…' : '暂无资费版本，可创建草稿。' }}</p>
      <div v-else class="mt-4 overflow-x-auto"><table class="table"><thead><tr><th>版本</th><th>状态</th><th>生效时间（UTC+08）</th><th>修订号</th><th>操作员</th><th>操作</th></tr></thead><tbody><tr v-for="v in versions" :key="v.versionId"><td class="break-all">{{ v.versionId }}</td><td><span class="badge" :class="state(v) === '生效中' ? 'badge-success' : 'badge-neutral'">{{ state(v) }}</span></td><td>{{ formatTime(v.effectiveFrom) }}</td><td>{{ v.revision }}</td><td>{{ v.publishedBy || v.updatedBy }}</td><td><div class="flex flex-wrap gap-2"><button class="btn btn-secondary" :disabled="busy" @click="open(v.versionId)">{{ v.status === 'DRAFT' ? '编辑草稿' : '查看' }}</button><button class="btn btn-ghost" :disabled="busy" @click="copy(v)">复制为新草稿</button></div></td></tr></tbody></table></div>
    </section>
    <section v-if="definition" class="card p-5">
      <div class="flex flex-wrap justify-between gap-3"><h3 class="card-title">{{ selected ? `${selected.versionId} · ${selected.status === 'DRAFT' ? '编辑草稿' : '只读查看'}` : '新建草稿' }}</h3><button class="btn btn-ghost" :disabled="busy" @click="close">关闭</button></div>
      <p v-if="selected" class="mt-3 text-sm muted">修订 {{ selected.revision }} · {{ selected.updatedBy }} 于 {{ formatTime(selected.updatedAt) }} 更新<span v-if="selected.publishedAt"> · {{ selected.publishedBy }} 于 {{ formatTime(selected.publishedAt) }} 发布</span></p>
      <p v-if="dirty" class="mt-3 text-sm text-amber-800">有未保存修改，保存后才能发布。</p>
      <p v-if="conflict" class="mt-3 text-sm text-red-800">服务器版本发生变化或已发布。当前输入已保留，请记录修改后重新加载草稿。</p>
      <button v-if="conflict" class="btn btn-secondary mt-3" :disabled="busy" @click="reloadSelected">重新加载服务器版本</button>
      <form class="mt-5 space-y-5" @submit.prevent="save">
        <label v-if="!selected" class="block">新版本标识<input v-model.trim="newId" class="input mt-1" placeholder="例如 LIANA-TARIFF-2026-002" pattern="[A-Z0-9_-]{1,64}" required maxlength="64" :disabled="busy" /></label>
        <fieldset :disabled="busy || conflict"><TariffDefinitionEditor :key="editorKey" ref="editor" v-model="definition" :readonly="readonly" /></fieldset>
        <div v-if="!readonly" class="flex flex-wrap gap-3"><button class="btn btn-primary" :disabled="busy || conflict">{{ busy ? '处理中…' : selected ? '保存草稿' : '创建草稿' }}</button><button v-if="selected" type="button" class="btn btn-secondary" :disabled="busy || dirty || conflict" @click="confirmPublish = true">发布资费…</button></div>
      </form>
      <section v-if="confirmPublish && !dirty && !readonly" class="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-5" aria-label="确认发布">
        <h4 class="font-bold">确认发布 {{ selected.versionId }}</h4><p class="mt-2 text-sm">包含 {{ definition.products.length }} 个产品、{{ definition.rules.length }} 条规则。发布后不能修改此版本。</p>
        <form class="mt-4 space-y-4" @submit.prevent="publish"><label class="block">生效方式<select v-model="publishMode" class="select mt-1" :disabled="busy"><option value="NOW">立即生效</option><option value="SCHEDULED">预约生效</option></select></label><label v-if="publishMode === 'SCHEDULED'" class="block">生效时间（UTC+08）<input v-model="scheduledAt" class="input mt-1" type="datetime-local" required :disabled="busy" /></label><p class="text-sm text-amber-900">{{ publishMode === 'NOW' ? '立即生效会使旧版本的报价失效。' : '预约版本生效时，旧版本报价失效。' }}生效时间须晚于所有已发布版本，包括待生效版本。</p><div class="flex gap-3"><button class="btn btn-primary" :disabled="busy || conflict">确认发布</button><button type="button" class="btn btn-secondary" :disabled="busy" @click="confirmPublish = false">取消</button></div></form>
      </section>
    </section>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { postageApi, cloneDefinition, versionState, formatTime, publishTime } from '../../lib/postage';
import { useSessionStore } from '../../stores/session';
import TariffDefinitionEditor from '../../components/TariffDefinitionEditor.vue';
const session = useSessionStore();
const versions = ref([]), selected = ref(null), definition = ref(null), baseline = ref(''), newId = ref('');
const busy = ref(false), error = ref(''), notice = ref(''), conflict = ref(false), editorKey = ref(0), editor = ref(null);
const confirmPublish = ref(false), publishMode = ref('NOW'), scheduledAt = ref('');
const readonly = computed(() => selected.value?.status === 'PUBLISHED');
const dirty = computed(() => definition.value && !readonly.value && (!selected.value || JSON.stringify(definition.value) !== baseline.value));
watch(dirty, (value) => { if (value) confirmPublish.value = false; });
const state = (v) => versionState(v, versions.value);
function canDiscard() { return !dirty.value || window.confirm('有未保存修改，确定放弃这些修改吗？'); }
function install(version, d) { selected.value = version; definition.value = cloneDefinition(d); baseline.value = JSON.stringify(definition.value); conflict.value = false; confirmPublish.value = false; editorKey.value++; }
async function run(action) { busy.value = true; error.value = ''; notice.value = ''; try { await action(); } catch (e) { error.value = e.message; if (e.status === 409 && selected.value?.status === 'DRAFT' && /草稿已更新|已发布资费不可修改/.test(e.message)) { conflict.value = true; confirmPublish.value = false; } } finally { busy.value = false; } }
async function refresh() { await run(async () => { versions.value = await postageApi.versions(session.token); }); }
async function open(id) { if (!canDiscard()) return; await run(async () => { const v = await postageApi.version(id, session.token); install(v, v.definition); }); }
async function reloadSelected() { await open(selected.value.versionId); }
function newDraft() { if (!canDiscard()) return; newId.value = ''; install(null, { currency: 'LNR', domesticCountry: 'LN', fallbackZone: 'Z3', countryZones: {}, products: [], rules: [] }); }
async function copy(version) { if (!canDiscard()) return; await run(async () => { const v = await postageApi.version(version.versionId, session.token); newId.value = ''; install(null, v.definition); }); }
function close() { if (canDiscard()) { definition.value = null; selected.value = null; } }
async function save() {
  await run(async () => {
    editor.value.validate();
    const body = cloneDefinition(definition.value);
    const v = selected.value ? await postageApi.update(selected.value.versionId, { revision: selected.value.revision, definition: body }, session.token)
      : await postageApi.create({ versionId: newId.value, definition: body }, session.token);
    install(v, v.definition);
    notice.value = '草稿已保存，可审查后发布。';
    versions.value = await postageApi.versions(session.token);
  });
}
async function publish() {
  await run(async () => {
    if (dirty.value || conflict.value) throw new Error('请先保存草稿并解决版本冲突');
    const effectiveFrom = publishMode.value === 'NOW' ? null : publishTime(scheduledAt.value);
    const v = await postageApi.publish(selected.value.versionId, { revision: selected.value.revision, effectiveFrom }, session.token);
    install(v, v.definition);
    notice.value = `已发布，${formatTime(v.effectiveFrom)} 生效（UTC+08）。`;
    versions.value = await postageApi.versions(session.token);
  });
}
onBeforeRouteLeave(() => canDiscard());
onMounted(refresh);
</script>
