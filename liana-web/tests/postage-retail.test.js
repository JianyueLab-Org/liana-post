import test from 'node:test';
import assert from 'node:assert/strict';
import { retailApi, retailLines, retailAttemptStore } from '../src/lib/postageRetail.js';
import { buildMenuTree, flattenMenu } from '../src/lib/rbac.js';

const policy = { minFaceValue:'0.01', maxFaceValue:'100.00' };
test('零售金额与张数在发送前验证，面额保留两位小数', () => {
  assert.deepEqual(retailLines([{faceValue:'1.2',quantity:1},{faceValue:0.8,quantity:'2'}], policy), [{faceValue:'1.20',quantity:1},{faceValue:'0.80',quantity:2}]);
  for (const lines of [[],[{faceValue:0,quantity:1}],[{faceValue:'1.001',quantity:1}],[{faceValue:100.01,quantity:1}],[{faceValue:1,quantity:1.1}],[{faceValue:1,quantity:60},{faceValue:1,quantity:41}]]) assert.throws(() => retailLines(lines,policy));
});
test('丢失响应后重试与刷新使用同一请求编号，隔离用户且不缓存凭证', () => {
  const data = new Map();
  const storage={getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)};
  const first=retailAttemptStore(storage,'clerk');
  const lines=retailLines([{faceValue:1,quantity:2}],policy);
  const attempt=first.prepare(lines);
  assert.equal(first.prepare(lines).requestId,attempt.requestId);
  assert.equal(retailAttemptStore(storage,'clerk').prepare(lines).requestId,attempt.requestId);
  assert.equal(retailAttemptStore(storage,'another').read(),null);
  first.write({...attempt,saleId:'sale-id'});
  assert.equal(first.read().saleId,'sale-id');
  first.clear(); assert.equal(first.read(),null);
  storage.setItem('liana-postage-retail:clerk','broken'); assert.equal(first.read(),null);
});
test('零售与发行规则菜单遵循角色和收寄权限', () => {
  for (const [role,permissions,retail,admin] of [['ADMIN',[],true,true],['CLERK',['MAIL_CREATE'],true,false],['MANAGER',['MAIL_CREATE'],true,false],['CLERK',[],false,false],['SORTER',['MAIL_CREATE'],false,false]]) {
    const paths=flattenMenu(buildMenuTree({role,permissions})).map(m=>m.path);
    assert.equal(paths.includes('/postage/retail'),retail);
    assert.equal(paths.includes('/postage/stamp-policy'),admin);
  }
});
test('收款重试沿用业务请求编号，查询凭证用 POST 避免代码出现在 URL',async()=>{
  const previousFetch=globalThis.fetch,previousWindow=globalThis.window;
  globalThis.window={location:{origin:'http://localhost'}};
  const calls=[];
  globalThis.fetch=async(url,options)=>{calls.push({path:url.pathname,...options,body:options.body?JSON.parse(options.body):null});return new Response(JSON.stringify({code:200,data:{ok:true}}),{headers:{'content-type':'application/json'}});};
  try {
    const body={requestId:'cash-sale',amountReceived:'2.80'};
    await retailApi.confirm('sale',body,'token'); await retailApi.confirm('sale',body,'token');
    await retailApi.lookup('LP-test-code','token');
    assert.deepEqual(calls[0].body,calls[1].body);
    assert.equal(calls[0].headers.Authorization,'Bearer token');
    assert.equal(calls[2].method,'POST'); assert.equal(calls[2].path,'/liana-postage-service/api/postage/stamps/lookup');
    assert.equal(calls[2].body.code,'LP-test-code');
  } finally {globalThis.fetch=previousFetch;globalThis.window=previousWindow;}
});
