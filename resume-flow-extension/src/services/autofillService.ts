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

export interface SetElementValueResult {
  success: boolean;
  reason: string;
  fieldType: string;
}

/** 识别元素类型（含常见富文本编辑器） */
function detectFieldType(el: HTMLElement): string {
  const tag = el.tagName.toLowerCase();
  if (tag === 'input') return `input[${(el as HTMLInputElement).type || 'text'}]`;
  if (tag === 'textarea') return 'textarea';
  if (tag === 'select') return 'select';
  if (el.classList.contains('ql-editor')) return 'richtext:quill';
  if (el.classList.contains('w-e-text') || el.classList.contains('w-e-text-container')) return 'richtext:wangeditor';
  if (el.classList.contains('ck-content')) return 'richtext:ckeditor';
  if (el.classList.contains('ProseMirror') || el.classList.contains('tox-edit-area')) return 'richtext:tinymce';
  if (el.getAttribute('contenteditable') === 'true' || el.getAttribute('contenteditable') === '') return 'contenteditable';
  return tag;
}

/** 富文本编辑器容器 → 实际可编辑元素 */
function resolveRichTarget(el: HTMLElement): HTMLElement {
  if (el.classList.contains('w-e-text-container') || el.classList.contains('tox-edit-area')) {
    const inner = el.querySelector<HTMLElement>('[contenteditable="true"], .w-e-text, iframe');
    if (inner && inner.tagName !== 'IFRAME') return inner;
  }
  return el;
}

/**
 * 将内容填入指定元素（手动点选填充统一入口）：
 * input/textarea 走原生 setter（兼容 React/Vue/AntD/Element Plus 受控组件）；
 * select 按 option 文本/值匹配；contenteditable 与富文本写入可编辑区域；
 * 填入后派发 keydown/input/change/keyup/blur 事件。
 */
export function setElementValue(element: HTMLElement, value: string): SetElementValueResult {
  const el = resolveRichTarget(element);
  const fieldType = detectFieldType(el);
  try {
    const tag = el.tagName.toLowerCase();
    let ok = false;
    if (tag === 'input' || tag === 'textarea') {
      ok = setNativeValue(el as HTMLInputElement | HTMLTextAreaElement, value);
    } else if (tag === 'select') {
      ok = setSelectValue(el as HTMLSelectElement, value);
      if (!ok) return { success: false, reason: '下拉框中未找到匹配选项', fieldType };
    } else {
      ok = setContentEditableValue(el, value);
    }
    if (!ok) return { success: false, reason: '写入失败', fieldType };
    el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
    el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
    return { success: true, reason: '', fieldType };
  } catch (err: any) {
    return { success: false, reason: String(err?.message || err), fieldType };
  }
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

/** 按 fieldId 定位页面元素（供悬浮面板预览确认后逐个填入复用） */
export function locateFieldElement(fieldId: string): HTMLElement | null {
  return findElement(fieldId);
}

/** 读取元素当前文本值（撤回填充前快照用） */
export function readElementText(el: HTMLElement): string {
  const target = resolveRichTarget(el);
  const tag = target.tagName.toLowerCase();
  if (tag === 'input' || tag === 'textarea') return (target as HTMLInputElement).value || '';
  if (tag === 'select') return (target as HTMLSelectElement).value || '';
  return target.innerText || target.textContent || '';
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
