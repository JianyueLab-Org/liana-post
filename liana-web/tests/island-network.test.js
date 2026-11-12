import test from 'node:test';
import assert from 'node:assert/strict';
import { facilityApi } from '../src/lib/api.js';
import { buildIslandNetwork } from '../src/lib/islandNetwork.js';

test('群岛网络读取 Facility 聚合接口并附带管理员 JWT', async () => {
  const originalWindow = globalThis.window;
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.window = { location: { origin: 'http://localhost' } };
  globalThis.fetch = async (url, options) => {
    calls.push({ url: String(url), options });
    return new Response(JSON.stringify({ code: 200, data: { regions: [] } }), {
      headers: { 'content-type': 'application/json' },
    });
  };
  try {
    await facilityApi.demoPostalNetwork('admin-token');
    assert.equal(calls[0].url, 'http://localhost/liana-facility-service/api/facilities/demo-network');
    assert.equal(calls[0].options.method, 'GET');
    assert.equal(calls[0].options.headers.Authorization, 'Bearer admin-token');
  } finally {
    globalThis.window = originalWindow;
    globalThis.fetch = originalFetch;
  }
});

test('地图数据由数据库设施、邮编档案和实际路线构成', () => {
  const network = buildIslandNetwork({
    regions: [{ regionCode: 'OR', regionName: 'Oro 北岛群', postcodePrefix: '20', inhabitedIslands: 3 }],
    islands: [{ islandCode: 'OR-MAIN', regionCode: 'OR', islandName: 'Oro', postcodeRange: '2000-2079', mapX: 560, mapY: 130 }],
    facilities: [
      { facilityCode: 'LN-OR-RC', name: 'Oro 区域中心', typeCode: 'TRANSFER_CENTER', city: 'Oro Town', province: 'Oro', status: 1 },
      { facilityCode: 'LN-OR-01', name: 'Oro 邮局', typeCode: 'POST_OFFICE', city: 'Oro Town', province: 'Oro', status: 1 },
      { facilityCode: 'B1', name: '旧演示网点', typeCode: 'POST_OFFICE', city: 'Legacy', status: 1 },
    ],
    routes: [{ routeCode: 'LN-OR-CITY', originFacilityCode: 'LN-OR-RC', destinationFacilityCode: 'LN-OR-01', transportMode: 'LAND', estimatedHours: 0.5, distanceKm: 8, status: 1 }],
    facilityProfiles: [
      { facilityCode: 'LN-OR-RC', islandCode: 'OR-MAIN', postcodeCoverage: '2000-2099', serviceCoverage: 'Oro 全境', capacityDescription: '每日 3,000 件', openingHours: '每日营业', mapX: 560, mapY: 130 },
      { facilityCode: 'LN-OR-01', islandCode: 'OR-MAIN', postcodeCoverage: '2000-2009', serviceCoverage: 'Oro 镇', capacityDescription: '柜台 2 席', openingHours: '工作日营业', mapX: 545, mapY: 145 },
    ],
    routeProfiles: [{ routeCode: 'LN-OR-CITY', frequencyDescription: '每日多班', operatingNote: '城镇集散' }],
  });

  assert.equal(network.regions[0].facilityCount, 2);
  assert.deepEqual(network.regions[0].islandNames, ['Oro']);
  assert.equal(network.facilities.length, 2);
  assert.equal(network.facilities[0].typeKey, 'center');
  assert.equal(network.facilities[1].postcodes, '2000-2009');
  assert.equal(network.routes[0].frequency, '每日多班');
  assert.match(network.routes[0].path, /^M560 130 Q/);
});
