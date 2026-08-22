/**
 * Content Script - 注入到网页中
 * 负责扫描页面字段、接收匹配结果并填充表单
 * 安全限制：不自动点击提交/确认/下一步/投递/保存并提交按钮
 */

import { scanFields } from './services/fieldScanService';
import { applyMatches, fillConfirmItem } from './services/autofillService';
import { autofillMatch, type FieldInfo } from './services/apiClient';
import { MessageType } from './utils/events';
import { fillFocusedElement } from './utils/domHelper';

// 监听来自 popup/background 的消息
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log('[ResumeFlow] Content script 收到消息:', message.type);

  if (message.type === MessageType.SCAN_AND_FILL) {
    handleScanAndFill(message.templateId, message.audienceType)
      .then((result) => sendResponse(result))
      .catch((error) => sendResponse({ error: error.message }));
    return true; // 异步响应
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

  return false;
});

/**
 * 扫描页面字段并调用后端匹配接口进行填充
 */
async function handleScanAndFill(templateId: number | null, audienceType?: string): Promise<{
  total: number;
  filled: number;
  skipped: number;
  sensitive: number;
  needConfirm: number;
  unmatched: number;
  details: string[];
  confirmItems: { fieldId: string; matchedFieldName: string; confidence: number; value: string }[];
  unmatchedFields: { fieldId: string; label: string }[];
}> {
  // 1. 扫描页面字段
  const fields = scanFields();
  console.log('[ResumeFlow] 扫描到字段数:', fields.length);

  if (fields.length === 0) {
    return { total: 0, filled: 0, skipped: 0, sensitive: 0, needConfirm: 0, unmatched: 0, details: ['未扫描到可填写字段'], confirmItems: [], unmatchedFields: [] };
  }

  // 2. 调用后端匹配接口（传入模板受众风格，后端据此选择内容版本）
  const pageUrl = window.location.href;
  const pageTitle = document.title || '';
  const matchResponse = await autofillMatch(templateId, pageUrl, pageTitle, fields, audienceType);

  // 3. 填充匹配结果（>=0.75 自动填，0.5~0.75 待确认，绝不自动提交表单）
  const result = applyMatches(matchResponse);

  // 4. 为未匹配字段附带页面标签，供弹窗手动绑定使用
  const labelMap = new Map<string, string>(fields.map((f: FieldInfo) => [
    f.fieldId,
    f.label || f.questionText || f.placeholder || f.name || f.id,
  ]));
  const unmatchedFields = (matchResponse.unmatched || []).map((item) => ({
    fieldId: item.fieldId,
    label: labelMap.get(item.fieldId) || item.fieldId,
  }));

  return {
    total: fields.length,
    filled: result.filled,
    skipped: result.skipped,
    sensitive: result.sensitive,
    needConfirm: result.needConfirm,
    unmatched: result.unmatched,
    details: result.details,
    confirmItems: result.confirmItems,
    unmatchedFields,
  };
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
