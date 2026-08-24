/**
 * Popup（简化入口）
 * 主交互为页面内悬浮面板（点击插件图标开关）；
 * popup 仅提供：登录/退出、开关悬浮面板、数据同步、打开管理后台。
 */

import { login, logout, isLoggedIn, getStoredAuth } from '../services/authService';
import { getBackendUrl } from '../services/storageService';
import { MessageType } from '../utils/events';

const loginSection = document.getElementById('login-section')!;
const mainSection = document.getElementById('main-section')!;
const backendUrlInput = document.getElementById('backend-url') as HTMLInputElement;
const usernameInput = document.getElementById('username') as HTMLInputElement;
const passwordInput = document.getElementById('password') as HTMLInputElement;
const btnLogin = document.getElementById('btn-login') as HTMLButtonElement;
const loginMsg = document.getElementById('login-msg')!;
const userInfo = document.getElementById('user-info')!;
const btnLogout = document.getElementById('btn-logout') as HTMLButtonElement;
const btnTogglePanel = document.getElementById('btn-toggle-panel') as HTMLButtonElement;
const btnClosePanel = document.getElementById('btn-close-panel') as HTMLButtonElement;
const btnSync = document.getElementById('btn-sync') as HTMLButtonElement;
const syncStatusEl = document.getElementById('sync-status')!;
const syncMsg = document.getElementById('sync-msg')!;
const btnOpenAdmin = document.getElementById('btn-open-admin') as HTMLButtonElement;
const btnOptions = document.getElementById('btn-options') as HTMLButtonElement;

async function init() {
  backendUrlInput.value = await getBackendUrl();
  if (await isLoggedIn()) {
    const auth = await getStoredAuth();
    showMainSection(auth?.username || '');
    await refreshSyncStatus();
  } else {
    showLoginSection();
  }
}

function showLoginSection() {
  loginSection.style.display = '';
  mainSection.style.display = 'none';
}

function showMainSection(username: string) {
  loginSection.style.display = 'none';
  mainSection.style.display = '';
  userInfo.textContent = `用户: ${username}`;
}

btnLogin.addEventListener('click', async () => {
  const backendUrl = backendUrlInput.value.trim();
  const username = usernameInput.value.trim();
  const password = passwordInput.value.trim();
  if (!backendUrl || !username || !password) {
    showLoginMsg('请填写完整登录信息', 'error');
    return;
  }
  btnLogin.disabled = true;
  try {
    const result = await login(username, password, backendUrl);
    showMainSection(result.username);
    showLoginMsg('登录成功', 'success');
    await refreshSyncStatus();
  } catch (e: any) {
    showLoginMsg(e.message || '登录失败', 'error');
  } finally {
    btnLogin.disabled = false;
  }
});

btnLogout.addEventListener('click', async () => {
  await logout();
  showLoginSection();
});

btnTogglePanel.addEventListener('click', async () => {
  const resp = await chrome.runtime.sendMessage({ type: MessageType.OPEN_PANEL });
  showSyncMsg(resp?.ok ? '已切换悬浮面板' : `操作失败：${resp?.message || '未知错误'}`,
    resp?.ok ? 'success' : 'error');
});

btnClosePanel.addEventListener('click', async () => {
  const resp = await chrome.runtime.sendMessage({ type: MessageType.CLOSE_PANEL });
  showSyncMsg(resp?.ok ? '面板已关闭' : `操作失败：${resp?.message || '未知错误'}`,
    resp?.ok ? 'success' : 'error');
});

btnSync.addEventListener('click', async () => {
  btnSync.disabled = true;
  showSyncMsg('同步中…', '');
  const resp = await chrome.runtime.sendMessage({ type: MessageType.SYNC_TRIGGER });
  if (resp?.ok) {
    showSyncMsg(`同步完成，当前数据版本 ${resp.profileVersion}`, 'success');
  } else {
    showSyncMsg(`同步失败：${resp?.message || '网络不可用，插件可继续使用本地缓存填写'}`, 'error');
  }
  await refreshSyncStatus();
  btnSync.disabled = false;
});

btnOpenAdmin.addEventListener('click', async () => {
  // 管理后台与后端同机部署（Nginx 80 端口），直接用已配置的后端地址打开
  const adminUrl = await getBackendUrl();
  chrome.tabs.create({ url: adminUrl });
});

btnOptions.addEventListener('click', () => {
  chrome.runtime.openOptionsPage();
});

/** 查询并展示同步状态：版本号 / 上次同步时间 / 是否有更新 */
async function refreshSyncStatus() {
  syncStatusEl.textContent = '检测中…';
  const resp = await chrome.runtime.sendMessage({ type: MessageType.SYNC_STATUS });
  if (!resp) {
    syncStatusEl.textContent = '无法获取同步状态';
    return;
  }
  if (resp.ok) {
    const localTime = resp.local?.lastSyncTime
      ? new Date(resp.local.lastSyncTime).toLocaleString() : '从未同步';
    syncStatusEl.innerHTML =
      `服务端版本：v${resp.remote.profileVersion}（${resp.remote.updatedAt || '-'}）<br/>`
      + `本地版本：${resp.local ? `v${resp.local.profileVersion}` : '无缓存'}　上次同步：${localTime}<br/>`
      + (resp.outdated ? '<b style="color:#e6a23c;">检测到更新，建议同步</b>'
        : '<b style="color:#67c23a;">数据已是最新</b>');
  } else if (resp.offline) {
    const localTime = resp.local?.lastSyncTime
      ? new Date(resp.local.lastSyncTime).toLocaleString() : '从未同步';
    syncStatusEl.innerHTML =
      `<b style="color:#f56c6c;">网络不可用：${resp.message}</b><br/>`
      + `本地缓存版本：${resp.local ? `v${resp.local.profileVersion}` : '无'}　上次同步：${localTime}<br/>`
      + '可继续使用本地缓存填写，数据可能不是最新版本。';
  } else {
    syncStatusEl.textContent = resp.message || '未登录';
  }
}

function showLoginMsg(text: string, type: 'success' | 'error' | '') {
  loginMsg.textContent = text;
  loginMsg.className = `msg ${type}`;
}

function showSyncMsg(text: string, type: 'success' | 'error' | '') {
  syncMsg.textContent = text;
  syncMsg.className = `msg ${type}`;
}

init();
