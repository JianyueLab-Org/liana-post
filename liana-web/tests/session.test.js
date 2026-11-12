import test from 'node:test';
import assert from 'node:assert/strict';
import { createPinia } from 'pinia';
import { useSessionStore } from '../src/stores/session.js';

test('缓存令牌遭 profile 401 拒绝后清空会话，允许重新登录', async () => {
  const values = new Map([['liana_token', 'expired-token'], ['liana_user', JSON.stringify({ username: 'sorter001', role: 'SORTER' })]]);
  const originals = { storage: globalThis.localStorage, window: globalThis.window, fetch: globalThis.fetch };
  globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
  globalThis.window = { location: { origin: 'http://localhost' } };
  const paths = [];
  globalThis.fetch = async (url, options) => {
    paths.push(url.pathname);
    assert.equal(url.searchParams.get('username'), 'sorter001');
    assert.equal(options.headers.Authorization, 'Bearer expired-token');
    return new Response(JSON.stringify({ code: 401, message: 'unauthorized' }), { status: 401, headers: { 'content-type': 'application/json' } });
  };
  try {
    const session = useSessionStore(createPinia());
    await assert.rejects(() => session.bootstrap(), e => e.status === 401);
    assert.equal(session.token, '');
    assert.equal(session.user, null);
    assert.equal(values.has('liana_token'), false);
    assert.equal(values.has('liana_user'), false);
    assert.deepEqual(paths, ['/liana-auth-service/api/auth/profile']);
    session.setSession('fresh-token', { username: 'clerk001' });
    globalThis.fetch = async (url, options) => {
      assert.equal(options.headers.Authorization, 'Bearer fresh-token');
      return new Response(JSON.stringify({ code: 200, data: { username: 'clerk001', role: 'CLERK', permissions: ['MAIL_CREATE'] } }), { headers: { 'content-type': 'application/json' } });
    };
    await session.bootstrap();
    assert.equal(session.ready, true);
    assert.equal(session.hasAction('MAIL_CREATE'), true);
    assert.ok(session.menuTree.some(menu => menu.id === 'postage'));
    session.setSession('another-token', { username: 'sorter001' });
    assert.equal(session.ready, false);
    assert.deepEqual(session.actionPermissions, []);
  } finally { globalThis.localStorage = originals.storage; globalThis.window = originals.window; globalThis.fetch = originals.fetch; }
});

test('损坏缓存资料不会阻止登录，缺少用户名的会话被清理', async () => {
  const originals = { storage: globalThis.localStorage };
  const values = new Map([['liana_token', 'old-token'], ['liana_user', '{broken json']]);
  globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
  try {
    const session = useSessionStore(createPinia());
    assert.equal(session.user, null);
    await session.bootstrap();
    assert.equal(session.token, '');
    assert.equal(values.has('liana_token'), false);
  } finally { globalThis.localStorage = originals.storage; }
});
