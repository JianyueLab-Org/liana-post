import { defineStore } from 'pinia';
import { authApi, facilityApi } from '../lib/api.js';
import { buildMenuTree } from '../lib/rbac.js';

const TOKEN_KEY = 'liana_token';
const USER_KEY = 'liana_user';
function cachedUser() {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); }
  catch { localStorage.removeItem(USER_KEY); return null; }
}
const KNOWN_FACILITY_TYPE_CODES = new Set([
  'POST_OFFICE',
  'TRANSFER_CENTER',
  'INTERNATIONAL_GATEWAY',
  'AIR_HUB',
  'SEA_HUB',
]);
const SORTING_FACILITY_TYPE_CODES = new Set([
  'TRANSFER_CENTER',
  'INTERNATIONAL_GATEWAY',
  'AIR_HUB',
  'SEA_HUB',
]);

function normalizeFacilityTypeCode(value) {
  if (!value) return '';
  const code = String(value).trim().toUpperCase();
  return KNOWN_FACILITY_TYPE_CODES.has(code) ? code : '';
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: cachedUser(),
    currentFacility: null,
    menuTree: [],
    actionPermissions: [],
    dataPermissions: [],
    ready: false,
  }),
  getters: {
    hasAction: (state) => (code) => state.actionPermissions.includes(code),
    currentOrgName: (state) => state.user?.facilityCode || '未登录',
    facilityTypeCode: (state) => normalizeFacilityTypeCode(state.user?.facilityTypeCode || state.currentFacility?.typeCode),
    facilityTypeLabel: (state) => normalizeFacilityTypeCode(state.user?.facilityTypeCode || state.currentFacility?.typeCode) || '普通分拣局',
    isInternationalGateway: (state) => normalizeFacilityTypeCode(state.user?.facilityTypeCode || state.currentFacility?.typeCode) === 'INTERNATIONAL_GATEWAY',
    isSortingFacility: (state) => SORTING_FACILITY_TYPE_CODES.has(
      normalizeFacilityTypeCode(state.user?.facilityTypeCode || state.currentFacility?.typeCode),
    ),
  },
  actions: {
    setSession(token, user) {
      if (this.token !== (token || '') || this.user?.username !== user?.username) {
        this.ready = false;
        this.currentFacility = null;
        this.menuTree = [];
        this.actionPermissions = [];
        this.dataPermissions = [];
      }
      this.token = token || '';
      this.user = user || null;
      if (this.token) localStorage.setItem(TOKEN_KEY, this.token); else localStorage.removeItem(TOKEN_KEY);
      if (this.user) localStorage.setItem(USER_KEY, JSON.stringify(this.user)); else localStorage.removeItem(USER_KEY);
    },
    async bootstrap() {
      if (!this.token) {
        this.ready = true;
        return;
      }
      const username = this.user?.username;
      if (username) {
        try {
          this.user = await authApi.profile(username, this.token);
        } catch (error) {
          if (error.status === 401) this.logout();
          throw error;
        }
      } else {
        this.logout();
        return;
      }
      if (this.user?.facilityCode) {
        try {
          this.currentFacility = await facilityApi.getFacility(this.user.facilityCode, this.token);
          this.user = {
            ...this.user,
            facilityTypeCode: normalizeFacilityTypeCode(this.currentFacility?.typeCode || this.user.facilityTypeCode),
          };
        } catch (error) {
          if (error.status === 401) { this.logout(); throw error; }
          this.currentFacility = null;
        }
      }
      if (this.user) {
        const facilityTypeCode = normalizeFacilityTypeCode(this.user.facilityTypeCode || this.currentFacility?.typeCode);
        this.actionPermissions = this.user.permissions || [];
        this.menuTree = buildMenuTree({
          role: this.user.role,
          permissions: this.actionPermissions,
          facilityTypeCode,
        });
        if (!this.menuTree.length && this.actionPermissions.includes('MAIL_CREATE')) {
          this.menuTree = buildMenuTree({
            role: 'CLERK',
            permissions: this.actionPermissions,
            facilityTypeCode,
          });
        }
        this.dataPermissions = [];
      }
      this.ready = true;
      this.setSession(this.token, this.user);
    },
    logout() {
      this.setSession('', null);
      this.currentFacility = null;
      this.menuTree = [];
      this.actionPermissions = [];
      this.dataPermissions = [];
      this.ready = false;
    },
  },
});
