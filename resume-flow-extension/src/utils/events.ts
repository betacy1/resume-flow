/**
 * 事件工具函数
 */

/** 消息类型枚举 */
export const MessageType = {
  SCAN_AND_FILL: 'SCAN_AND_FILL',
  FILL_MATERIAL: 'FILL_MATERIAL',
  CONFIRM_FILL: 'CONFIRM_FILL',
  GET_STATUS: 'GET_STATUS',
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
