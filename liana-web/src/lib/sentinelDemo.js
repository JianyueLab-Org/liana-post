const SENTINEL_SERVICES = [
  { name: 'liana-gateway', role: '网关入口', dashboardPort: 8719, status: '已接入' },
  { name: 'liana-sorting-service', role: '分拣演示服务', dashboardPort: 8720, status: '已接入' },
  { name: 'liana-oms-service', role: '订单演示服务', dashboardPort: 8721, status: '已接入' },
  { name: 'liana-tracking-service', role: '轨迹演示服务', dashboardPort: 8722, status: '已接入' },
];

const GATEWAY_RULE = {
  routeId: 'sorting-alias',
  path: '/liana-sorting-service/**',
  qps: 2,
  statusCode: 429,
  message: '请求过于频繁，请稍后再试',
};

const FALLBACK_CHAINS = [
  { source: 'liana-sorting-service', target: 'liana-oms-service', strategy: 'Result.fail', status: '已覆盖' },
  { source: 'liana-oms-service', target: 'liana-tracking-service', strategy: 'Result.fail', status: '已覆盖' },
];

const PROBE_PATH = '/liana-sorting-service/api/v1/sorting/dashboard/summary';

export function sentinelDemoSummary() {
  return {
    title: 'Sentinel 演示监控',
    scope: '管理员视图',
    metrics: [
      { label: '接入应用', value: SENTINEL_SERVICES.length, hint: 'Dashboard 可见服务', tone: 'info' },
      { label: 'Gateway QPS', value: GATEWAY_RULE.qps, hint: GATEWAY_RULE.routeId, tone: 'warning' },
      { label: 'Feign 降级链路', value: FALLBACK_CHAINS.length, hint: 'fallbackFactory', tone: 'success' },
    ],
    breakdowns: [
      {
        title: 'Gateway 规则',
        items: [
          { label: '限流路由', value: GATEWAY_RULE.routeId, status: GATEWAY_RULE.path },
          { label: '响应状态', value: GATEWAY_RULE.statusCode, status: GATEWAY_RULE.message },
        ],
      },
      {
        title: 'Dashboard',
        items: [
          { label: '本地地址', value: '8858', status: '127.0.0.1:8858' },
          { label: '容器地址', value: '8858', status: 'sentinel-dashboard:8858' },
        ],
      },
    ],
  };
}

export function sentinelDemoConfig() {
  return {
    services: SENTINEL_SERVICES,
    rule: GATEWAY_RULE,
    fallbackChains: FALLBACK_CHAINS,
    dashboardUrl: 'http://127.0.0.1:8858',
    probePath: PROBE_PATH,
  };
}

export async function probeGatewayLimit({ token, count = 10 } = {}) {
  const startedAt = performance.now();
  const requests = Array.from({ length: count }, (_, index) => {
    const url = new URL(PROBE_PATH, window.location.origin);
    url.searchParams.set('probe', `${Date.now()}-${index}`);
    const headers = { Accept: 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    return fetch(url, { headers })
      .then(async (response) => {
        const contentType = response.headers.get('content-type') || '';
        const payload = contentType.includes('application/json') ? await response.json() : await response.text();
        return {
          ok: response.ok,
          status: response.status,
          message: typeof payload === 'string' ? payload : payload?.message || response.statusText,
        };
      })
      .catch((error) => ({ ok: false, status: 0, message: error.message || '请求失败' }));
  });
  const results = await Promise.all(requests);
  const durationMs = Math.round(performance.now() - startedAt);
  return {
    total: results.length,
    success: results.filter((item) => item.status >= 200 && item.status < 300).length,
    limited: results.filter((item) => item.status === GATEWAY_RULE.statusCode).length,
    failed: results.filter((item) => item.status === 0 || item.status >= 400 && item.status !== GATEWAY_RULE.statusCode).length,
    durationMs,
    results,
  };
}
