<template>
  <section class="network-panel">
    <header class="network-heading">
      <div>
        <div class="network-eyebrow"><span class="live-dot"></span> Liana Pacific · 模拟规划网络</div>
        <h2>群岛邮政覆盖图</h2>
        <p>服务岛屿、处理设施与基础邮路 · 数据来自 demo MySQL</p>
      </div>
      <div class="map-actions">
        <span class="scenario-tag">非实时运营数据</span>
        <button class="icon-button" title="重置地图筛选" aria-label="重置地图筛选" @click="resetFilters">↺</button>
      </div>
    </header>

    <div class="map-toolbar">
      <div class="map-filters" aria-label="筛选邮路运输方式">
        <button v-for="mode in modes" :key="mode.id" class="filter-button" :class="{ active: selectedMode === mode.id }" @click="selectedMode = mode.id">
          <span class="mode-mark" :class="`mode-${mode.id.toLowerCase()}`"></span>{{ mode.label }}
        </button>
      </div>
      <label class="region-select-wrap">
        <span>区域</span>
        <select v-model="selectedRegion" aria-label="按岛群筛选">
          <option value="ALL">全部岛群</option>
          <option v-for="region in regions" :key="region.code" :value="region.code">{{ region.name }}</option>
        </select>
      </label>
    </div>

    <div class="network-layout">
      <div class="map-stage" aria-label="利亚纳群岛设施和邮路地图">
        <div v-if="loading || loadError" class="map-load-state" role="status">
          <template v-if="loading"><span class="loading-mark"></span><span>正在读取邮政网络数据</span></template>
          <template v-else><strong>邮政网络数据暂不可用</strong><span>{{ loadError }}</span><button class="retry-button" @click="loadNetwork">重试</button></template>
        </div>
        <div class="map-water-label water-west">SOUTH PACIFIC OCEAN</div>
        <div class="map-water-label water-east">PACIFIC · LNPS NETWORK</div>
        <svg class="island-map" viewBox="0 0 1000 600" role="img" aria-label="Namoa、Oro、Miri、Sela 四个邮政区域及海陆运输路线">
          <defs>
            <pattern id="seaGrid" width="42" height="42" patternUnits="userSpaceOnUse">
              <path d="M42 0H0V42" fill="none" stroke="#8eb9b6" stroke-opacity=".14" stroke-width="1" />
            </pattern>
            <filter id="islandShadow" x="-30%" y="-30%" width="160%" height="170%">
              <feDropShadow dx="0" dy="10" stdDeviation="9" flood-color="#1e4b46" flood-opacity=".18" />
            </filter>
            <marker id="arrow-air" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#cb5b45" /></marker>
            <marker id="arrow-sea" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#397b9b" /></marker>
            <marker id="arrow-land" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#56825c" /></marker>
          </defs>
          <rect width="1000" height="600" fill="#dcebe4" />
          <rect width="1000" height="600" fill="url(#seaGrid)" />
          <path class="sea-current" d="M35 445C165 385 205 498 335 445S526 392 626 444s228 52 344-5" />
          <path class="sea-current current-two" d="M68 160c90-40 154-4 226-31s138-44 205-14" />

          <g class="island-group" :class="{ 'region-muted': !regionVisible('NM') }">
            <path class="landmass namoa" filter="url(#islandShadow)" d="M116 170l20-22 28 4 19-14 27 7 9 21 25 4 8 19-14 20 14 17-6 26-20 12-7 24-23 11-10 26-28 7-14-13-24 6-17-12 3-26-18-15 7-23-17-22 13-22-8-22 18-16z" />
            <path class="land-detail" d="M155 172l19 13-7 21 22 13-7 22 16 17-14 21m31-105-15 20 13 19-17 17 9 18m-48-9-18 14 17 15-9 20" />
            <circle class="town-dot" cx="173" cy="236" r="3" /><circle class="town-dot" cx="216" cy="204" r="2.5" /><circle class="town-dot" cx="142" cy="277" r="2.5" />
            <path class="landmass islet" d="M260 223l8-6 10 3 2 9-9 7-10-4z" /><path class="landmass islet" d="M278 249l7-5 8 4-2 8-9 1z" />
          </g>
          <g class="island-group" :class="{ 'region-muted': !regionVisible('OR') }">
            <path class="landmass oro" filter="url(#islandShadow)" d="M520 88l18-18 23 4 13-11 22 9 8 19 18 5 7 20-15 18-1 20-20 11-11 20-24 5-13-13-20 3-17-14 4-20-14-15 12-17-7-16 17-10z" />
            <path class="land-detail" d="M549 88l14 12-8 18 18 13-12 20m33-48-14 17 11 17-15 18m-36-16-12 13 13 11-8 13" />
            <path class="landmass islet" d="M625 115l10-7 12 4 5 12-10 10-13-3z" /><path class="landmass islet" d="M650 156l9-5 10 6-3 10-12 1z" />
            <circle class="town-dot" cx="566" cy="130" r="3" /><circle class="town-dot" cx="608" cy="164" r="2.5" />
          </g>
          <g class="island-group" :class="{ 'region-muted': !regionVisible('MI') }">
            <path class="landmass miri" filter="url(#islandShadow)" d="M704 277l20-17 27 6 17-10 22 12 6 23 19 11 2 20-17 15-4 22-23 11-13 19-24 1-11-15-21 1-15-17 7-20-11-18 14-15-4-19 15-10z" />
            <path class="land-detail" d="M737 277l13 16-10 17 15 16-9 20m38-50-13 19 10 18-17 15m-39-6-14 15 14 11-5 12" />
            <path class="landmass islet" d="M807 348l9-8 12 4 2 11-11 8-11-5z" /><path class="landmass islet" d="M829 374l8-5 8 7-4 9-10-1z" />
            <circle class="town-dot" cx="758" cy="323" r="3" /><circle class="town-dot" cx="788" cy="358" r="2.5" />
          </g>
          <g class="island-group" :class="{ 'region-muted': !regionVisible('SE') }">
            <path class="landmass sela" filter="url(#islandShadow)" d="M475 407l19-16 22 6 17-12 19 14 2 20 17 12-3 20-17 11-8 21-22 7-15-10-19 6-15-13 3-19-13-14 10-17-7-15z" />
            <path class="land-detail" d="M504 407l12 14-9 16 14 14-9 20m37-51-14 17 9 17-17 12m-36-4-12 12 12 10-7 10" />
            <path class="landmass islet" d="M547 491l9-7 11 5 1 10-12 7-10-5z" /><path class="landmass islet" d="M567 514l8-5 9 6-3 9-10 1z" />
            <circle class="town-dot" cx="517" cy="444" r="3" /><circle class="town-dot" cx="545" cy="480" r="2.5" />
          </g>

          <text v-for="island in islands" :key="island.code" class="island-name" :class="{ 'island-muted': !regionVisible(island.region) }" :x="island.mapX" :y="island.mapY" text-anchor="middle">{{ island.name }}</text>

          <g class="routes-layer">
            <path v-for="route in visibleRoutes" :key="route.code" class="route-line" :class="[`route-${route.mode.toLowerCase()}`, { selected: selectedRoute?.code === route.code }]" :d="route.path" :marker-end="`url(#arrow-${route.mode.toLowerCase()})`" @click="selectRoute(route)" />
          </g>
          <g v-for="facility in visibleFacilities" :key="facility.code" class="facility-marker" :class="[`type-${facility.typeKey}`, { selected: selectedFacility?.code === facility.code }]" role="button" tabindex="0" :aria-label="`查看${facility.name}`" @click="selectFacility(facility)" @keydown.enter="selectFacility(facility)">
            <circle class="marker-halo" :cx="facility.x" :cy="facility.y" r="15" />
            <circle class="marker-core" :cx="facility.x" :cy="facility.y" r="8" />
            <path v-if="facility.typeKey === 'center'" class="marker-symbol" :d="`M${facility.x - 3} ${facility.y - 3}h6v6h-6z`" />
            <path v-else-if="facility.typeKey === 'gateway'" class="marker-symbol" :d="`M${facility.x} ${facility.y - 4}l4 4-4 4-4-4z`" />
            <text v-if="facility.label" class="marker-label" :x="facility.x + 12" :y="facility.y - 8">{{ facility.label }}</text>
          </g>
          <g class="map-compass" transform="translate(943 61)"><circle r="25" /><path d="M0-17L5 4 0 1-5 4z" /><text x="0" y="38">N</text></g>
          <g class="map-scale" transform="translate(37 558)"><path d="M0 0h60m-60-4v8m60-8v8" /><text x="30" y="-9" text-anchor="middle">约 100 km</text></g>
        </svg>
        <div class="map-legend">
          <span><i class="legend-node center"></i>处理中心</span><span><i class="legend-node gateway"></i>交换/运输设施</span><span><i class="legend-node office"></i>邮局/代理点</span>
        </div>
      </div>

      <aside class="network-details">
        <template v-if="selectedFacility">
          <div class="detail-topline"><span class="detail-kicker">设施详情</span><button class="plain-button" aria-label="关闭设施详情" @click="selectedFacility = null">×</button></div>
          <div class="facility-title-row"><span class="facility-type-icon" :class="`type-${selectedFacility.typeKey}`">{{ typeGlyph(selectedFacility.typeKey) }}</span><div><h3>{{ selectedFacility.name }}</h3><p>{{ selectedFacility.nameEn }}</p></div></div>
          <div class="detail-code-row"><span>{{ selectedFacility.code }}</span><span class="status-indicator"><i></i> {{ selectedFacility.status === 1 ? '服务中' : '暂停服务' }}</span></div>
          <dl class="detail-list">
            <div><dt>设施类型</dt><dd>{{ selectedFacility.typeLabel }}</dd></div><div><dt>所属区域</dt><dd>{{ selectedFacility.regionName }}</dd></div>
            <div><dt>服务邮编</dt><dd>{{ selectedFacility.postcodes }}</dd></div><div><dt>服务范围</dt><dd>{{ selectedFacility.coverage }}</dd></div>
            <div><dt>处理能力</dt><dd>{{ selectedFacility.capacity }}</dd></div><div><dt>运营时间</dt><dd>{{ selectedFacility.hours }}</dd></div>
          </dl>
          <div class="detail-block"><h4>相连邮路 <span>{{ connectedRoutes.length }}</span></h4><button v-for="route in connectedRoutes" :key="route.code" class="connected-route" @click="selectRoute(route)"><span class="connected-mode" :class="`mode-${route.mode.toLowerCase()}`">{{ modeName(route.mode) }}</span><span class="connected-route-name">{{ route.name }}</span><span class="connected-hours">{{ route.hours }}</span></button><p v-if="!connectedRoutes.length" class="empty-note">当前筛选条件下无相连路线</p></div>
        </template>
        <template v-else-if="selectedRoute">
          <div class="detail-topline"><span class="detail-kicker">邮路详情</span><button class="plain-button" aria-label="关闭邮路详情" @click="selectedRoute = null">×</button></div>
          <div class="route-title-row"><span class="route-mode-icon" :class="`mode-${selectedRoute.mode.toLowerCase()}`">{{ modeGlyph(selectedRoute.mode) }}</span><div><h3>{{ selectedRoute.name }}</h3><p>{{ selectedRoute.code }}</p></div></div>
          <div class="route-status"><span class="status-indicator"><i></i> 计划运行</span><span class="mode-long">{{ modeName(selectedRoute.mode) }}干线</span></div>
          <div class="route-stops"><div v-for="(stop, index) in routeStops(selectedRoute)" :key="stop.code" class="route-stop"><i :class="{ end: index === 1 }"></i><div><span>{{ index === 0 ? '始发设施' : '到达设施' }}</span><strong>{{ stop.name }}</strong><small>{{ stop.code }}</small></div></div></div>
          <dl class="detail-list route-details"><div><dt>计划时长</dt><dd>{{ selectedRoute.hours }}</dd></div><div><dt>班次频率</dt><dd>{{ selectedRoute.frequency }}</dd></div><div><dt>路线里程</dt><dd>{{ selectedRoute.distance }}</dd></div><div><dt>运营说明</dt><dd>{{ selectedRoute.note }}</dd></div></dl>
          <div class="route-note"><strong>时效说明</strong><p>计划时长不含截邮等待、装卸与末端投递；天气和班次变化可能导致延误。</p></div>
        </template>
        <template v-else>
          <div class="detail-topline"><span class="detail-kicker">网络概况</span><span class="network-version">LN · 01</span></div>
          <h3 class="overview-title">一国四区<br />群岛邮政网络</h3>
          <p class="overview-copy">选择地图上的设施或邮路线，查看服务覆盖和规划运输信息。</p>
          <div class="coverage-stat-grid"><div><strong>{{ facilities.length }}</strong><span>服务设施</span></div><div><strong>{{ islands.length }}</strong><span>覆盖岛屿</span></div><div><strong>{{ routes.length }}</strong><span>基础邮路</span></div><div><strong>{{ regions.length }}</strong><span>邮政区域</span></div></div>
          <div class="region-list"><h4>邮政区域 <span>邮编范围</span></h4><button v-for="region in regions" :key="region.code" :class="{ chosen: selectedRegion === region.code }" @click="selectedRegion = selectedRegion === region.code ? 'ALL' : region.code"><span class="region-dot" :class="`region-${region.code.toLowerCase()}`"></span><span class="region-name">{{ region.name }}</span><span class="region-postcode">{{ region.postcode }}</span><span class="region-count">{{ region.facilityCount }}</span></button></div>
          <div class="network-footnote"><span>i</span><p>规划场景数据来自 demo 数据库；邮路和班次说明不代表实时运营状态。</p></div>
        </template>
      </aside>
    </div>

    <footer class="network-footer"><span><i class="footer-dot"></i>数据库覆盖：常住岛 {{ islands.length }} 座 · 邮政区域 {{ regions.length }} 个</span><span>demo 网络数据 <b>实时读取</b></span></footer>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { facilityApi } from '../lib/api';
import { buildIslandNetwork } from '../lib/islandNetwork';
import { useSessionStore } from '../stores/session';

const modes = [{ id: 'ALL', label: '全部邮路' }, { id: 'AIR', label: '航空' }, { id: 'SEA', label: '海运' }, { id: 'LAND', label: '陆运' }];
const session = useSessionStore();
const selectedMode = ref('ALL');
const selectedRegion = ref('ALL');
const selectedFacility = ref(null);
const selectedRoute = ref(null);
const loading = ref(true);
const loadError = ref('');
const network = ref({ regions: [], islands: [], facilities: [], routes: [] });
const regions = computed(() => network.value.regions);
const islands = computed(() => network.value.islands);
const facilities = computed(() => network.value.facilities);
const routes = computed(() => network.value.routes);

async function loadNetwork() {
  loading.value = true;
  loadError.value = '';
  try {
    const payload = await facilityApi.demoPostalNetwork(session.token);
    network.value = buildIslandNetwork(payload);
    if (!network.value.regions.length || !network.value.facilities.length) {
      throw new Error('数据库尚无完整的群岛网络场景数据');
    }
  } catch (error) {
    loadError.value = error?.message || '读取邮政网络数据失败';
  } finally {
    loading.value = false;
  }
}

const visibleFacilities = computed(() => facilities.value.filter((item) => selectedRegion.value === 'ALL' || item.region === selectedRegion.value));
const visibleRoutes = computed(() => routes.value.filter((route) => (selectedMode.value === 'ALL' || route.mode === selectedMode.value) && (selectedRegion.value === 'ALL' || [route.origin, route.destination].some((code) => facilities.value.find((facility) => facility.code === code)?.region === selectedRegion.value))));
const connectedRoutes = computed(() => selectedFacility.value ? routes.value.filter((route) => route.origin === selectedFacility.value.code || route.destination === selectedFacility.value.code) : []);

function regionVisible(code) { return selectedRegion.value === 'ALL' || selectedRegion.value === code; }
function selectFacility(facility) { selectedFacility.value = facility; selectedRoute.value = null; }
function selectRoute(route) { selectedRoute.value = route; selectedFacility.value = null; }
function resetFilters() { selectedRegion.value = 'ALL'; selectedMode.value = 'ALL'; selectedFacility.value = null; selectedRoute.value = null; }
function typeGlyph(type) { return type === 'center' ? 'C' : type === 'gateway' ? '↗' : 'P'; }
function modeGlyph(mode) { return mode === 'AIR' ? '✦' : mode === 'SEA' ? '≈' : '↔'; }
function modeName(mode) { return mode === 'AIR' ? '航空' : mode === 'SEA' ? '海运' : '陆运'; }
function routeStops(route) { return [facilities.value.find((facility) => facility.code === route.origin), facilities.value.find((facility) => facility.code === route.destination)].filter(Boolean); }

onMounted(loadNetwork);
</script>

<style scoped>
.map-load-state{position:absolute;inset:0;z-index:5;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;background:rgba(220,235,228,.9);color:#476861;font-size:12px;text-align:center;padding:20px}.map-load-state strong{font-size:13px;color:#344f49}.map-load-state>span:not(.loading-mark){max-width:340px;color:#708580;font-size:10px}.loading-mark{width:22px;height:22px;border:2px solid #aac8bd;border-top-color:#367e70;border-radius:50%;animation:network-spin .8s linear infinite}.retry-button{border:1px solid #7db5a5;border-radius:4px;background:#fff;padding:6px 12px;color:#286e63;font-size:11px;font-weight:700;cursor:pointer}.retry-button:hover{background:#e8f4f0}.island-muted{opacity:.18;transition:opacity .2s}@keyframes network-spin{to{transform:rotate(360deg)}}
.network-panel{--ink:#20383a;--muted:#718481;--line:#e0e9e5;--teal:#127b70;background:#fff;border:1px solid #dce7e2;border-radius:8px;color:var(--ink);overflow:hidden}.network-heading{min-height:104px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:22px 26px;border-bottom:1px solid var(--line)}.network-eyebrow{display:flex;align-items:center;gap:8px;color:#28827a;font-size:11px;font-weight:800;letter-spacing:0}.live-dot,.footer-dot{width:7px;height:7px;background:#46a77a;border-radius:50%;box-shadow:0 0 0 3px #e3f4e9;display:inline-block}.network-heading h2{font-size:21px;line-height:1.2;margin:7px 0 3px;font-weight:750;letter-spacing:0}.network-heading p{font-size:12px;color:var(--muted);margin:0}.map-actions{display:flex;align-items:center;gap:10px}.scenario-tag{font-size:10px;font-weight:700;color:#9b6d27;background:#fff5df;border:1px solid #f2dfb4;padding:6px 9px;border-radius:4px;white-space:nowrap}.icon-button,.plain-button{border:1px solid var(--line);background:#fff;color:#607573;width:32px;height:32px;border-radius:4px;cursor:pointer;font-size:20px;line-height:1}.icon-button:hover,.plain-button:hover{color:var(--teal);border-color:#8cc7b8}.map-toolbar{min-height:58px;padding:10px 22px;display:flex;justify-content:space-between;align-items:center;gap:12px;border-bottom:1px solid var(--line)}.map-filters{display:flex;gap:5px;flex-wrap:wrap}.filter-button{display:flex;align-items:center;gap:7px;background:transparent;border:1px solid transparent;padding:7px 10px;border-radius:4px;color:#70817e;font-size:11px;font-weight:700;cursor:pointer}.filter-button:hover{background:#f4f8f6}.filter-button.active{background:#e8f4f0;color:#176e64;border-color:#d2e8df}.mode-mark{width:8px;height:8px;border-radius:50%;background:#aabbb7}.mode-air{background:#cb5b45}.mode-sea{background:#397b9b}.mode-land{background:#56825c}.region-select-wrap{display:flex;align-items:center;gap:8px;color:#788985;font-size:11px;white-space:nowrap}.region-select-wrap select{border:1px solid var(--line);padding:7px 25px 7px 9px;background:#fff;border-radius:4px;font-size:11px;color:#394f4c;outline:none}.network-layout{display:grid;grid-template-columns:minmax(0,1fr) 290px;min-height:525px}.map-stage{position:relative;min-width:0;overflow:hidden;background:#dcebe4}.island-map{display:block;width:100%;height:100%;min-height:525px}.map-water-label{position:absolute;color:#709a94;font-weight:700;font-size:8px;letter-spacing:2px;pointer-events:none;opacity:.7}.water-west{left:7%;top:13%}.water-east{right:6%;bottom:17%}.sea-current{fill:none;stroke:#6da8a0;stroke-width:1.2;stroke-dasharray:4 9;opacity:.45}.current-two{opacity:.28}.landmass{fill:#c7d2ae;stroke:#879b7b;stroke-width:1.2}.namoa{fill:#d5d9b5}.oro{fill:#d4d8b5}.miri{fill:#cbd6b1}.sela{fill:#d5d6ae}.islet{fill:#d1d9b8;stroke-width:1}.land-detail{fill:none;stroke:#aab895;stroke-width:1;stroke-linecap:round;stroke-dasharray:2 4;opacity:.8}.town-dot{fill:#e19a58}.island-name{font-size:15px;letter-spacing:1px;font-weight:800;fill:#64775f;text-anchor:middle}.island-subname{font-size:8px;letter-spacing:0;fill:#708272;text-anchor:middle}.region-muted{opacity:.16;transition:opacity .2s}.route-line{fill:none;stroke-width:2;opacity:.72;cursor:pointer;transition:opacity .15s,stroke-width .15s}.route-line:hover,.route-line.selected{stroke-width:3.8;opacity:1}.route-air{stroke:#cb5b45;stroke-dasharray:none}.route-sea{stroke:#397b9b;stroke-dasharray:7 5}.route-land{stroke:#56825c;stroke-dasharray:2 3}.facility-marker{cursor:pointer;outline:none}.marker-halo{fill:#fff;stroke:#778a7d;stroke-width:1.4;transition:r .15s,stroke-width .15s}.marker-core{fill:#738b78}.type-center .marker-halo{stroke:#3b776c;stroke-width:2}.type-center .marker-core{fill:#277c6d}.type-gateway .marker-core{fill:#bc7146}.type-gateway .marker-halo{stroke:#bb754b}.type-office .marker-core{fill:#356f91}.type-office .marker-halo{stroke:#467b91}.marker-symbol{fill:#fff;stroke:#fff;stroke-width:.5}.facility-marker:hover .marker-halo,.facility-marker.selected .marker-halo{r:13;stroke-width:2.5}.marker-label{fill:#304d47;font-size:8px;font-weight:750;paint-order:stroke;stroke:#e2eee6;stroke-width:3px;stroke-linejoin:round}.map-compass circle{fill:#f7faf6;stroke:#b5c7bd}.map-compass path{fill:#4d7868}.map-compass text{font-size:8px;font-weight:800;text-anchor:middle;fill:#4d7868}.map-scale path{stroke:#628579;stroke-width:1.5}.map-scale text{font-size:8px;fill:#5b7972}.map-legend{position:absolute;left:18px;bottom:15px;display:flex;flex-wrap:wrap;gap:10px;background:rgba(249,252,248,.88);border:1px solid rgba(205,220,211,.9);border-radius:4px;padding:8px 10px;color:#647772;font-size:9px}.map-legend span{display:flex;align-items:center;gap:5px}.legend-node{width:9px;height:9px;border-radius:50%;display:inline-block;background:#356f91}.legend-node.center{background:#277c6d;box-shadow:0 0 0 2px #d8e9dc}.legend-node.gateway{background:#bc7146;transform:rotate(45deg);border-radius:1px}.network-details{border-left:1px solid var(--line);padding:20px 19px;min-width:0;background:#fff}.detail-topline{height:25px;display:flex;align-items:flex-start;justify-content:space-between}.detail-kicker{font-size:10px;color:#82918e;font-weight:800;text-transform:uppercase;letter-spacing:.6px}.network-version{font-size:9px;color:#3d8177;font-weight:800;border:1px solid #d5eae1;border-radius:3px;padding:3px 6px}.plain-button{width:23px;height:23px;font-size:18px;border:0}.overview-title{font-size:23px;line-height:1.3;margin:9px 0;color:#213b39;font-weight:760}.overview-copy{font-size:11px;line-height:1.65;color:#758581;margin:0 0 18px}.coverage-stat-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border-top:1px solid var(--line);border-left:1px solid var(--line)}.coverage-stat-grid div{padding:11px 12px;border-right:1px solid var(--line);border-bottom:1px solid var(--line);display:flex;flex-direction:column;gap:4px}.coverage-stat-grid strong{font-size:19px;line-height:1;font-weight:760;color:#276f65}.coverage-stat-grid span{font-size:10px;color:#81908c}.region-list{margin-top:19px}.region-list h4,.detail-block h4{display:flex;align-items:center;justify-content:space-between;color:#425955;font-size:11px;font-weight:800;margin:0 0 8px}.region-list h4 span{color:#99a5a2;font-size:9px;font-weight:600}.region-list button{display:flex;align-items:center;gap:8px;width:100%;padding:8px 6px;border:0;border-bottom:1px solid #edf1ee;background:#fff;text-align:left;cursor:pointer;color:#536762}.region-list button:hover,.region-list button.chosen{background:#f2f8f5}.region-dot{width:8px;height:8px;border-radius:50%;background:#6d9b81}.region-or{background:#cf9660}.region-mi{background:#708d9d}.region-se{background:#a98273}.region-name{font-size:10px;font-weight:700;flex:1}.region-postcode{font-size:9px;color:#82918c}.region-count{min-width:18px;text-align:right;font-size:9px;color:#657a72;font-weight:800}.network-footnote{display:flex;gap:8px;margin-top:14px;padding:10px;background:#f7f9f5;border-left:2px solid #c8d9c9}.network-footnote>span{display:flex;align-items:center;justify-content:center;width:15px;height:15px;flex:0 0 auto;border-radius:50%;background:#e5ede5;color:#66806d;font-size:10px;font-weight:800}.network-footnote p{margin:0;color:#788680;font-size:9px;line-height:1.55}.facility-title-row,.route-title-row{display:flex;align-items:center;gap:10px;margin:10px 0 12px}.facility-title-row h3,.route-title-row h3{font-size:14px;line-height:1.35;margin:0;font-weight:750;color:#243f3b}.facility-title-row p,.route-title-row p{font-size:9px;color:#81908c;margin:3px 0 0}.facility-type-icon,.route-mode-icon{display:flex;align-items:center;justify-content:center;width:34px;height:34px;flex:0 0 auto;border-radius:50%;font-size:13px;color:#fff;background:#356f91;font-weight:800}.facility-type-icon.type-center{background:#277c6d}.facility-type-icon.type-gateway{background:#bc7146}.detail-code-row,.route-status{display:flex;justify-content:space-between;align-items:center;border-top:1px solid var(--line);padding:9px 0;font-size:9px;color:#687b76}.detail-code-row>span:first-child{font-weight:800;color:#537068}.status-indicator{display:inline-flex;align-items:center;gap:5px;color:#648372;font-size:9px}.status-indicator i{width:6px;height:6px;border-radius:50%;background:#4a9a70}.detail-list{margin:0;border-top:1px solid var(--line)}.detail-list div{display:grid;grid-template-columns:77px minmax(0,1fr);gap:7px;padding:8px 0;border-bottom:1px solid #eef2ef}.detail-list dt{font-size:9px;color:#8a9894}.detail-list dd{font-size:9px;font-weight:700;color:#425952;margin:0;line-height:1.5}.detail-block{margin-top:15px}.detail-block h4 span{color:#9aa8a3}.connected-route{width:100%;display:grid;grid-template-columns:42px minmax(0,1fr) 48px;gap:6px;align-items:center;padding:8px 0;background:transparent;border:0;border-bottom:1px solid #edf1ee;text-align:left;cursor:pointer}.connected-route:hover .connected-route-name{color:#16756c}.connected-mode{font-size:8px;font-weight:800}.mode-air.connected-mode{color:#bc5c47}.mode-sea.connected-mode{color:#397b9b}.mode-land.connected-mode{color:#56825c}.connected-route-name{font-size:9px;line-height:1.35;color:#526862}.connected-hours{text-align:right;font-size:8px;color:#8a9894}.empty-note{font-size:9px;color:#9aa6a2}.route-mode-icon.mode-air{background:#bc5c47}.route-mode-icon.mode-sea{background:#397b9b}.route-mode-icon.mode-land{background:#56825c}.route-status .mode-long{color:#74847f}.route-stops{padding:8px 0 8px 4px}.route-stop{display:flex;gap:11px;position:relative;padding-bottom:14px}.route-stop:not(:last-child):after{position:absolute;content:"";left:5px;top:12px;bottom:0;border-left:1px dashed #a6c0b4}.route-stop>i{position:relative;z-index:1;width:11px;height:11px;flex:0 0 auto;margin-top:1px;border:2px solid #538f7b;border-radius:50%;background:#fff}.route-stop>i.end{border-color:#ca7555;background:#fff8f2}.route-stop div{display:flex;flex-direction:column;gap:2px}.route-stop span,.route-stop small{font-size:8px;color:#8b9994}.route-stop strong{font-size:10px;color:#425852}.route-details div{grid-template-columns:68px minmax(0,1fr)}.route-note{margin-top:14px;padding:10px;background:#f8f7f1;border-left:2px solid #c6a66c}.route-note strong{font-size:9px;color:#806d48}.route-note p{font-size:9px;line-height:1.55;color:#807b6e;margin:4px 0 0}.network-footer{height:38px;padding:0 18px;display:flex;align-items:center;justify-content:space-between;gap:8px;border-top:1px solid var(--line);color:#82918d;font-size:9px}.network-footer>span:first-child{display:flex;align-items:center;gap:7px}.network-footer b{font-weight:600;color:#a0aaa6;margin-left:8px}
@media(max-width:1100px){.network-layout{grid-template-columns:minmax(0,1fr) 260px}.network-details{padding:16px}.island-map{min-height:485px}.network-layout{min-height:485px}}
@media(max-width:760px){.network-heading{padding:17px;align-items:flex-start}.network-heading h2{font-size:19px}.scenario-tag{display:none}.map-toolbar{align-items:flex-start;flex-direction:column;padding:9px 14px}.map-filters{width:100%;justify-content:space-between}.filter-button{padding:7px 6px;font-size:10px}.region-select-wrap{align-self:flex-end}.network-layout{grid-template-columns:1fr}.map-stage{min-height:380px}.island-map{min-height:380px}.network-details{border-left:0;border-top:1px solid var(--line);padding:17px}.network-footer{height:auto;min-height:42px;padding:8px 12px;flex-wrap:wrap}.map-legend{left:9px;bottom:9px;gap:7px;padding:7px;font-size:8px}.map-water-label{font-size:6px}}
@media(max-width:480px){.network-heading{gap:8px}.network-heading p{font-size:10px}.map-actions{gap:4px}.map-stage{min-height:315px}.island-map{min-height:315px}.network-footer{font-size:8px}}
</style>
