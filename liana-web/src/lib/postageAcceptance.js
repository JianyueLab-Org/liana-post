import { request } from './gatewayApi.js';

const path='/api/postage/acceptances';
export const acceptanceApi={
  accept:(body,token)=>request('postage',path,{method:'POST',body,token}),
  list:(page,token)=>request('postage',path,{query:{page},token}),
  get:(id,token)=>request('postage',`${path}/${encodeURIComponent(id)}`,{token}),
  byRequest:(id,token)=>request('postage',`${path}/by-request/${encodeURIComponent(id)}`,{token}),
  byWaybill:(id,token)=>request('postage',`${path}/by-waybill/${encodeURIComponent(id)}`,{token}),
  resume:(id,token)=>request('postage',`${path}/${encodeURIComponent(id)}/resume`,{method:'POST',token}),
  cancel:(id,token)=>request('postage',`${path}/${encodeURIComponent(id)}/cancel`,{method:'POST',token}),
  validate:(body,token)=>request('oms','/api/oms/acceptances/validate',{method:'POST',body,token}),
};
export const acceptanceStatus=(status)=>({PREPARED:'待恢复收寄',COMPLETED:'已结算收寄',CANCELLED:'已取消'}[status]||status);
export function traditionalLines(lines) {
  if (lines.length>50) throw new Error('传统邮票最多记录 50 行');
  return lines.map(line=>{
    const value=String(line.faceValue).trim(), quantity=Number(line.quantity);
    if (!/^\d+(\.\d{1,2})?$/.test(value) || Number(value)<0.01 || Number(value)>999999.99) throw new Error('传统邮票面额须为 0.01–999999.99，最多两位小数');
    if (!Number.isInteger(quantity) || quantity<1 || quantity>1000) throw new Error('传统邮票张数须为 1–1000 的整数');
    return {faceValue:Number(value).toFixed(2),quantity};
  });
}
export function codeLines(value) {
  const codes=value.split(/\r?\n/).map(v=>v.trim()).filter(Boolean).map(v=>{
    const compact=v.replace(/[\s-]/g,'').toUpperCase();
    if (!/^LP[0-9A-F]{32}$/.test(compact)) throw new Error('请输入完整数码邮票代码，每行一张');
    return `LP-${compact.slice(2).match(/.{8}/g).join('-')}`;
  });
  if (codes.length>100 || new Set(codes).size!==codes.length) throw new Error('最多使用 100 张数码邮票，代码不能重复');
  return codes;
}
export function appliedCents(traditional,stamps) {
  return traditional.reduce((n,l)=>n+Math.round(Number(l.faceValue)*100)*l.quantity,0)
    +stamps.reduce((n,s)=>n+Math.round(Number(s.faceValue)*100),0);
}
// Persist only operation IDs, never addresses, identity numbers or full voucher codes.
export function acceptanceAttemptStore(storage,username) {
  const key=`liana-acceptance:${username}`;
  return {
    read() { try { const v=JSON.parse(storage.getItem(key)||'null'); return v && typeof v.requestId==='string'?v:null; } catch { return null; } },
    write(v) { try { storage.setItem(key,JSON.stringify({requestId:v.requestId,acceptanceId:v.acceptanceId})); } catch { /* Current page retains the IDs. */ } },
    clear() { try { storage.removeItem(key); } catch { /* Optional storage. */ } },
  };
}
