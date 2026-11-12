export const API_ERROR_EVENT = 'liana:api-error';

let nextId = 1;

function canUseWindow() {
  return typeof window !== 'undefined' && typeof window.dispatchEvent === 'function';
}

export function notifyApiError(error) {
  if (!canUseWindow()) return;

  const detail = {
    id: nextId++,
    message: error?.message || '请求失败',
    service: error?.service || '',
    method: error?.method || '',
    path: error?.path || '',
    status: error?.status || 0,
    code: error?.code ?? '',
    occurredAt: new Date().toLocaleTimeString(),
  };

  window.dispatchEvent(new CustomEvent(API_ERROR_EVENT, { detail }));
}
