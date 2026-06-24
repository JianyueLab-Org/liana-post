import { notifyApiError } from './apiErrorEvents';
import { ApiRequestError, request } from './gatewayApi';

function unwrapList(payload) {
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === 'object') {
    if (Array.isArray(payload.list)) return payload.list;
    if (Array.isArray(payload.data)) return payload.data;
  }
  return [];
}

export const authApi = {
  async login(payload) {
    const result = await request('auth', '/api/auth/login', { method: 'POST', body: payload });
    if (!result?.token) {
      const error = new ApiRequestError('登录失败：认证服务未返回有效令牌', {
        service: 'auth',
        method: 'POST',
        path: '/api/auth/login',
      });
      notifyApiError(error);
      throw error;
    }
    return result;
  },
  profile(username, token) {
    return request('auth', '/api/auth/profile', { query: { username }, token });
  },
  validateToken(token) {
    return request('auth', '/api/auth/token/validate', { token });
  },
  users(token) {
    return request('auth', '/api/auth/system/users', { token }).then(unwrapList);
  },
  createUser(payload, token) {
    return request('auth', '/api/auth/system/users', { method: 'POST', body: payload, token });
  },
  resetPassword(payload, token) {
    return request('auth', '/api/auth/system/users/password/reset', { method: 'POST', body: payload, token });
  },
  roles(token) {
    return request('auth', '/api/auth/system/roles', { token }).then(unwrapList);
  },
  permissions(token) {
    return request('auth', '/api/auth/system/permissions', { token }).then(unwrapList);
  },
  bootstrapSystem(payload, token) {
    return request('auth', '/api/auth/system/project/init', { method: 'POST', token });
  },
  dashboardSummary(token) {
    return request('auth', '/api/auth/system/dashboard/summary', { token });
  },
};

export const mailApi = {
  create(payload, token) {
    return request('oms', '/api/oms/mails', { method: 'POST', body: payload, token });
  },
  listMailTypes(token) {
    return request('oms', '/api/oms/mail-types', { token }).then(unwrapList);
  },
  listCountries(token) {
    return request('oms', '/api/oms/countries', { token }).then(unwrapList);
  },
  listServiceTypes(token) {
    return request('oms', '/api/oms/service-types', { token }).then(unwrapList);
  },
  listServiceTypesByCountry(countryCode, token) {
    return request('oms', '/api/oms/service-types/by-country', { query: { countryCode }, token }).then(unwrapList);
  },
  list(token) {
    return request('oms', '/api/oms/mails', { token }).then(unwrapList);
  },
  listPackages(token) {
    return request('oms', '/api/oms/packages', { token }).then(unwrapList);
  },
  listPendingDeliveryMails(currentFacilityCode, token) {
    return request('oms', '/api/oms/packages/pending-delivery', { query: { currentFacilityCode }, token }).then(unwrapList);
  },
  detail(waybillNo, token) {
    return request('oms', `/api/oms/mails/${encodeURIComponent(waybillNo)}`, { token });
  },
  byBarcode(barcode, token) {
    return request('oms', '/api/oms/mails/by-barcode', { query: { barcode }, token });
  },
  listByStatus(status, token) {
    return request('oms', `/api/oms/mails/status/${encodeURIComponent(status)}`, { token }).then(unwrapList);
  },
  dispatchCandidates(payload, token) {
    return request('oms', '/api/oms/mails/dispatch-candidates', { method: 'POST', body: payload, token }).then(unwrapList);
  },
  updateStatus(waybillNo, payload, token) {
    return request('oms', `/api/oms/mails/${encodeURIComponent(waybillNo)}/status`, { method: 'POST', body: payload, token });
  },
  assignBag(waybillNo, payload, token) {
    return request('oms', `/api/oms/mails/${encodeURIComponent(waybillNo)}/bag`, { method: 'POST', body: payload, token });
  },
  updateRoute(waybillNo, payload, token) {
    return request('oms', `/api/oms/mails/${encodeURIComponent(waybillNo)}/route`, { method: 'POST', body: payload, token });
  },
  listSlots(token) {
    return request('oms', '/api/oms/slots', { token }).then(unwrapList);
  },
  sealSlot(slotCode, payload, token) {
    return request('oms', `/api/oms/slots/${encodeURIComponent(slotCode)}/seal`, { method: 'POST', body: payload, token });
  },
  receivePackage(payload, token) {
    return request('oms', '/api/oms/packages/receive-open', { method: 'POST', body: payload, token });
  },
  receiveAndOpenPackage(payload, token) {
    return request('oms', '/api/oms/packages/receive-open', { method: 'POST', body: payload, token });
  },
  deliverMail(waybillNo, facilityCode, token) {
    return request('oms', `/api/oms/mails/${encodeURIComponent(waybillNo)}/deliver`, { method: 'POST', query: { facilityCode }, token });
  },
  departExchangeMail(waybillNo, facilityCode, token) {
    return request('oms', `/api/oms/mails/${encodeURIComponent(waybillNo)}/exchange-depart`, { method: 'POST', query: { facilityCode }, token });
  },
  dashboardSummary(facilityCode, token) {
    return request('oms', '/api/oms/dashboard/summary', { query: { facilityCode }, token });
  },
};

export const dispatchApi = {
  createRouteRule(payload, token) {
    return request('dispatch', '/api/dispatch/route-rules', { method: 'POST', body: payload, token });
  },
  routeDecision(payload, token) {
    return request('dispatch', '/api/dispatch/route-decisions', { method: 'POST', body: payload, token });
  },
  createBag(payload, token) {
    return request('dispatch', '/api/dispatch/bags', { method: 'POST', body: payload, token });
  },
  syncMailBag(payload, token) {
    return request('dispatch', '/api/dispatch/bags/sync-mail', { method: 'POST', body: payload, token });
  },
  createBatch(payload, token) {
    return request('dispatch', '/api/dispatch/batches', { method: 'POST', body: payload, token });
  },
  approveBatch(batchNo, payload, token) {
    return request('dispatch', `/api/dispatch/batches/${encodeURIComponent(batchNo)}/approve`, { method: 'POST', body: payload, token });
  },
  createHandoff(payload, token) {
    return request('dispatch', '/api/dispatch/handoffs', { method: 'POST', body: payload, token });
  },
  listBags(token) {
    return request('dispatch', '/api/dispatch/bags', { token }).then(unwrapList);
  },
  listBatches(token) {
    return request('dispatch', '/api/dispatch/batches', { token }).then(unwrapList);
  },
  listHandoffs(token) {
    return request('dispatch', '/api/dispatch/handoffs', { token }).then(unwrapList);
  },
  dashboardSummary(facilityCode, token) {
    return request('dispatch', '/api/dispatch/dashboard/summary', { query: { facilityCode }, token });
  },
};

export const sortingApi = {
  listPackages(token) {
    return request('sorting', '/api/v1/sorting/packages', { token }).then(unwrapList);
  },
  listManifests(token, params = {}) {
    return request('sorting', '/api/v1/sorting/manifests', { query: params, token }).then(unwrapList);
  },
  getManifest(manifestNo, token) {
    return request('sorting', `/api/v1/sorting/manifests/${encodeURIComponent(manifestNo)}`, { token });
  },
  listLines(packageNo, token) {
    return request('sorting', '/api/v1/sorting/lines', { query: { packageNo }, token }).then(unwrapList);
  },
  listLinesByRoute(token) {
    return request('sorting', '/api/v1/sorting/lines', { token }).then(unwrapList);
  },
  routeCalculateScan(payload, token) {
    return request('sorting', '/api/v1/sorting/route-calculate/scan', { method: 'POST', body: payload, token });
  },
  listPendingRouteItems(stationCode, token) {
    return request('sorting', '/api/v1/sorting/route-calculate/pending', { query: { stationCode }, token }).then(unwrapList);
  },
  listDiscrepancies(packageNo, token) {
    return request('sorting', '/api/v1/sorting/discrepancies', { query: { packageNo }, token }).then(unwrapList);
  },
  receive(payload, token) {
    return request('sorting', '/api/v1/sorting/receive', { method: 'POST', body: payload, token });
  },
  unpackItem(payload, token) {
    return request('sorting', '/api/v1/sorting/unpack-item', { method: 'POST', body: payload, token });
  },
  previewUnpackItems(params, token) {
    return request('sorting', '/api/v1/sorting/unpack-preview', { query: params, token });
  },
  routeCalculate(payload, token) {
    return request('sorting', '/api/v1/sorting/route-calculate', { method: 'POST', body: payload, token });
  },
  reBag(payload, token) {
    return request('sorting', '/api/v1/sorting/re-bag', { method: 'POST', body: payload, token });
  },
  listSlots(token, stationCode) {
    return request('sorting', '/api/v1/sorting/slots', { query: { stationCode }, token }).then(unwrapList);
  },
  sealBagBySlot(payload, token) {
    return request('sorting', '/api/v1/sorting/slots/seal', { method: 'POST', body: payload, token });
  },
  listCountrySlots(token, stationCode) {
    return request('sorting', '/api/v1/sorting/country-slots', { query: { stationCode }, token }).then(unwrapList);
  },
  sealCountrySlot(payload, token) {
    return request('sorting', '/api/v1/sorting/country-slots/seal', { method: 'POST', body: payload, token });
  },
  dashboardSummary(stationCode, token) {
    return request('sorting', '/api/v1/sorting/dashboard/summary', { query: { stationCode }, token });
  },
};

export const trackingApi = {
  recordEvent(payload, token) {
    return request('tracking', '/api/records/events', { method: 'POST', body: payload, token });
  },
  getEvent(eventNo, token) {
    return request('tracking', `/api/records/events/${encodeURIComponent(eventNo)}`, { token });
  },
  listEvents(token) {
    return request('tracking', '/api/records/events', { token }).then(unwrapList);
  },
  listEventsByWaybill(waybillNo, token) {
    return request('tracking', `/api/records/events/waybill/${encodeURIComponent(waybillNo)}`, { token }).then(unwrapList);
  },
  search(payload, token) {
    return request('tracking', '/api/records/events/search', { method: 'POST', body: payload, token }).then(unwrapList);
  },
};

export const transportApi = {
  listAssets(params, token) {
    return request('transport', '/api/transport/assets', { query: params, token });
  },
  getAsset(code, token) {
    return request('transport', `/api/transport/assets/${encodeURIComponent(code)}`, { token });
  },
  createAsset(payload, token) {
    return request('transport', '/api/transport/assets', { method: 'POST', body: payload, token });
  },
  updateAsset(code, payload, token) {
    return request('transport', `/api/transport/assets/${encodeURIComponent(code)}`, { method: 'PUT', body: payload, token });
  },
  listRoutes(params, token) {
    return request('transport', '/api/transport/routes', { query: params, token });
  },
  getRoute(code, token) {
    return request('transport', `/api/transport/routes/${encodeURIComponent(code)}`, { token });
  },
  createRoute(payload, token) {
    return request('transport', '/api/transport/routes', { method: 'POST', body: payload, token });
  },
  updateRoute(code, payload, token) {
    return request('transport', `/api/transport/routes/${encodeURIComponent(code)}`, { method: 'PUT', body: payload, token });
  },
  listSchedules(params, token) {
    return request('transport', '/api/transport/schedules', { query: params, token });
  },
  getSchedule(code, token) {
    return request('transport', `/api/transport/schedules/${encodeURIComponent(code)}`, { token });
  },
  createSchedule(payload, token) {
    return request('transport', '/api/transport/schedules', { method: 'POST', body: payload, token });
  },
  updateSchedule(code, payload, token) {
    return request('transport', `/api/transport/schedules/${encodeURIComponent(code)}`, { method: 'PUT', body: payload, token });
  },
  listTasks(params, token) {
    return request('transport', '/api/transport/tasks', { query: params, token });
  },
  createTaskFromDispatchBag(dispatchBagId, token) {
    return request('transport', `/api/transport/tasks/from-dispatch/${encodeURIComponent(dispatchBagId)}`, { method: 'POST', token });
  },
  getTask(code, token) {
    return request('transport', `/api/transport/tasks/${encodeURIComponent(code)}`, { token });
  },
  createTask(payload, token) {
    return request('transport', '/api/transport/tasks', { method: 'POST', body: payload, token });
  },
  updateTaskStatus(code, payload, token) {
    return request('transport', `/api/transport/tasks/${encodeURIComponent(code)}/status`, { method: 'POST', body: payload, token });
  },
  dashboardSummary(token) {
    return request('transport', '/api/transport/dashboard/summary', { token });
  },
};

export const facilityApi = {
  listTypes(token) {
    return request('facility', '/api/facilities/types', { token }).then(unwrapList);
  },
  getType(code, token) {
    return request('facility', `/api/facilities/types/${encodeURIComponent(code)}`, { token });
  },
  createFacilityType(payload, token) {
    return request('facility', '/api/facilities/types', { method: 'POST', body: payload, token });
  },
  createFacility(payload, token) {
    return request('facility', '/api/facilities', { method: 'POST', body: payload, token });
  },
  listFacilities(token) {
    return request('facility', '/api/facilities', { token }).then(unwrapList);
  },
  getFacility(facilityCode, token) {
    return request('facility', `/api/facilities/${encodeURIComponent(facilityCode)}`, { token });
  },
  createRoute(payload, token) {
    return request('facility', '/api/facilities/routes', { method: 'POST', body: payload, token });
  },
  updateRoute(routeCode, payload, token) {
    return request('facility', `/api/facilities/routes/${encodeURIComponent(routeCode)}`, { method: 'PUT', body: payload, token });
  },
  listRoutes(token) {
    return request('facility', '/api/facilities/routes', { token }).then(unwrapList);
  },
  getRoute(routeCode, token) {
    return request('facility', `/api/facilities/routes/${encodeURIComponent(routeCode)}`, { token });
  },
  dashboardSummary(token) {
    return request('facility', '/api/facilities/dashboard/summary', { token });
  },
};

export const syncApi = {
  listOutbox(token) {
    return request('sync', '/api/syncer/outbox', { token }).then(unwrapList);
  },
  listTasks(token) {
    return request('sync', '/api/syncer/tasks', { token }).then(unwrapList);
  },
  listRetries(token) {
    return request('sync', '/api/syncer/retries', { token }).then(unwrapList);
  },
  summary(token) {
    return request('sync', '/api/syncer/summary', { token });
  },
  scanOutbox(token) {
    return request('sync', '/api/syncer/outbox/scan', { method: 'POST', token });
  },
  retryTasks(token) {
    return request('sync', '/api/syncer/tasks/retry', { method: 'POST', token });
  },
  createSuccessDemo(token) {
    return request('sync', '/api/syncer/demo/success', { method: 'POST', token });
  },
  createFailOnceDemo(token) {
    return request('sync', '/api/syncer/demo/fail-once', { method: 'POST', token });
  },
};
