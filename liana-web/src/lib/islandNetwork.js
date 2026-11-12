function formatHours(value) {
  const hours = Number(value);
  return Number.isFinite(hours) ? `${hours} 小时` : '-';
}

function routePath(origin, destination, mode, index) {
  const originX = origin.x ?? origin.mapX;
  const originY = origin.y ?? origin.mapY;
  const destinationX = destination.x ?? destination.mapX;
  const destinationY = destination.y ?? destination.mapY;
  const dx = destinationX - originX;
  const dy = destinationY - originY;
  const length = Math.hypot(dx, dy) || 1;
  const baseOffset = mode === 'AIR' ? 72 : mode === 'SEA' ? 42 : 4;
  const alternatingOffset = (index % 2 === 0 ? 1 : -1) * (index % 3) * 12;
  const offset = baseOffset + alternatingOffset;
  const controlX = (originX + destinationX) / 2 - (dy / length) * offset;
  const controlY = (originY + destinationY) / 2 + (dx / length) * offset;
  return `M${originX} ${originY} Q${controlX.toFixed(1)} ${controlY.toFixed(1)} ${destinationX} ${destinationY}`;
}

export function buildIslandNetwork(payload = {}) {
  const profiles = new Map((payload.facilityProfiles || []).map((item) => [item.facilityCode, item]));
  const routeProfiles = new Map((payload.routeProfiles || []).map((item) => [item.routeCode, item]));
  const regions = (payload.regions || []).map((item) => ({
    code: item.regionCode,
    name: item.regionName,
    postcode: `${item.postcodePrefix}xx`,
    centerFacilityCode: item.centerFacilityCode,
    inhabitedIslands: Number(item.inhabitedIslands || 0),
    coverage: item.coverageDescription,
    facilityCount: 0,
    islandNames: [],
  }));
  const regionByCode = new Map(regions.map((region) => [region.code, region]));
  const islands = (payload.islands || []).map((item) => ({
    code: item.islandCode,
    region: item.regionCode,
    name: item.islandName,
    postcodeRange: item.postcodeRange,
    type: item.islandType,
    mapX: Number(item.mapX),
    mapY: Number(item.mapY),
  }));
  for (const island of islands) regionByCode.get(island.region)?.islandNames.push(island.name);

  const facilities = (payload.facilities || []).flatMap((item) => {
    const profile = profiles.get(item.facilityCode);
    if (!profile || item.status === 0) return [];
    const island = islands.find((entry) => entry.code === profile.islandCode);
    const region = regionByCode.get(island?.region);
    const typeKey = item.typeCode === 'TRANSFER_CENTER' ? 'center'
      : ['INTERNATIONAL_GATEWAY', 'AIR_HUB', 'SEA_HUB'].includes(item.typeCode) ? 'gateway' : 'office';
    if (region) region.facilityCount += 1;
    return [{
      code: item.facilityCode,
      name: item.name,
      nameEn: item.city || item.address || item.facilityCode,
      type: item.typeCode,
      typeLabel: typeKey === 'center' ? (item.facilityCode === 'LN-NAM-HQ' ? '国家转运中心' : '区域处理中心')
        : typeKey === 'gateway' ? ({ INTERNATIONAL_GATEWAY: '国际交换局', AIR_HUB: '航空节点', SEA_HUB: '海运节点' }[item.typeCode])
          : (item.facilityCode.endsWith('-02') && island?.region !== 'NM' ? '岛屿邮政代理点' : '综合邮局'),
      typeKey,
      region: island?.region,
      regionName: region?.name || item.province || '-',
      islandCode: profile.islandCode,
      postcodes: profile.postcodeCoverage,
      coverage: profile.serviceCoverage,
      capacity: profile.capacityDescription,
      hours: profile.openingHours,
      x: Number(profile.mapX),
      y: Number(profile.mapY),
      label: item.facilityCode.replace(/^LN-/, '').replaceAll('-', ' '),
      status: item.status,
    }];
  });
  const facilityByCode = new Map(facilities.map((facility) => [facility.code, facility]));
  const routes = (payload.routes || [])
    .filter((item) => item.routeCode.startsWith('LN-') && item.status !== 0)
    .flatMap((item, index) => {
      const origin = facilityByCode.get(item.originFacilityCode);
      const destination = facilityByCode.get(item.destinationFacilityCode);
      if (!origin || !destination) return [];
      const profile = routeProfiles.get(item.routeCode) || {};
      const mode = String(item.transportMode || 'LAND').toUpperCase();
      return [{
        code: item.routeCode,
        name: `${origin.name} — ${destination.name}`,
        origin: item.originFacilityCode,
        destination: item.destinationFacilityCode,
        mode,
        hours: formatHours(item.estimatedHours),
        distance: Number.isFinite(Number(item.distanceKm)) ? `约 ${Number(item.distanceKm)} km` : '-',
        frequency: profile.frequencyDescription || '班次待配置',
        note: profile.operatingNote || '无补充运营说明',
        path: routePath(origin, destination, mode, index),
      }];
    });

  return { regions, islands, facilities, routes };
}
