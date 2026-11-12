import { request } from './gatewayApi.js';

const endpoint = '/api/postage';
export const retailApi = {
  policy: (token) => request('postage', `${endpoint}/stamp-policy`, { token }),
  updatePolicy: (body, token) => request('postage', `${endpoint}/stamp-policy`, { method: 'PUT', body, token }),
  policyAudit: (token) => request('postage', `${endpoint}/stamp-policy/audit`, { token }),
  create: (body, token) => request('postage', `${endpoint}/sales`, { method: 'POST', body, token }),
  sales: (page, token) => request('postage', `${endpoint}/sales`, { query: { page, size: 20 }, token }),
  sale: (id, token) => request('postage', `${endpoint}/sales/${encodeURIComponent(id)}`, { token }),
  confirm: (id, body, token) => request('postage', `${endpoint}/sales/${encodeURIComponent(id)}/confirm-cash`, { method: 'POST', body, token }),
  cancel: (id, token) => request('postage', `${endpoint}/sales/${encodeURIComponent(id)}/cancel`, { method: 'POST', token }),
  lookup: (code, token) => request('postage', `${endpoint}/stamps/lookup`, { method: 'POST', body: { code }, token }),
  voidStamp: (id, body, token) => request('postage', `${endpoint}/stamps/${encodeURIComponent(id)}/void`, { method: 'POST', body, token }),
};

export const saleStatus = (status) => ({ PENDING_PAYMENT: '待收款', PAID: '已收款并发行', CANCELLED: '已取消' }[status] || status);
export const stampStatus = (status) => ({ ISSUED: '已发行', RESERVED: '收寄预占', EXPIRED: '已过期', VOIDED: '已作废', REDEEMED: '已核销' }[status] || status);
export const auditType = (type) => ({ SALE_CREATED: '创建售卖', CASH_CONFIRMED: '确认柜台收款', STAMP_ISSUED: '发行凭证', STAMP_VOIDED: '作废凭证', STAMP_RESERVED: '收寄预占', STAMP_REDEEMED: '收寄核销', STAMP_RELEASED: '取消收寄并释放', SALE_CANCELLED: '取消售卖', POLICY_UPDATED: '调整发行规则' }[type] || type);

export function retailLines(lines, policy) {
  if (!lines.length || lines.length > 50) throw new Error('请输入 1–50 行面额');
  let count = 0;
  const normalized = lines.map(line => {
    const value = String(line.faceValue).trim();
    if (!/^\d+(\.\d{1,2})?$/.test(value)) throw new Error('面额须为正数且最多两位小数');
    const amount = Number(value);
    if (amount < Number(policy.minFaceValue) || amount > Number(policy.maxFaceValue)) throw new Error('面额超出当前发行范围');
    const quantity = Number(line.quantity);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 100) throw new Error('张数须为 1–100 的整数');
    count += quantity;
    return { faceValue: amount.toFixed(2), quantity };
  });
  if (count > 100) throw new Error('单次售卖最多发行 100 张');
  return normalized;
}

// Keep the create request ID after a lost response or reload. Never cache voucher codes.
export function retailAttemptStore(storage, username) {
  const key = `liana-postage-retail:${username}`;
  const read = () => {
    try {
      const saved = JSON.parse(storage.getItem(key) || 'null');
      return saved && typeof saved.requestId === 'string' && Array.isArray(saved.lines) ? saved : null;
    } catch { return null; }
  };
  const write = (attempt) => { try { storage.setItem(key, JSON.stringify(attempt)); } catch { /* Backend keys still protect retries in this page. */ } };
  return {
    read, write,
    clear: () => { try { storage.removeItem(key); } catch { /* Storage is optional. */ } },
    prepare(lines) {
      const previous = read();
      const signature = JSON.stringify(lines);
      const attempt = previous && JSON.stringify(previous.lines) === signature && !previous.saleId
        ? previous : { requestId: crypto.randomUUID(), lines };
      write(attempt);
      return attempt;
    },
  };
}
