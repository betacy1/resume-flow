/**
 * 事件工具函数
 */

/** 消息类型枚举 */
export const MessageType = {
  SCAN_FIELDS: 'SCAN_FIELDS',
  APPLY_MATCHES: 'APPLY_MATCHES',
  FILL_MATERIAL: 'FILL_MATERIAL',
  CONFIRM_FILL: 'CONFIRM_FILL',
  GET_STATUS: 'GET_STATUS',
  /** 显示/切换悬浮面板（background → content） */
  OPEN_PANEL: 'OPEN_PANEL',
  /** 关闭悬浮面板（popup → content） */
  CLOSE_PANEL: 'CLOSE_PANEL',
  /** 面板查询自身状态（background/popup → content） */
  PANEL_STATE: 'PANEL_STATE',
  /** API 代理（content → background，避免网页上下文 CORS/混合内容限制） */
  API_PROXY: 'API_PROXY',
  /** 查询同步状态（popup → background） */
  SYNC_STATUS: 'SYNC_STATUS',
  /** 触发全量同步（popup → background） */
  SYNC_TRIGGER: 'SYNC_TRIGGER',
} as const;

/** 发送消息到 content script */
export function sendToContentScript(tabId: number, message: any): Promise<any> {
  return chrome.tabs.sendMessage(tabId, message);
}

/** 发送消息到 background */
export function sendToBackground(message: any): Promise<any> {
  return chrome.runtime.sendMessage(message);
}

/** 监听消息 */
export function onMessage(
  callback: (message: any, sender: chrome.runtime.MessageSender, sendResponse: (response: any) => void) => void,
) {
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    callback(message, sender, sendResponse);
    return true; // 保持消息通道开放
  });
}
