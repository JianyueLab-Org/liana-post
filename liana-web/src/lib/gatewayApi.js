import { notifyApiError } from './apiErrorEvents';

const SERVICE_PREFIX = {
  auth: '/liana-auth-service',
  facility: '/liana-facility-service',
  oms: '/liana-oms-service',
  dispatch: '/liana-dispatch-service',
  sorting: '/liana-sorting-service',
  tracking: '/liana-records-service',
  transport: '/liana-transport-service',
  sync: '/liana-syncer',
};

export class ApiRequestError extends Error {
  constructor(message, details = {}) {
    super(message || '请求失败');
    this.name = 'ApiRequestError';
    Object.assign(this, details);
  }
}

export function unwrapResult(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return payload.data;
  }
  return payload;
}

async function readPayload(response) {
  const contentType = response.headers.get('content-type') || '';
  const text = await response.text();
  if (!text) return '';

  if (contentType.includes('application/json')) {
    try {
      return JSON.parse(text);
    } catch (error) {
      return text;
    }
  }

  return text;
}

function extractErrorMessage(payload, fallback) {
  if (typeof payload === 'string' && payload.trim()) return payload.trim();
  if (payload && typeof payload === 'object') {
    return payload.message || payload.msg || payload.error || payload.detail || fallback;
  }
  return fallback;
}

function getBusinessError(payload) {
  if (!payload || typeof payload !== 'object') return null;

  if ('success' in payload && payload.success === false) {
    return {
      code: payload.code ?? '',
      message: extractErrorMessage(payload, '请求失败'),
    };
  }

  if ('ok' in payload && payload.ok === false) {
    return {
      code: payload.code ?? '',
      message: extractErrorMessage(payload, '请求失败'),
    };
  }

  if ('code' in payload) {
    const numericCode = Number(payload.code);
    const isSuccessCode = Number.isFinite(numericCode) ? numericCode === 200 : payload.code === 'SUCCESS';
    if (!isSuccessCode) {
      return {
        code: payload.code,
        message: extractErrorMessage(payload, '请求失败'),
      };
    }
  }

  return null;
}

function createApiError(message, details) {
  const error = new ApiRequestError(message, details);
  notifyApiError(error);
  return error;
}

export async function request(service, path, { method = 'GET', query, body, token } = {}) {
  const prefix = SERVICE_PREFIX[service];
  if (!prefix) {
    throw createApiError(`Unknown service: ${service}`, { service, path, method });
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

  let response;
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (error) {
    throw createApiError(error?.message || '网络请求失败，请检查服务是否可用', {
      service,
      method,
      path,
      url: url.toString(),
    });
  }

  const payload = await readPayload(response);
  if (!response.ok) {
    throw createApiError(extractErrorMessage(payload, response.statusText || '请求失败'), {
      service,
      method,
      path,
      url: url.toString(),
      status: response.status,
      payload,
    });
  }

  const businessError = getBusinessError(payload);
  if (businessError) {
    throw createApiError(businessError.message, {
      service,
      method,
      path,
      url: url.toString(),
      status: response.status,
      code: businessError.code,
      payload,
    });
  }

  return unwrapResult(payload);
}

export function servicePrefix(service) {
  return SERVICE_PREFIX[service];
}
