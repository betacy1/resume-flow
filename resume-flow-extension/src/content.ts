/**
 * Content Script - 注入到网页中
 * 负责扫描页面字段与填充表单；API 请求由 popup 发起（避免网页上下文的 CORS/混合内容限制）
 * 安全限制：不自动点击提交/确认/下一步/投递/保存并提交按钮
 */

import { scanFields } from './services/fieldScanService';
import { applyMatches, fillConfirmItem } from './services/autofillService';
import type { AutofillMatchResponse } from './services/apiClient';
import { MessageType } from './utils/events';
import { fillFocusedElement } from './utils/domHelper';
import { closePanel, togglePanel, panelExists, getPanelStatus, openPanel } from './panel/floatingPanel';
import { getPanelState } from './services/panelStateService';

// 防止编程式重复注入时重复注册监听器（隔离世界内 window 状态持久）
const injectedFlag = window as unknown as { __rfContentInjected?: boolean };
if (injectedFlag.__rfContentInjected) {
  console.log('[ResumeFlow] Content script 已注入，跳过重复注册');
} else {
  injectedFlag.__rfContentInjected = true;

  // 监听来自 popup/background 的消息
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('[ResumeFlow] Content script 收到消息:', message.type);

    if (message.type === MessageType.SCAN_FIELDS) {
      sendResponse({ fields: scanFields() });
      return false;
    }

    if (message.type === MessageType.APPLY_MATCHES) {
      const result = applyMatches(message.response as AutofillMatchResponse);
      sendResponse(result);
      return false;
    }

    if (message.type === MessageType.CONFIRM_FILL) {
      const success = fillConfirmItem({
        fieldId: message.fieldId,
        matchedFieldName: message.matchedFieldName || '',
        confidence: message.confidence || 0,
        value: message.value,
      });
      sendResponse({ success });
      return false;
    }

    if (message.type === MessageType.FILL_MATERIAL) {
      handleFillMaterial(message.content)
        .then((result) => sendResponse(result))
        .catch((error) => sendResponse({ error: error.message }));
      return true;
    }

    // ---- 悬浮面板：始终保留在页面上，不因点击外部/切换输入框而关闭 ----
    if (message.type === MessageType.OPEN_PANEL) {
      togglePanel()
        .then(() => sendResponse({ ok: true }))
        .catch((error) => sendResponse({ ok: false, error: String(error?.message || error) }));
      return true;
    }

    if (message.type === MessageType.CLOSE_PANEL) {
      closePanel()
        .then(() => sendResponse({ ok: true }))
        .catch((error) => sendResponse({ ok: false, error: String(error?.message || error) }));
      return true;
    }

    if (message.type === MessageType.PANEL_STATE) {
      getPanelStatus()
        .then((status) => sendResponse({ exists: panelExists(), ...status }))
        .catch(() => sendResponse({ exists: panelExists(), visible: false, minimized: false }));
      return true;
    }

    return false;
  });

  // 页面刷新后自动恢复悬浮面板：仅当上次状态 visible=true 时恢复；
  // 用户主动关闭后（visible=false）不自动弹出。
  getPanelState().then((st) => {
    if (st.visible) {
      openPanel().catch((err) => console.warn('[ResumeFlow] 恢复悬浮面板失败:', err));
    }
  });
}

/**
 * 填入指定文本到当前焦点元素
 * 用于长文本一键填入功能
 */
async function handleFillMaterial(content: string): Promise<{ success: boolean; message: string }> {
  const focused = document.activeElement as HTMLElement;
  if (!focused || !isFillableElement(focused)) {
    return { success: false, message: '请先点击网页中的文本框' };
  }

  const success = fillFocusedElement(focused, content);
  return {
    success,
    message: success ? '填入成功' : '填入失败',
  };
}

/** 判断元素是否可填写 */
function isFillableElement(el: HTMLElement): boolean {
  const tag = el.tagName.toLowerCase();
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return true;
  if (el.getAttribute('contenteditable')) return true;
  if (el.classList.contains('ql-editor') || el.classList.contains('w-e-text') || el.classList.contains('ProseMirror')) {
    return true;
  }
  return false;
}
