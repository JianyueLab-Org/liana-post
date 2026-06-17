const ROLE_MENU_RULES = {
  CLERK: {
    top: ['dashboard', 'mail', 'catalog', 'dispatch', 'sorting', 'tracking', 'delivery'],
    children: {
      mail: ['MAIL_CREATE', 'MAIL_QUERY'],
      catalog: ['catalog-countries', 'catalog-service-types'],
      dispatch: ['dispatch-bags', 'dispatch-batches', 'dispatch-create-bag', 'dispatch-handoff'],
      sorting: ['sorting-unpack', 'sorting-rebag'],
      delivery: ['delivery-postoffice', 'delivery-postoffice-packages'],
      tracking: ['TRACK_QUERY'],
    },
  },
  MANAGER: {
    top: ['dashboard', 'mail', 'catalog', 'tracking', 'facility', 'transport', 'sorting'],
    children: {
      mail: ['MAIL_CREATE', 'MAIL_QUERY'],
      catalog: ['catalog-countries', 'catalog-service-types'],
      tracking: ['TRACK_QUERY'],
      facility: ['facility-offices', 'facility-hubs', 'facility-routes'],
      transport: ['transport-assets', 'transport-routes', 'transport-schedules', 'transport-tasks'],
      sorting: ['sorting-manifests', 'sorting-route'],
    },
  },
  SORTER: {
    top: ['dashboard', 'catalog', 'dispatch', 'sorting', 'tracking', 'facility', 'transport'],
    children: {
      catalog: ['catalog-countries', 'catalog-service-types'],
      dispatch: ['dispatch-bags', 'dispatch-batches', 'dispatch-create-bag', 'dispatch-handoff'],
      sorting: ['sorting-manifests', 'sorting-receive', 'sorting-unpack', 'sorting-route', 'sorting-rebag'],
      tracking: ['TRACK_QUERY'],
      facility: ['facility-offices', 'facility-hubs', 'facility-routes'],
      transport: ['transport-assets', 'transport-routes', 'transport-schedules', 'transport-tasks'],
    },
  },
  ADMIN: {
    top: ['dashboard', 'catalog', 'facility', 'transport', 'system', 'sync'],
    children: {
      catalog: ['catalog-countries', 'catalog-service-types'],
      facility: ['facility-admin', 'facility-offices', 'facility-hubs', 'facility-routes'],
      transport: ['transport-assets', 'transport-routes', 'transport-schedules', 'transport-tasks'],
      system: ['system-users', 'system-roles', 'system-permissions'],
      sync: ['sync-outbox', 'sync-tasks'],
    },
  },
};

const MENU_LIBRARY = {
  dashboard: { id: 'dashboard', name: '工作台', path: '/dashboard', kind: 'link' },
  mail: {
    id: 'mail',
    name: '收寄',
    path: '/mail/create',
    kind: 'group',
    children: [
      { id: 'mail-create', name: '收寄录入', path: '/mail/create', permission: 'MAIL_CREATE' },
      { id: 'mail-list', name: '邮件台账', path: '/mail/list', permission: 'MAIL_QUERY' },
    ],
  },
  delivery: {
    id: 'delivery',
    name: '投递',
    path: '/delivery/postoffice',
    kind: 'group',
    children: [
      { id: 'delivery-postoffice', name: '投递工作台', path: '/delivery/postoffice' },
      { id: 'delivery-postoffice-print', name: '打印投递单', path: '/delivery/print' },
      { id: 'delivery-postoffice-packages', name: '接收总包', path: '/delivery/packages' },
    ],
  },
  catalog: {
    id: 'catalog',
    name: '基础资料',
    path: '/catalog/countries',
    kind: 'group',
    children: [
      { id: 'catalog-countries', name: '国家管理', path: '/catalog/countries' },
      { id: 'catalog-service-types', name: '服务类型', path: '/catalog/service-types' },
    ],
  },
  dispatch: {
    id: 'dispatch',
    name: '封发',
    path: '/dispatch/bags',
    kind: 'group',
    children: [
      { id: 'dispatch-bags', name: '邮袋管理', path: '/dispatch/bags' },
      { id: 'dispatch-batches', name: '批次管理', path: '/dispatch/batches' },
      { id: 'dispatch-create-bag', name: '建袋作业', path: '/dispatch/create-bag' },
      { id: 'dispatch-handoff', name: '交接确认', path: '/dispatch/handoff' },
    ],
  },
  sorting: {
    id: 'sorting',
    name: '分拣机构',
    path: '/sorting/manifests',
    kind: 'group',
    children: [
      { id: 'sorting-manifests', name: '路单管理', path: '/sorting/manifests' },
      { id: 'sorting-receive', name: '接收勾核', path: '/sorting/receive' },
      { id: 'sorting-unpack', name: '开拆作业', path: '/sorting/unpack' },
      { id: 'sorting-route', name: '出口处理', path: '/sorting/export' },
      { id: 'sorting-rebag', name: '再次封发', path: '/sorting/rebag' },
    ],
  },
  tracking: {
    id: 'tracking',
    name: '查询',
    path: '/tracking/search',
    kind: 'group',
    children: [
      { id: 'tracking-search', name: '轨迹查询', path: '/tracking/search', permission: 'TRACK_QUERY' },
      { id: 'tracking-events', name: '事件流', path: '/tracking/events', permission: 'TRACK_QUERY' },
    ],
  },
  facility: {
    id: 'facility',
    name: '邮政网络',
    path: '/facility/offices',
    kind: 'group',
    children: [
      { id: 'facility-admin', name: '机构管理', path: '/facility/admin' },
      { id: 'facility-offices', name: '网点', path: '/facility/offices' },
      { id: 'facility-hubs', name: '分拣中心', path: '/facility/hubs' },
      { id: 'facility-routes', name: '封发关系管理', path: '/facility/routes' },
    ],
  },
  transport: {
    id: 'transport',
    name: '运输管理',
    path: '/transport/assets',
    kind: 'group',
    children: [
      { id: 'transport-assets', name: '运输资源', path: '/transport/assets' },
      { id: 'transport-routes', name: '运输线路', path: '/transport/routes' },
      { id: 'transport-schedules', name: '运输计划', path: '/transport/schedules' },
      { id: 'transport-tasks', name: '运输任务', path: '/transport/tasks' },
    ],
  },
  system: {
    id: 'system',
    name: '系统管理',
    path: '/system/users',
    kind: 'group',
    children: [
      { id: 'system-users', name: '用户管理', path: '/system/users' },
      { id: 'system-roles', name: '角色管理', path: '/system/roles' },
      { id: 'system-permissions', name: '权限管理', path: '/system/permissions' },
    ],
  },
  sync: {
    id: 'sync',
    name: '同步监控',
    path: '/sync/outbox',
    kind: 'group',
    children: [
      { id: 'sync-outbox', name: 'Outbox', path: '/sync/outbox' },
      { id: 'sync-tasks', name: '任务监控', path: '/sync/tasks' },
    ],
  },
};

function allowByPermission(permissions = [], permission) {
  if (!permission) return true;
  return permissions.includes(permission);
}

export function buildMenuTree(profile = {}) {
  const role = profile.role === 'POSTOFFICE' ? 'CLERK' : (profile.role || 'CLERK');
  const permissions = profile.permissions || [];
  const facilityTypeCode = profile.facilityTypeCode || '';
  const rule = ROLE_MENU_RULES[role] || ROLE_MENU_RULES.CLERK;

  const nodes = [];
  for (const key of rule.top) {
    const entry = MENU_LIBRARY[key];
    if (!entry) continue;
    if (!entry.children) {
      nodes.push({ ...entry, children: [] });
      continue;
    }
    const children = entry.children
      .filter((child) => allowByPermission(permissions, child.permission))
      .filter((child) => {
        if (key !== 'sorting') return true;
        if (facilityTypeCode === 'INTERNATIONAL_GATEWAY') return true;
        return child.id !== 'sorting-route';
      });
    if (!children.length) continue;
    nodes.push({ ...entry, children });
  }
  return nodes;
}

export function flattenMenu(nodes = []) {
  const result = [];
  const walk = (items) => {
    items.forEach((item) => {
      result.push(item);
      if (item.children?.length) walk(item.children);
    });
  };
  walk(nodes);
  return result;
}

export function firstAccessiblePath(profile = {}) {
  const tree = buildMenuTree(profile);
  const flat = flattenMenu(tree);
  return flat[0]?.path || '/dashboard';
}
