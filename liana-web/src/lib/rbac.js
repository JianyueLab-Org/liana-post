const ROLE_MENU_RULES = {
  CLERK: {
    top: ['dashboard', 'mail', 'catalog', 'dispatch', 'sorting', 'tracking', 'delivery'],
    children: {
      mail: ['mail-create', 'mail-list'],
      catalog: ['catalog-countries', 'catalog-service-types'],
      dispatch: ['dispatch-bags', 'dispatch-batches', 'dispatch-create-bag', 'dispatch-handoff'],
      sorting: ['sorting-unpack', 'sorting-route', 'sorting-rebag'],
      delivery: ['delivery-postoffice-packages', 'delivery-postoffice-print', 'delivery-postoffice'],
      tracking: ['tracking-search', 'tracking-events'],
    },
  },
  MANAGER: {
    top: ['dashboard', 'mail', 'catalog', 'tracking', 'facility', 'transport', 'sorting'],
    children: {
      mail: ['mail-create', 'mail-list'],
      catalog: ['catalog-countries', 'catalog-service-types'],
      tracking: ['tracking-search', 'tracking-events'],
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
      sorting: ['sorting-manifests', 'sorting-receive', 'sorting-unpack', 'sorting-route', 'sorting-export', 'sorting-rebag'],
      tracking: ['tracking-search', 'tracking-events'],
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
      system: ['system-users', 'system-roles', 'system-permissions', 'system-sentinel'],
      sync: ['sync-overview', 'sync-outbox', 'sync-tasks', 'sync-retries'],
    },
  },
};

const MENU_LIBRARY = {
  dashboard: { id: 'dashboard', name: '工作台', path: '/dashboard', kind: 'link' },
  mail: {
    id: 'mail',
    name: '邮件收寄',
    path: '/mail/create',
    kind: 'group',
    children: [
      { id: 'mail-create', name: '收寄录入', path: '/mail/create', permission: 'MAIL_CREATE' },
      { id: 'mail-list', name: '邮件台账', path: '/mail/list', permission: 'MAIL_QUERY' },
    ],
  },
  delivery: {
    id: 'delivery',
    name: '末端投递',
    path: '/delivery/packages',
    kind: 'group',
    children: [
      { id: 'delivery-postoffice-packages', name: '接收总包', path: '/delivery/packages', permission: 'MAIL_QUERY' },
      { id: 'delivery-postoffice-print', name: '打印投递单', path: '/delivery/print', permission: 'MAIL_QUERY' },
      { id: 'delivery-postoffice', name: '投递工作台', path: '/delivery/postoffice', permission: 'MAIL_QUERY' },
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
    name: '总包封发',
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
    name: '分拣作业',
    path: '/sorting/manifests',
    kind: 'group',
    children: [
      { id: 'sorting-manifests', name: '路单管理', path: '/sorting/manifests' },
      { id: 'sorting-receive', name: '接收勾核', path: '/sorting/receive' },
      { id: 'sorting-unpack', name: '开拆作业', path: '/sorting/unpack' },
      { id: 'sorting-route', name: '路由计算', path: '/sorting/route' },
      { id: 'sorting-export', name: '出口处理', path: '/sorting/export' },
      { id: 'sorting-rebag', name: '再次封发', path: '/sorting/rebag' },
    ],
  },
  tracking: {
    id: 'tracking',
    name: '轨迹查询',
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
      { id: 'facility-offices', name: '网点管理', path: '/facility/offices' },
      { id: 'facility-hubs', name: '分拣中心', path: '/facility/hubs' },
      { id: 'facility-routes', name: '封发关系', path: '/facility/routes' },
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
      { id: 'system-users', name: '用户管理', path: '/system/users', permission: 'USER_ADMIN' },
      { id: 'system-roles', name: '角色管理', path: '/system/roles', permission: 'ROLE_ADMIN' },
      { id: 'system-permissions', name: '权限管理', path: '/system/permissions', permission: 'ROLE_ADMIN' },
      { id: 'system-sentinel', name: 'Sentinel 监控', path: '/system/sentinel', permission: 'ROLE_ADMIN' },
    ],
  },
  sync: {
    id: 'sync',
    name: '同步补偿',
    path: '/sync/overview',
    kind: 'group',
    children: [
      { id: 'sync-overview', name: '同步总览', path: '/sync/overview' },
      { id: 'sync-outbox', name: 'Outbox 消息', path: '/sync/outbox' },
      { id: 'sync-tasks', name: '同步任务', path: '/sync/tasks' },
      { id: 'sync-retries', name: '重试记录', path: '/sync/retries' },
    ],
  },
};

function allowByPermission(permissions = [], permission) {
  if (!permission) return true;
  return permissions.includes(permission);
}

function allowSortingSection(facilityTypeCode, roleKey) {
  const code = String(facilityTypeCode || '').toUpperCase();
  if (code === 'POST_OFFICE') {
    return false;
  }
  if (roleKey === 'SORTER') {
    return true;
  }
  return code !== '';
}

export function buildMenuTree(profile = {}) {
  const role = profile.role || 'CLERK';
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
    if (key === 'sorting' && !allowSortingSection(facilityTypeCode, role)) {
      continue;
    }
    const allowedChildren = rule.children[key] || [];
    const children = entry.children
      .filter((child) => allowedChildren.includes(child.id))
      .filter((child) => allowByPermission(permissions, child.permission))
      .filter((child) => {
        if (key !== 'sorting') return true;
        if (facilityTypeCode === 'INTERNATIONAL_GATEWAY') return true;
        return child.id !== 'sorting-export';
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
