export const mockSession = {
  accessToken: 'mock-access-token',
  user: {
    id: 'u1001',
    username: 'operator.liu',
    name: '鍒樻晱',
    orgId: 'org-hk-001',
    orgName: '棣欐腐閭欢澶勭悊涓績',
    roles: ['operator'],
    permissionsVersion: 'v1',
  },
  permissions: {
    menuTree: [
      { id: 'dashboard', name: '宸ヤ綔鍙?, code: 'menu:dashboard', path: '/dashboard', type: 'menu', children: [] },
      { id: 'facility', name: '閭斂缃戠粶', code: 'menu:facility', path: '/facility', type: 'menu', children: [
        { id: 'facility-offices', name: '缃戠偣', code: 'menu:facility:offices', path: '/facility/offices', type: 'menu' },
        { id: 'facility-hubs', name: '鍒嗘嫞涓績', code: 'menu:facility:hubs', path: '/facility/hubs', type: 'menu' },
        { id: 'facility-routes', name: '杩愯緭璺嚎', code: 'menu:facility:routes', path: '/facility/routes', type: 'menu' },
      ]},
      { id: 'system', name: '绯荤粺绠＄悊', code: 'menu:system', path: '/system', type: 'menu', children: [
        { id: 'system-users', name: '鐢ㄦ埛绠＄悊', code: 'menu:system:users', path: '/system/users', type: 'menu' },
        { id: 'system-roles', name: '瑙掕壊绠＄悊', code: 'menu:system:roles', path: '/system/roles', type: 'menu' },
        { id: 'system-permissions', name: '鏉冮檺绠＄悊', code: 'menu:system:permissions', path: '/system/permissions', type: 'menu' },
      ]},
      { id: 'sync', name: '鍚屾鐩戞帶', code: 'menu:sync', path: '/sync', type: 'menu', children: [
        { id: 'sync-outbox', name: 'Outbox', code: 'menu:sync:outbox', path: '/sync/outbox', type: 'menu' },
        { id: 'sync-tasks', name: '浠诲姟鐩戞帶', code: 'menu:sync:tasks', path: '/sync/tasks', type: 'menu' },
      ]},
    ],
    actionPermissions: ['mail:create', 'mail:update', 'dispatch:create-bag', 'dispatch:handoff'],
    dataPermissions: [{ scope: 'org', value: 'org-hk-001' }],
  },
};

export const mockLists = {
  mail: [
    { id: 'mail-001', mailNo: 'MP202606090001', status: 'ACCEPTED', sender: '寮犱笁', receiver: '鐜嬩簲', origin: '棣欐腐涓幆缃戠偣', destination: '绂忓窞鍥介檯浜ゆ崲灞€', createdAt: '2026-06-09 08:30:00', updatedAt: '2026-06-09 09:15:00' },
    { id: 'mail-002', mailNo: 'MP202606090002', status: 'IN_SORTING', sender: '鏉庡洓', receiver: '闄堟櫒', origin: '涔濋緳婀剧綉鐐?, destination: '鍘﹂棬鍒嗘嫧涓績', createdAt: '2026-06-09 09:05:00', updatedAt: '2026-06-09 10:02:00' },
  ],
  bags: [
    { id: 'bag-001', bagNo: 'BG20260609001', status: 'DRAFT', originHub: '棣欐腐閭欢澶勭悊涓績', destinationHub: '绂忓窞鍥介檯浜ゆ崲灞€', itemCount: 32, createdAt: '2026-06-09 09:30:00' },
    { id: 'bag-002', bagNo: 'BG20260609002', status: 'CONFIRMED', originHub: '涔濋緳婀惧垎鎷ｄ腑蹇?, destinationHub: '鍘﹂棬鍒嗘嫧涓績', itemCount: 58, createdAt: '2026-06-09 10:05:00' },
  ],
  trackingEvents: [
    { eventTime: '2026-06-09 08:32:00', nodeName: '棣欐腐涓幆缃戠偣', eventType: 'ACCEPTED', description: '閭欢宸叉敹瀵? },
    { eventTime: '2026-06-09 09:25:00', nodeName: '涔濋緳婀惧垎鎷ｄ腑蹇?, eventType: 'SORTED', description: '瀹屾垚鍒嗘嫞寰呭皝鍙? },
    { eventTime: '2026-06-09 10:14:00', nodeName: '鍥介檯杞繍鍙?, eventType: 'IN_TRANSIT', description: '宸茶繘鍏ヨ浆杩愪氦鎺ユ祦绋? },
  ],
  offices: [
    { id: 'office-001', name: '棣欐腐涓幆缃戠偣', region: '棣欐腐', status: 'ACTIVE' },
    { id: 'office-002', name: '婢抽棬鏂板彛宀哥綉鐐?, region: '婢抽棬', status: 'ACTIVE' },
  ],
  hubs: [
    { id: 'hub-001', name: '棣欐腐閭欢澶勭悊涓績', region: '棣欐腐', status: 'ACTIVE' },
    { id: 'hub-002', name: '绂忓窞鍥介檯浜ゆ崲灞€', region: '绂忓缓', status: 'ACTIVE' },
  ],
  routes: [
    { id: 'route-001', code: 'HK-FZ-01', origin: '棣欐腐', destination: '绂忓窞', mode: 'AIR', status: 'ACTIVE' },
    { id: 'route-002', code: 'HK-XM-01', origin: '棣欐腐', destination: '鍘﹂棬', mode: 'SEA', status: 'PLANNED' },
  ],
  users: [
    { id: 'u1001', name: '鍒樻晱', username: 'operator.liu', role: 'operator', status: 'ACTIVE' },
    { id: 'u1002', name: '鍛ㄥ己', username: 'admin.zhou', role: 'admin', status: 'ACTIVE' },
  ],
  roles: [
    { id: 'role-operator', name: '鎿嶄綔鍛?, code: 'operator', status: 'ACTIVE' },
    { id: 'role-admin', name: '绯荤粺绠＄悊鍛?, code: 'admin', status: 'ACTIVE' },
  ],
  permissions: [
    { id: 'perm-mail-create', code: 'mail:create', type: 'action', name: '鏀跺瘎鍒涘缓' },
    { id: 'perm-dispatch-confirm', code: 'dispatch:confirm-bag', type: 'action', name: '閭纭' },
  ],
  outbox: [
    { id: 'outbox-001', aggregateType: 'Mail', eventType: 'MailAccepted', status: 'PENDING', createdAt: '2026-06-09 09:00:00' },
    { id: 'outbox-002', aggregateType: 'Bag', eventType: 'BagConfirmed', status: 'SENT', createdAt: '2026-06-09 10:20:00' },
  ],
  syncTasks: [
    { id: 'task-001', name: '閭欢杞ㄨ抗鍚屾', status: 'RUNNING', lastRunAt: '2026-06-09 10:30:00' },
    { id: 'task-002', name: '閭鐘舵€佹帹閫?, status: 'IDLE', lastRunAt: '2026-06-09 10:00:00' },
  ],
};