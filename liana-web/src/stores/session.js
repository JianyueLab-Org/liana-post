import { defineStore } from 'pinia';
import { authApi } from '../lib/api';
import { buildMenuTree } from '../lib/rbac';

const TOKEN_KEY = 'liana_token';
const USER_KEY = 'liana_user';

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
    menuTree: [],
    actionPermissions: [],
    dataPermissions: [],
    ready: false,
  }),
  getters: {
    hasAction: (state) => (code) => state.actionPermissions.includes(code),
    currentOrgName: (state) => state.user?.facilityCode || '未登录',
  },
  actions: {
    setSession(token, user) {
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
        const profile = await authApi.profile(username, this.token);
        this.user = profile;
      }
      if (this.user) {
        this.actionPermissions = this.user.permissions || [];
        this.menuTree = buildMenuTree({
          role: this.user.role,
          permissions: this.actionPermissions,
        });
        if (!this.menuTree.length && this.actionPermissions.includes('MAIL_CREATE')) {
          this.menuTree = buildMenuTree({ role: 'CLERK', permissions: this.actionPermissions });
        }
        this.dataPermissions = [];
      }
      this.ready = true;
      this.setSession(this.token, this.user);
    },
    logout() {
      this.setSession('', null);
      this.menuTree = [];
      this.actionPermissions = [];
      this.dataPermissions = [];
      this.ready = false;
    },
  },
});
