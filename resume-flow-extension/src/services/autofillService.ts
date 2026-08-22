import type { AutofillMatchResponse } from './apiClient';

/** 自动填写阈值：>=0.75 自动填入 */
const AUTO_FILL_THRESHOLD = 0.75;
/** 确认阈值：0.5~0.75 建议人工确认，不自动填 */
const CONFIRM_THRESHOLD = 0.5;

export interface ConfirmItem {
  fieldId: string;
  matchedFieldName: string;
  confidence: number;
  value: string;
}

export function applyMatches(response: AutofillMatchResponse): {
  filled: number;
  skipped: number;
  sensitive: number;
  needConfirm: number;
  unmatched: number;
  details: string[];
  confirmItems: ConfirmItem[];
} {
  let filled = 0;
  let skipped = 0;
  let sensitive = 0;
  const confirmItems: ConfirmItem[] = [];
  const details: string[] = [];

  for (const match of response.matches || []) {
    const variantTag = match.variantDesc ? `[${match.variantDesc}]` : '';
    if (match.confidence < CONFIRM_THRESHOLD) {
      skipped++;
      details.push(`低置信度跳过: ${match.fieldId}(${match.confidence.toFixed(2)})`);
      continue;
    }
    if (match.confidence < AUTO_FILL_THRESHOLD) {
      skipped++;
      confirmItems.push({
        fieldId: match.fieldId,
        matchedFieldName: match.matchedFieldName,
        confidence: match.confidence,
        value: match.value,
      });
      details.push(`建议人工确认: ${match.fieldId} -> ${match.matchedFieldName}(${match.confidence.toFixed(2)})`);
      continue;
    }
    const el = findElement(match.fieldId);
    if (!el) {
      skipped++;
      details.push(`未找到字段: ${match.fieldId}`);
      continue;
    }
    const success = setFieldValue(el, match.value);
    if (success) {
      filled++;
      details.push(`已填充: ${match.fieldId} -> ${match.matchedFieldName}${variantTag}${match.sensitive ? '(敏感)' : ''}`);
    } else {
      skipped++;
      details.push(`填充失败: ${match.fieldId}`);
    }
  }

  for (const skip of response.skipped || []) {
    skipped++;
    if (skip.sensitive) sensitive++;
    details.push(`跳过: ${skip.fieldId} - ${skip.reason}`);
  }

  for (const item of response.unmatched || []) {
    details.push(`未匹配: ${item.fieldId} - ${item.reason}`);
  }

  return {
    filled,
    skipped,
    sensitive,
    needConfirm: confirmItems.length,
    unmatched: (response.unmatched || []).length,
    details,
    confirmItems,
  };
}

/** 手动确认填入某个中置信度字段 */
export function fillConfirmItem(item: ConfirmItem): boolean {
  const el = findElement(item.fieldId);
  if (!el) return false;
  return setFieldValue(el, item.value);
}

function findElement(fieldId: string): HTMLElement | null {
  let el = document.querySelector(`[data-rf-field-id="${fieldId}"]`);
  if (el) return el as HTMLElement;
  if (fieldId.startsWith('id_')) {
    return document.getElementById(fieldId.substring(3));
  }
  if (fieldId.startsWith('name_')) {
    el = document.querySelector(`[name="${fieldId.substring(5)}"]`);
    if (el) return el as HTMLElement;
  }
  return null;
}

function setFieldValue(el: HTMLElement, value: string): boolean {
  const tagName = el.tagName.toLowerCase();
  if (tagName === 'input' || tagName === 'textarea') {
    return setNativeValue(el as HTMLInputElement | HTMLTextAreaElement, value);
  }
  if (tagName === 'select') {
    return setSelectValue(el as HTMLSelectElement, value);
  }
  if (el.getAttribute('contenteditable')) {
    return setContentEditableValue(el, value);
  }
  return setContentEditableValue(el, value);
}

function setNativeValue(el: HTMLInputElement | HTMLTextAreaElement, value: string): boolean {
  try {
    const descriptor = Object.getOwnPropertyDescriptor(
      el.tagName === 'INPUT' ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype,
      'value',
    );
    if (descriptor?.set) {
      descriptor.set.call(el, value);
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
    return true;
  } catch {
    return false;
  }
}

function setSelectValue(el: HTMLSelectElement, value: string): boolean {
  try {
    let matched = false;
    for (const option of el.options) {
      if (option.value === value || option.text.trim() === value) {
        el.value = option.value;
        matched = true;
        break;
      }
    }
    if (!matched && el.options.length > 0) {
      for (const option of el.options) {
        if (value.includes(option.text.trim()) || option.text.includes(value)) {
          el.value = option.value;
          matched = true;
          break;
        }
      }
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
    return matched;
  } catch {
    return false;
  }
}

function setContentEditableValue(el: HTMLElement, value: string): boolean {
  try {
    el.innerText = value;
    el.textContent = value;
    el.dispatchEvent(new InputEvent('input', { bubbles: true, data: value }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
    return true;
  } catch {
    return false;
  }
}
