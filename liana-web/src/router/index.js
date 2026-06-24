import { createRouter, createWebHistory } from 'vue-router';
import { useSessionStore } from '../stores/session';
import LoginView from '../views/LoginView.vue';
import LayoutView from '../views/layout/LayoutView.vue';
import DashboardView from '../views/dashboard/DashboardView.vue';
import MailCreateView from '../views/mail/MailCreateView.vue';
import MailListView from '../views/mail/MailListView.vue';
import MailDetailView from '../views/mail/MailDetailView.vue';
import DispatchBagsView from '../views/dispatch/DispatchBagsView.vue';
import DispatchBatchesView from '../views/dispatch/DispatchBatchesView.vue';
import DispatchCreateBagView from '../views/dispatch/DispatchCreateBagView.vue';
import DispatchHandoffView from '../views/dispatch/DispatchHandoffView.vue';
import SortingManifestsView from '../views/sorting/SortingManifestsView.vue';
import SortingReceiveView from '../views/sorting/SortingReceiveView.vue';
import SortingUnpackView from '../views/sorting/SortingUnpackView.vue';
import SortingRouteView from '../views/sorting/SortingRouteView.vue';
import SortingRebagView from '../views/sorting/SortingRebagView.vue';
import SortingExportView from '../views/sorting/SortingExportView.vue';
import TrackingSearchView from '../views/tracking/TrackingSearchView.vue';
import TrackingEventsView from '../views/tracking/TrackingEventsView.vue';
import FacilityOfficesView from '../views/facility/FacilityOfficesView.vue';
import FacilityHubsView from '../views/facility/FacilityHubsView.vue';
import FacilityRoutesView from '../views/facility/FacilityRoutesView.vue';
import FacilityAdminView from '../views/facility/FacilityAdminView.vue';
import TransportAssetsView from '../views/transport/TransportAssetsView.vue';
import TransportRoutesView from '../views/transport/TransportRoutesView.vue';
import TransportSchedulesView from '../views/transport/TransportSchedulesView.vue';
import TransportTasksView from '../views/transport/TransportTasksView.vue';
import SystemUsersView from '../views/system/SystemUsersView.vue';
import SystemRolesView from '../views/system/SystemRolesView.vue';
import SystemPermissionsView from '../views/system/SystemPermissionsView.vue';
import SystemSentinelView from '../views/system/SystemSentinelView.vue';
import SyncOutboxView from '../views/sync/SyncOutboxView.vue';
import SyncTasksView from '../views/sync/SyncTasksView.vue';
import SyncOverviewView from '../views/sync/SyncOverviewView.vue';
import SyncRetriesView from '../views/sync/SyncRetriesView.vue';
import CountryCatalogView from '../views/catalog/CountryCatalogView.vue';
import ServiceTypeCatalogView from '../views/catalog/ServiceTypeCatalogView.vue';
import PostOfficeDeliveryView from '../views/delivery/PostOfficeDeliveryView.vue';
import PostOfficePackagesView from '../views/delivery/PostOfficePackagesView.vue';
import PostOfficeDeliveryPrintView from '../views/delivery/PostOfficeDeliveryPrintView.vue';

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true, title: '登录' } },
  {
    path: '/layout',
    component: LayoutView,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard' },
      { path: '/dashboard', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
      { path: '/mail/create', name: 'mail-create', component: MailCreateView, meta: { title: '收寄录入', action: 'MAIL_CREATE' } },
      { path: '/mail/list', name: 'mail-list', component: MailListView, meta: { title: '邮件台账', action: 'MAIL_QUERY' } },
      { path: '/mail/detail/:id', name: 'mail-detail', component: MailDetailView, props: true, meta: { title: '邮件详情', action: 'MAIL_QUERY' } },
      { path: '/dispatch/bags', name: 'dispatch-bags', component: DispatchBagsView, meta: { title: '邮袋管理' } },
      { path: '/dispatch/batches', name: 'dispatch-batches', component: DispatchBatchesView, meta: { title: '批次管理' } },
      { path: '/dispatch/create-bag', name: 'dispatch-create-bag', component: DispatchCreateBagView, meta: { title: '建袋作业' } },
      { path: '/dispatch/handoff', name: 'dispatch-handoff', component: DispatchHandoffView, meta: { title: '交接确认' } },
      { path: '/sorting/manifests', name: 'sorting-manifests', component: SortingManifestsView, meta: { title: '路单管理' } },
      { path: '/sorting/receive', name: 'sorting-receive', component: SortingReceiveView, meta: { title: '接收勾核' } },
      { path: '/sorting/unpack', name: 'sorting-unpack', component: SortingUnpackView, meta: { title: '开拆作业' } },
      { path: '/sorting/route', name: 'sorting-route', component: SortingRouteView, meta: { title: '路由计算' } },
      { path: '/sorting/export', name: 'sorting-export', component: SortingExportView, meta: { title: '出口处理' } },
      { path: '/sorting/rebag', name: 'sorting-rebag', component: SortingRebagView, meta: { title: '再次封发' } },
      { path: '/tracking/search', name: 'tracking-search', component: TrackingSearchView, meta: { title: '轨迹查询', action: 'TRACK_QUERY' } },
      { path: '/tracking/events', name: 'tracking-events', component: TrackingEventsView, meta: { title: '事件流', action: 'TRACK_QUERY' } },
      { path: '/facility/offices', name: 'facility-offices', component: FacilityOfficesView, meta: { title: '网点管理' } },
      { path: '/facility/hubs', name: 'facility-hubs', component: FacilityHubsView, meta: { title: '分拣中心' } },
      { path: '/facility/routes', name: 'facility-routes', component: FacilityRoutesView, meta: { title: '运输路线' } },
      { path: '/facility/admin', name: 'facility-admin', component: FacilityAdminView, meta: { title: '机构管理' } },
      { path: '/transport/assets', name: 'transport-assets', component: TransportAssetsView, meta: { title: '运输资源' } },
      { path: '/transport/routes', name: 'transport-routes', component: TransportRoutesView, meta: { title: '运输线路' } },
      { path: '/transport/schedules', name: 'transport-schedules', component: TransportSchedulesView, meta: { title: '运输计划' } },
      { path: '/transport/tasks', name: 'transport-tasks', component: TransportTasksView, meta: { title: '运输任务' } },
      { path: '/system/users', name: 'system-users', component: SystemUsersView, meta: { title: '用户管理', action: 'USER_ADMIN' } },
      { path: '/system/roles', name: 'system-roles', component: SystemRolesView, meta: { title: '角色管理', action: 'ROLE_ADMIN' } },
      { path: '/system/permissions', name: 'system-permissions', component: SystemPermissionsView, meta: { title: '权限管理', action: 'ROLE_ADMIN' } },
      { path: '/system/sentinel', name: 'system-sentinel', component: SystemSentinelView, meta: { title: 'Sentinel 监控', action: 'ROLE_ADMIN' } },
      { path: '/catalog/countries', name: 'catalog-countries', component: CountryCatalogView, meta: { title: '国家管理' } },
      { path: '/catalog/service-types', name: 'catalog-service-types', component: ServiceTypeCatalogView, meta: { title: '服务类型' } },
      { path: '/delivery/postoffice', name: 'delivery-postoffice', component: PostOfficeDeliveryView, meta: { title: '投递工作台', action: 'MAIL_QUERY' } },
      { path: '/delivery/print', name: 'delivery-print', component: PostOfficeDeliveryPrintView, meta: { title: '打印投递单', action: 'MAIL_QUERY' } },
      { path: '/delivery/packages', name: 'delivery-packages', component: PostOfficePackagesView, meta: { title: '接收总包', action: 'MAIL_QUERY' } },
      { path: '/sync/overview', name: 'sync-overview', component: SyncOverviewView, meta: { title: '同步总览' } },
      { path: '/sync/outbox', name: 'sync-outbox', component: SyncOutboxView, meta: { title: 'Outbox' } },
      { path: '/sync/tasks', name: 'sync-tasks', component: SyncTasksView, meta: { title: '任务监控' } },
      { path: '/sync/retries', name: 'sync-retries', component: SyncRetriesView, meta: { title: '重试记录' } },
      { path: '/mail', redirect: '/mail/list' },
      { path: '/dispatch', redirect: '/dispatch/bags' },
      { path: '/sorting', redirect: '/sorting/receive' },
      { path: '/tracking', redirect: '/tracking/search' },
      { path: '/facility', redirect: '/facility/offices' },
      { path: '/transport', redirect: '/transport/assets' },
      { path: '/system', redirect: '/system/users' },
      { path: '/catalog', redirect: '/catalog/countries' },
      { path: '/delivery', redirect: '/delivery/packages' },
      { path: '/sync', redirect: '/sync/overview' },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
];

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior() { return { top: 0 }; } });

router.beforeEach(async (to) => {
  const session = useSessionStore();
  if (to.meta.public) {
    if (to.path === '/login' && session.token) return '/dashboard';
    return true;
  }
  if (!session.token) return '/login';
  if (!session.ready) await session.bootstrap();
  if (to.path.startsWith('/sorting') && !session.isSortingFacility) {
    return '/dashboard';
  }
  if (to.meta.action && !session.hasAction(to.meta.action)) {
    return '/dashboard';
  }
  if (to.path === '/sorting' || to.path === '/sorting/') {
    return session.isInternationalGateway ? '/sorting/export' : '/sorting/receive';
  }
  if (to.path === '/sorting/export' && !session.isInternationalGateway) {
    return '/sorting/receive';
  }
  return true;
});

export default router;
