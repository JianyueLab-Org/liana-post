import { request } from './gatewayApi.js';

const endpoint = '/api/postage';
export const postageApi = {
  current: (token) => request('postage', `${endpoint}/tariffs/current`, { token }),
  versions: (token) => request('postage', `${endpoint}/tariffs`, { token }),
  version: (id, token) => request('postage', `${endpoint}/tariffs/${encodeURIComponent(id)}`, { token }),
  create: (body, token) => request('postage', `${endpoint}/tariffs`, { method: 'POST', body, token }),
  update: (id, body, token) => request('postage', `${endpoint}/tariffs/${encodeURIComponent(id)}`, { method: 'PUT', body, token }),
  publish: (id, body, token) => request('postage', `${endpoint}/tariffs/${encodeURIComponent(id)}/publish`, { method: 'POST', body, token }),
  quote: (body, token) => request('postage', `${endpoint}/quotes`, { method: 'POST', body, token }),
  findQuote: (id, token) => request('postage', `${endpoint}/quotes/${encodeURIComponent(id)}`, { token }),
};

export function canUsePostage(profile = {}) {
  return profile.role === 'ADMIN' || (['CLERK', 'MANAGER'].includes(profile.role)
    && (profile.permissions || []).includes('MAIL_CREATE'));
}
export const cloneDefinition = (value) => JSON.parse(JSON.stringify(value));
// Published legacy R versions remain immutable. Their base rate also describes ordinary letters.
export function letterProducts(products = []) {
  const result = products.filter(p => p.code !== 'R');
  if (!result.some(p => p.code === 'U') && products.some(p => p.code === 'R')) result.unshift({ code: 'U', name: '信函' });
  return result;
}
export function letterRuleProduct(definition, product) {
  return product === 'U' && !definition.rules.some(r => r.product === 'U') ? 'R' : product;
}
export function additionalServiceOptions(rule, product) {
  return product === 'U' && rule?.fees?.some(f => f.code === 'REGISTRATION') ? [{ code: 'REGISTRATION', name: '挂号' }] : [];
}
export const money = (value) => Number(value ?? 0).toFixed(2);
export function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Hong_Kong', hour12: false }) : '—';
}
export function versionState(version, versions, now = Date.now()) {
  if (version.status === 'DRAFT') return '草稿';
  const start = Date.parse(version.effectiveFrom);
  if (start > now) return '待生效';
  const replaced = versions.some((v) => v.status === 'PUBLISHED'
    && Date.parse(v.effectiveFrom) > start && Date.parse(v.effectiveFrom) <= now);
  return replaced ? '历史' : '生效中';
}
// The scheduling form explicitly uses UTC+08 rather than the browser's local zone.
export function publishTime(value) {
  if (!value) throw new Error('请选择预约生效时间');
  const date = new Date(`${value}+08:00`);
  if (!Number.isFinite(date.getTime()) || date.getTime() <= Date.now()) throw new Error('预约时间必须晚于当前时间（UTC+08）');
  return date.toISOString();
}
