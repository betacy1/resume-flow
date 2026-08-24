/**
 * Background Service Worker
 * 职责：
 * 1. 点击插件图标 → 确保 content script 已注入 → 显示/切换悬浮面板；
 * 2. 代理 content script 的后端 API 请求（避免网页上下文 CORS/混合内容限制）；
 * 3. 为 popup 提供同步状态查询与全量同步触发。
 */

import { MessageType } from './utils/events';
import { getAuth, getBackendUrl } from './services/storageService';
import { getSyncStatus, getSyncFull } from './services/apiClient';
import { getSyncCache, saveSyncCache } from './services/syncCacheService';

chrome.runtime.onInstalled.addListener((details) => {
  console.log('[ResumeFlow] 插件已安装', details.reason);
});

/** 点击插件图标：未注入则注入；面板未显示则显示；已显示则切换最小化 */
chrome.action.onClicked.addListener(async (tab) => {
  if (!tab.id || !tab.url) return;
  if (/^(chrome|edge|about|view-source|devtools):/i.test(tab.url)
    || /chrome\.google\.com\/webstore/i.test(tab.url)) {
    console.log('[ResumeFlow] 浏览器内部页面不支持注入悬浮面板');
    return;
  }
  try {
    await sendToTabWithInjection(tab.id, tab.url, { type: MessageType.OPEN_PANEL });
  } catch (err) {
    console.warn('[ResumeFlow] 打开面板失败:', err);
  }
});

/** 向标签页发消息；接收端不存在时编程式注入 content script 后重试 */
async function sendToTabWithInjection(tabId: number, url: string, message: any): Promise<any> {
  try {
    return await chrome.tabs.sendMessage(tabId, message);
  } catch (err: any) {
    const msg = String(err?.message || '');
    if (!/Receiving end does not exist|Could not establish connection|tab has not been injected/i.test(msg)) {
      throw err;
    }
    const files = chrome.runtime.getManifest().content_scripts?.[0]?.js || [];
    await chrome.scripting.executeScript({ target: { tabId }, files });
    return chrome.tabs.sendMessage(tabId, message);
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === MessageType.GET_STATUS) {
    sendResponse({ status: 'ready' });
    return false;
  }

  // content script 的后端请求代理（扩展上下文不受网页 CORS/混合内容限制）
  if (message.type === MessageType.API_PROXY) {
    proxyRequest(message.path, message.method || 'GET', message.body)
      .then((data) => sendResponse({ ok: true, data }))
      .catch((err) => sendResponse({ ok: false, message: String(err?.message || err) }));
    return true;
  }

  // popup：同步状态 + 本地缓存摘要
  if (message.type === MessageType.SYNC_STATUS) {
    (async () => {
      try {
        const auth = await getAuth();
        if (!auth) {
          return { ok: false, message: '未登录' };
        }
        const [remote, local] = await Promise.all([getSyncStatus(), getSyncCache()]);
        const outdated = !local
          || local.currentProfileVersion < remote.profileVersion
          || local.currentDataHash !== remote.dataHash;
        return {
          ok: true,
          remote,
          local: local ? {
            profileVersion: local.currentProfileVersion,
            lastSyncTime: local.lastSyncTime,
          } : null,
          outdated,
        };
      } catch (err: any) {
        const local = await getSyncCache();
        return { ok: false, message: String(err?.message || err), offline: true, local };
      }
    })().then(sendResponse);
    return true;
  }

  // popup：触发全量同步
  if (message.type === MessageType.SYNC_TRIGGER) {
    (async () => {
      try {
        const payload = await getSyncFull();
        const cache = await saveSyncCache(payload);
        return { ok: true, profileVersion: cache.currentProfileVersion, lastSyncTime: cache.lastSyncTime };
      } catch (err: any) {
        return { ok: false, message: String(err?.message || err) };
      }
    })().then(sendResponse);
    return true;
  }

  // popup：向当前标签页转发面板消息（打开/关闭）
  if (message.type === MessageType.OPEN_PANEL || message.type === MessageType.CLOSE_PANEL) {
    (async () => {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (!tab?.id || !tab.url) {
        return { ok: false, message: '无法获取当前标签页' };
      }
      try {
        return await sendToTabWithInjection(tab.id, tab.url, { type: message.type });
      } catch (err: any) {
        return { ok: false, message: String(err?.message || err) };
      }
    })().then(sendResponse);
    return true;
  }

  return false;
});

/** 以扩展身份调用后端接口（自动附加 token）；409 冲突体透传给面板供冲突处理 */
async function proxyRequest(path: string, method: string, body?: any): Promise<any> {
  const backendUrl = await getBackendUrl();
  const auth = await getAuth();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (auth?.token) {
    headers.Authorization = `Bearer ${auth.token}`;
  }
  const response = await fetch(`${backendUrl}${path}`, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    let result: any = null;
    try {
      result = await response.json();
    } catch { /* 非 JSON 响应 */ }
    if (response.status === 409) {
      // 冲突体透传：面板据此弹出“拉取最新 / 覆盖保存”
      return { __rfStatus: 409, message: result?.message || '数据冲突', data: result?.data ?? null };
    }
    throw new Error(result?.message || `HTTP ${response.status}: ${response.statusText}`);
  }
  const result = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败');
  }
  return result.data;
}
