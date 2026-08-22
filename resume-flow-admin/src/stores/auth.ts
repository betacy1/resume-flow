import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('rf_token') || '');
  const username = ref<string>(localStorage.getItem('rf_username') || '');

  function setAuth(t: string, u: string) {
    token.value = t;
    username.value = u;
    localStorage.setItem('rf_token', t);
    localStorage.setItem('rf_username', u);
  }

  function clearAuth() {
    token.value = '';
    username.value = '';
    localStorage.removeItem('rf_token');
    localStorage.removeItem('rf_username');
  }

  function isLoggedIn(): boolean {
    return !!token.value;
  }

  return { token, username, setAuth, clearAuth, isLoggedIn };
});
