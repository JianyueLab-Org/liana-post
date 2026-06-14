const SERVICE_PREFIX = {
  auth: '/liana-auth-service',
  facility: '/liana-facility-service',
  oms: '/liana-oms-service',
  dispatch: '/liana-dispatch-service',
  sorting: '/liana-sorting-service',
  tracking: '/liana-records-service',
  transport: '/liana-transport-service',
};

export function unwrapResult(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data;
  }
  return payload;
}

export async function request(service, path, { method = 'GET', query, body, token } = {}) {
  const prefix = SERVICE_PREFIX[service];
  if (!prefix) {
    throw new Error(`Unknown service: ${service}`);
  }

  const url = new URL(`${prefix}${path}`, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }

  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8';
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json') ? await response.json() : await response.text();

  if (!response.ok) {
    throw new Error(typeof payload === 'string' ? payload : payload?.message || response.statusText);
  }
  if (payload && typeof payload === 'object' && 'code' in payload && payload.code !== 200) {
    throw new Error(payload.message || 'Request failed');
  }
  return unwrapResult(payload);
}

export function servicePrefix(service) {
  return SERVICE_PREFIX[service];
}
