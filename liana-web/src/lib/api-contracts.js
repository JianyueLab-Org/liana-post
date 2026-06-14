export const apiContracts = {
  auth: {
    login: { method: 'POST', path: '/auth/login', mock: false, request: { username: 'string', password: 'string' }, response: { code: 200, data: { accessToken: 'string', refreshToken: 'string', expiresIn: 7200 } } },
    me: { method: 'GET', path: '/auth/me', mock: false, response: { code: 200, data: { id: 'u1001', username: 'operator.liu', name: '刘敏', orgId: 'org-hk-001', orgName: '香港邮件处理中心', roles: ['operator'], permissionsVersion: 'v1' } } },
    permissions: { method: 'GET', path: '/auth/permissions', mock: false, response: { code: 200, data: { menuTree: [], actionPermissions: [], dataPermissions: [] } } },
  },
  mail: {
    create: { method: 'POST', path: '/mail/create', mock: true, response: { code: 200, data: { mailNo: 'MP202606090001', status: 'CREATED', createdAt: '2026-06-09 09:20:00', destCountryCode: 'HK' } } },
    list: { method: 'GET', path: '/mail/list', mock: true, response: { code: 200, data: { list: [], page: 1, pageSize: 10, total: 0 } } },
    detail: { method: 'GET', path: '/mail/{id}', mock: true, response: { code: 200, data: { id: 'mail-001', mailNo: 'MP202606090001', status: 'ACCEPTED', sender: '张三', receiver: '王五', origin: 'HK-CWB-01', destination: 'FJ-FZ-02' } } },
    update: { method: 'PUT', path: '/mail/{id}', mock: true, response: { code: 200, data: { updated: true } } },
  },
  dispatch: {
    bags: { method: 'GET', path: '/dispatch/bags', mock: true, response: { code: 200, data: { list: [], page: 1, pageSize: 10, total: 0 } } },
    batches: { method: 'GET', path: '/dispatch/batches', mock: true, response: { code: 200, data: { list: [], page: 1, pageSize: 10, total: 0 } } },
    createBag: { method: 'POST', path: '/dispatch/bag/create', mock: true, response: { code: 200, data: { bagNo: 'BG20260609001', status: 'DRAFT' } } },
    confirmBag: { method: 'POST', path: '/dispatch/bag/confirm', mock: true, response: { code: 200, data: { confirmed: true } } },
    handoff: { method: 'POST', path: '/dispatch/handoff', mock: true, response: { code: 200, data: { handoffNo: 'HF20260609001', status: 'HANDOFFED' } } },
  },
  sorting: {
    receive: { method: 'POST', path: '/sorting/receive', mock: true, response: { code: 200, data: { packageNo: 'PKG202606140001', packageStatus: 'RECEIVED' } } },
    unpack: { method: 'POST', path: '/sorting/unpack-item', mock: true, response: { code: 200, data: { list: [] } } },
    route: { method: 'POST', path: '/sorting/route-calculate', mock: true, response: { code: 200, data: { routeCode: 'C1-STANDARD-01', cellCode: 'C1-CELL-01' } } },
    rebag: { method: 'POST', path: '/sorting/re-bag', mock: true, response: { code: 200, data: { packageNo: 'B202606140001', packageStatus: 'SEALED' } } },
  },
  tracking: {
    mail: { method: 'GET', path: '/tracking/{mailNo}', mock: true, readOnly: true, response: { code: 200, data: { mailNo: 'MP202606090001', currentStatus: 'IN_TRANSIT', lastUpdatedAt: '2026-06-09 10:10:00' } } },
    events: { method: 'GET', path: '/tracking/events/{mailNo}', mock: true, readOnly: true, response: { code: 200, data: { list: [] } } },
  },
  facility: {
    offices: { method: 'GET', path: '/facility/offices', mock: true, response: { code: 200, data: { list: [] } } },
    hubs: { method: 'GET', path: '/facility/hubs', mock: true, response: { code: 200, data: { list: [] } } },
    routes: { method: 'GET', path: '/facility/routes', mock: true, response: { code: 200, data: { list: [] } } },
  },
  oms: {
    countries: { method: 'GET', path: '/oms/countries', mock: true, response: { code: 200, data: { list: [] } } },
    serviceTypes: { method: 'GET', path: '/oms/service-types', mock: true, response: { code: 200, data: { list: [] } } },
  },
  system: {
    users: { method: 'GET', path: '/system/users', mock: true, response: { code: 200, data: { list: [] } } },
    roles: { method: 'GET', path: '/system/roles', mock: true, response: { code: 200, data: { list: [] } } },
    permissions: { method: 'GET', path: '/system/permissions', mock: true, response: { code: 200, data: { list: [] } } },
  },
  sync: {
    outbox: { method: 'GET', path: '/sync/outbox', mock: true, response: { code: 200, data: { list: [] } } },
    tasks: { method: 'GET', path: '/sync/tasks', mock: true, response: { code: 200, data: { list: [] } } },
  },
};

export const apiContractMarkdown = `# Liana Pacific Postal System API Contract\n\n## Auth\n- POST /auth/login\n- GET /auth/me\n- GET /auth/permissions\n\n## Mail\n- POST /mail/create\n- GET /mail/list\n- GET /mail/{id}\n- PUT /mail/{id}\n\n## Dispatch\n- GET /dispatch/bags\n- POST /dispatch/bag/create\n- POST /dispatch/bag/confirm\n- POST /dispatch/handoff\n\n## Tracking\n- GET /tracking/{mailNo}\n- GET /tracking/events/{mailNo}\n\n## Facility\n- GET /facility/offices\n- GET /facility/hubs\n- GET /facility/routes\n\n## OMS Catalog\n- GET /oms/countries\n- GET /oms/service-types\n- GET /oms/service-types/by-country\n\n## System\n- GET /system/users\n- GET /system/roles\n- GET /system/permissions\n\n## Syncer\n- GET /sync/outbox\n- GET /sync/tasks\n`;
