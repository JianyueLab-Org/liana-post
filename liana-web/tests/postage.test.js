import test from 'node:test';
import assert from 'node:assert/strict';
import { canUsePostage, postageApi, versionState, publishTime, cloneDefinition, letterProducts, letterRuleProduct, additionalServiceOptions } from '../src/lib/postage.js';
import { buildMenuTree, flattenMenu } from '../src/lib/rbac.js';

test('普通信函目录兼容历史 R，挂号仅出现在适用规则中', () => {
  const products = [{ code: 'R', name: '挂号信' }, { code: 'C', name: '包裹' }];
  assert.deepEqual(letterProducts(products).map(p => p.code), ['U', 'C']);
  assert.equal(products[0].code, 'R');
  const definition = { rules: [{ product: 'R', fees: [{ code: 'REGISTRATION', amount: 3 }] }] };
  assert.equal(letterRuleProduct(definition, 'U'), 'R');
  assert.deepEqual(additionalServiceOptions(definition.rules[0], 'U'), [{ code: 'REGISTRATION', name: '挂号' }]);
  assert.deepEqual(additionalServiceOptions(definition.rules[0], 'C'), []);
  assert.deepEqual(additionalServiceOptions({ fees: [] }, 'U'), []);
});

test('邮资菜单与后端角色权限一致，SORTER 和缺少收寄权限的人员无法进入', () => {
  for (const [role, permissions, allowed, admin] of [
    ['ADMIN', [], true, true], ['CLERK', ['MAIL_CREATE'], true, false],
    ['MANAGER', ['MAIL_CREATE'], true, false], ['SORTER', ['MAIL_CREATE'], false, false],
    ['CLERK', [], false, false], ['CUSTOMER', ['MAIL_CREATE'], false, false],
  ]) {
    const profile = { role, permissions };
    assert.equal(canUsePostage(profile), allowed);
    const paths = flattenMenu(buildMenuTree(profile)).map(item => item.path);
    assert.equal(paths.includes('/postage/quote'), allowed);
    assert.equal(paths.includes('/postage/tariffs'), admin);
    assert.equal(paths.includes('/facility/island-network'), admin);
  }
});

test('版本在准确生效边界切换，草稿不覆盖已发布资费', () => {
  const old = { status: 'PUBLISHED', effectiveFrom: '2026-10-14T16:00:00Z' };
  const next = { status: 'PUBLISHED', effectiveFrom: '2026-10-15T16:00:00Z' };
  const draft = { status: 'DRAFT' };
  const versions = [old, next, draft];
  const before = Date.parse('2026-10-15T15:59:59Z');
  assert.equal(versionState(old, versions, before), '生效中');
  assert.equal(versionState(next, versions, before), '待生效');
  assert.equal(versionState(draft, versions, before), '草稿');
  const boundary = Date.parse(next.effectiveFrom);
  assert.equal(versionState(old, versions, boundary), '历史');
  assert.equal(versionState(next, versions, boundary), '生效中');
});

test('预约始终按 UTC+08 解释并拒绝过去时间', () => {
  assert.equal(publishTime('2099-10-16T00:00'), '2099-10-15T16:00:00.000Z');
  assert.throws(() => publishTime('2000-01-01T00:00'), /预约时间/);
  assert.throws(() => publishTime(''), /请选择/);
});

test('复制草稿不改变原始已发布定义的规则及金额', () => {
  const original = { rules: [{ fees: [{ amount: '3.00' }] }] };
  const copy = cloneDefinition(original);
  copy.rules[0].fees[0].amount = 9;
  assert.equal(original.rules[0].fees[0].amount, '3.00');
});

test('报价和资费编辑使用网关前缀、JWT、修订号并保留 409 错误', async () => {
  const originalWindow = globalThis.window, originalFetch = globalThis.fetch;
  globalThis.window = { location: { origin: 'http://localhost' } };
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ path: url.pathname, ...options, body: options.body ? JSON.parse(options.body) : null });
    return new Response(JSON.stringify({ code: 200, data: { ok: true } }), { headers: { 'content-type': 'application/json' } });
  };
  try {
    const input = { product: 'C', scope: 'DOMESTIC', mode: 'AIR', destinationCountry: 'LN', weightGrams: 1001 };
    await postageApi.quote(input, 'test-token');
    await postageApi.update('V2', { revision: 4, definition: { rules: [] } }, 'test-token');
    await postageApi.publish('V2', { revision: 5, effectiveFrom: null }, 'test-token');
    await postageApi.findQuote('quote/id', 'test-token');
    assert.equal(calls[0].path, '/liana-postage-service/api/postage/quotes');
    assert.equal(calls[0].headers.Authorization, 'Bearer test-token');
    assert.deepEqual(calls[0].body, input);
    assert.equal(calls[1].method, 'PUT');
    assert.equal(calls[1].body.revision, 4);
    assert.equal(calls[2].body.effectiveFrom, null);
    assert.equal(calls[3].path, '/liana-postage-service/api/postage/quotes/quote%2Fid');
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 409, message: '草稿已更新，请重新读取版本' }), { status: 409, headers: { 'content-type': 'application/json' } });
    await assert.rejects(() => postageApi.update('V2', { revision: 4 }, 'test-token'), e => e.status === 409 && /草稿已更新/.test(e.message));
  } finally { globalThis.window = originalWindow; globalThis.fetch = originalFetch; }
});
