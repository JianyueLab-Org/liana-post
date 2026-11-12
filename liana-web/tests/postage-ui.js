// Development-only browser test. No real JWT, database writes or production data.
import { createApp, nextTick } from 'vue';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import QuoteView from '../src/views/postage/PostageQuoteView.vue';
import TariffsView from '../src/views/postage/PostageTariffsView.vue';
import { useSessionStore } from '../src/stores/session';
import definition from '../../liana-postage-service/src/main/resources/initial-tariff.json';
import '../src/style.css';

const results = [], originalFetch = window.fetch;
const root = document.querySelector('#test-app');
let app, versions, quotes, requests;
const version = () => ({ versionId: 'LIANA-TARIFF-2026-001', status: 'PUBLISHED', revision: 2, definition: structuredClone(definition), effectiveFrom: '2026-10-14T16:00:00Z', updatedBy: 'SYSTEM', updatedAt: '2026-10-14T16:00:00Z' });
const assert = (condition, message) => { if (!condition) throw new Error(message); };
async function flush() { await new Promise(resolve => setTimeout(resolve, 30)); await nextTick(); }
function click(text) { const el = [...root.querySelectorAll('button')].find(b => b.textContent.includes(text)); assert(el && !el.disabled, `找不到可用按钮 ${text}`); el.click(); }
function input(el, value) { assert(el, '输入控件缺失'); el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); }
window.fetch = async (url, options = {}) => {
  const path = new URL(url, window.location.origin).pathname.replace('/liana-postage-service/api/postage', '');
  const body = options.body ? JSON.parse(options.body) : null;
  requests.push({ path, method: options.method, body });
  let data;
  if (path === '/tariffs/current') data = versions.find(v => v.status === 'PUBLISHED');
  else if (path === '/tariffs' && options.method === 'POST') {
    data = { versionId: body.versionId, status: 'DRAFT', revision: 1, definition: body.definition, updatedBy: 'ui-test', updatedAt: new Date().toISOString() };
    versions.push(data);
  } else if (path === '/tariffs') data = versions;
  else if (path.startsWith('/tariffs/')) {
    const id = path.split('/')[2];
    data = versions.find(v => v.versionId === id);
    if (path.endsWith('/publish')) { assert(body.revision === data.revision, '发布修订号错误'); data.status = 'PUBLISHED'; data.revision++; data.effectiveFrom = body.effectiveFrom || new Date().toISOString(); }
    else if (options.method === 'PUT') { assert(body.revision === data.revision, '更新修订号错误'); data.definition = body.definition; data.revision++; }
  } else if (path === '/quotes') {
    assert(body.product === 'C' && body.weightGrams === 1001, '报价输入未同步');
    data = { quoteId: 'ui-quote', tariffVersion: version().versionId, currency: 'LNR', input: body, calculation: { ruleId: 'D_C_AIR_DOM', algorithm: 'FIRST_STEP', zone: 'DOM', additionalUnits: 1, baseAmount: 11, totalAmount: 11, fees: [] }, createdBy: 'ui-test', createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 1800000).toISOString() };
    quotes[data.quoteId] = data;
  } else if (path.startsWith('/quotes/')) data = { snapshot: quotes[path.split('/')[2]], valid: true };
  else throw new Error(`未预期接口 ${path}`);
  return new Response(JSON.stringify({ code: 200, data }), { headers: { 'content-type': 'application/json' } });
};
async function mount(component) {
  if (app) app.unmount();
  root.innerHTML = '';
  const pinia = createPinia();
  const session = useSessionStore(pinia);
  session.$patch({ token: 'isolated-ui-test', user: { role: 'ADMIN', username: 'ui-test', permissions: [] }, ready: true });
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component }] });
  await router.push('/');
  const { h } = await import('vue');
  const { RouterView } = await import('vue-router');
  app = createApp({ render: () => h(RouterView) }).use(pinia).use(router);
  app.mount(root);
  await flush();
}
async function run() {
  versions = [version()]; quotes = {}; requests = [];
  await mount(QuoteView);
  assert(root.textContent.includes('LIANA-TARIFF-2026-001'), '当前资费未加载');
  const selects = root.querySelectorAll('form select');
  input(selects[0], 'C'); await flush();
  input(root.querySelector('input[type="number"]'), '1001');
  await flush();
  root.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); await flush();
  assert(root.textContent.includes('LNR 11.00') && root.textContent.includes('ui-quote'), `报价结果未显示：${root.textContent}`);
  click('重新检查此报价'); await flush();
  assert(requests.filter(r => r.path === '/quotes/ui-quote').length === 2, '报价有效性未重新查询');
  results.push('PASS 营业员报价输入、结果明细及有效性查询');
  await mount(TariffsView);
  click('查看'); await flush();
  const ruleSelect = root.querySelector('form select');
  assert(ruleSelect && !ruleSelect.disabled, '已发布版本不能切换查看规则');
  input(ruleSelect, '1'); await flush();
  assert(root.querySelector('fieldset[disabled]'), '已发布价格可被编辑');
  results.push('PASS 已发布版本只读且可切换规则');
  click('复制为新草稿'); await flush();
  input(root.querySelector('input[placeholder^="例如"]'), 'UI_V2');
  await flush();
  root.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); await flush();
  assert(root.textContent.includes('草稿已保存'), '草稿没有保存');
  click('发布资费'); await flush();
  assert(root.textContent.includes('确认发布 UI_V2'), '缺少发布确认');
  const publishForm = root.querySelector('[aria-label="确认发布"] form');
  publishForm.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true })); await flush();
  assert(root.textContent.includes('已发布，') && versions.find(v => v.versionId === 'UI_V2').status === 'PUBLISHED', '发布未完成');
  results.push('PASS 复制、保存草稿、带修订号确认发布');
}
run().then(() => { document.querySelector('#results').textContent = results.join('\n') + '\n全部通过（模拟接口，非真实服务联调）'; }).catch(e => { document.querySelector('#results').textContent = results.join('\n') + `\nFAIL ${e.message}`; }).finally(() => { window.fetch = originalFetch; if (app) app.unmount(); });
