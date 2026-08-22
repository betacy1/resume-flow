/**
 * Background Service Worker
 * 处理跨标签页通信和后端 API 调用
 */

import { MessageType } from './utils/events';

// 安装事件
chrome.runtime.onInstalled.addListener((details) => {
  console.log('[ResumeFlow] 插件已安装', details.reason);
});

// 监听来自 popup 的消息
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log('[ResumeFlow] Background 收到消息:', message.type);

  if (message.type === MessageType.GET_STATUS) {
    sendResponse({ status: 'ready' });
    return false;
  }

  return true;
});

// 监听标签页更新，在页面加载完成后注入 content script（如果需要）
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url) {
    // content script 已经通过 manifest 自动注入，这里不需要额外操作
  }
});
