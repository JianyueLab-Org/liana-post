import test from 'node:test';
import assert from 'node:assert/strict';
import {traditionalLines,codeLines,appliedCents,acceptanceAttemptStore,acceptanceApi} from '../src/lib/postageAcceptance.js';
test('传统与数码邮票按分求和，保留溢贴且拒绝重复代码',()=>{
  const lines=traditionalLines([{faceValue:'1.2',quantity:1},{faceValue:'0.80',quantity:2}]);
  assert.equal(appliedCents(lines,[{faceValue:'0.01'}]),281);
  assert.throws(()=>traditionalLines([{faceValue:'1.234',quantity:1}]));assert.throws(()=>traditionalLines([{faceValue:1,quantity:1.5}]));
  const code='LP-12345678-12345678-12345678-12345678';assert.deepEqual(codeLines(code.toLowerCase().replaceAll('-',' ')),[code]);assert.throws(()=>codeLines(`${code}\n${code.toLowerCase()}`));
});
test('恢复只存请求编号，隔离员工，不缓存身份地址或邮票代码',()=>{
  const map=new Map(),storage={getItem:k=>map.get(k),setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};
  const saved=acceptanceAttemptStore(storage,'clerk');saved.write({requestId:'retry-1',acceptanceId:'one',mail:{senderIdNumber:'private'},stampCodes:['secret']});
  assert.deepEqual(saved.read(),{requestId:'retry-1',acceptanceId:'one'});assert.equal(acceptanceAttemptStore(storage,'other').read(),null);assert.ok(![...map.values()][0].includes('private'));saved.clear();assert.equal(saved.read(),null);
});
test('收寄只通过结算接口，恢复与取消沿用编号和 JWT',async()=>{
  globalThis.window={location:{origin:'http://localhost'}};const calls=[];globalThis.fetch=async(url,opts)=>{calls.push({url:String(url),opts});return new Response(JSON.stringify({code:200,data:{status:'PREPARED'}}),{headers:{'Content-Type':'application/json'}});};
  await acceptanceApi.accept({requestId:'same'},'test-token');await acceptanceApi.resume('id','test-token');await acceptanceApi.cancel('id','test-token');await acceptanceApi.byRequest('same','test-token');
  assert.equal(calls[0].url,'http://localhost/liana-postage-service/api/postage/acceptances');assert.equal(calls[0].opts.method,'POST');assert.equal(JSON.parse(calls[0].opts.body).requestId,'same');assert.ok(calls[1].url.endsWith('/id/resume'));assert.ok(calls[2].url.endsWith('/id/cancel'));assert.ok(calls[3].url.endsWith('/by-request/same'));assert.equal(calls[0].opts.headers.Authorization,'Bearer test-token');
});
