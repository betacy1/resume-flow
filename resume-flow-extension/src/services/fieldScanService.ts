import type { FieldInfo } from './apiClient';

const RICH_EDITORS_SELECTOR = [
  '[contenteditable="true"]',
  '[contenteditable=""]',
  '.ql-editor',
  '.w-e-text',
  '.ProseMirror',
  '.tox-edit-area iframe',
  '.ant-input',
  '.el-textarea__inner',
].join(', ');

export function scanFields(): FieldInfo[] {
  const fields: FieldInfo[] = [];
  const seen = new Set<string>();
  const selectors = ['input', 'textarea', 'select', RICH_EDITORS_SELECTOR];

  document.querySelectorAll(selectors.join(',')).forEach((node) => {
    const el = normalizeEditableElement(node as HTMLElement);
    if (!el) return;
    const info = extractFieldInfo(el);
    if (!info || seen.has(info.fieldId)) return;
    seen.add(info.fieldId);
    fields.push(info);
  });
  return fields;
}

/** 扫描单个元素（悬浮面板“填写当前输入框”使用） */
export function scanElement(el: HTMLElement): FieldInfo | null {
  const normalized = normalizeEditableElement(el);
  if (!normalized) return null;
  return extractFieldInfo(normalized);
}

function normalizeEditableElement(el: HTMLElement): HTMLElement | null {
  if (el.tagName.toLowerCase() === 'iframe') {
    return null;
  }
  if ((el as HTMLInputElement).type && ['hidden', 'submit', 'button', 'file', 'image', 'reset'].includes((el as HTMLInputElement).type.toLowerCase())) {
    return null;
  }
  return el;
}

function extractFieldInfo(el: HTMLElement): FieldInfo | null {
  const fieldId = buildFieldId(el);
  if (!fieldId) return null;
  el.setAttribute('data-rf-field-id', fieldId);
  const type = resolveFieldType(el);

  return {
    fieldId,
    tagName: el.tagName.toLowerCase(),
    label: findLabel(el),
    placeholder: el.getAttribute('placeholder') || '',
    type,
    name: el.getAttribute('name') || '',
    id: el.id || '',
    className: el.className || '',
    ariaLabel: el.getAttribute('aria-label') || '',
    parentText: findParentText(el),
    questionText: findQuestionText(el),
    nearbyText: findNearbyText(el),
    wordLimit: detectWordLimit(el),
    visible: isVisible(el),
    disabled: isDisabled(el),
  };
}

/** 探测字段字数限制：maxlength 属性与附近文本中的“N字以内/不超过N字”要求，取最小值 */
function detectWordLimit(el: HTMLElement): number | undefined {
  const limits: number[] = [];
  const maxlength = el.getAttribute('maxlength');
  if (maxlength && /^\d+$/.test(maxlength)) {
    limits.push(Number(maxlength));
  }
  const context = [
    findQuestionText(el),
    findNearbyText(el),
    el.getAttribute('placeholder') || '',
    el.getAttribute('aria-label') || '',
    findLabel(el),
  ].join(' ');
  const patterns = [
    /(?:不超过|最多|限制在|限)\s*(\d{2,4})\s*字/g,
    /(\d{2,4})\s*字(?:以内|内|左右|以下)/g,
  ];
  for (const pattern of patterns) {
    let m: RegExpExecArray | null;
    while ((m = pattern.exec(context)) !== null) {
      const value = Number(m[1]);
      if (value >= 20 && value <= 5000) limits.push(value);
    }
  }
  if (limits.length === 0) return undefined;
  return Math.min(...limits);
}

function buildFieldId(el: HTMLElement): string {
  return (
    el.getAttribute('data-field-id')
    || (el.id ? `id_${el.id}` : '')
    || (el.getAttribute('name') ? `name_${el.getAttribute('name')}` : '')
    || `xpath_${getXPath(el)}`
  );
}

function resolveFieldType(el: HTMLElement): string {
  const tagName = el.tagName.toLowerCase();
  if (tagName === 'textarea') return 'textarea';
  if (tagName === 'select') return 'select';
  if (el.getAttribute('contenteditable')) return 'contenteditable';
  if (tagName === 'input') return 'input';
  return 'richeditor';
}

function findLabel(el: HTMLElement): string {
  if (el.id) {
    const byFor = document.querySelector(`label[for="${el.id}"]`);
    if (byFor?.textContent?.trim()) return byFor.textContent.trim();
  }
  const labelParent = el.closest('label');
  if (labelParent?.textContent?.trim()) return labelParent.textContent.trim();
  const ariaLabelledBy = el.getAttribute('aria-labelledby');
  if (ariaLabelledBy) {
    const text = ariaLabelledBy.split(' ')
      .map((id) => document.getElementById(id)?.textContent?.trim() || '')
      .join(' ')
      .trim();
    if (text) return text;
  }
  return '';
}

function findParentText(el: HTMLElement): string {
  let current: HTMLElement | null = el.parentElement;
  for (let i = 0; i < 3 && current; i++) {
    const text = normalizedText(current.textContent || '');
    if (text && text.length < 260) return text;
    current = current.parentElement;
  }
  return '';
}

function findQuestionText(el: HTMLElement): string {
  const selector = '.ant-form-item-label,.el-form-item__label,.form-label,.question,.label,.title,p,span,div';
  const container = el.closest('.ant-form-item,.el-form-item,.form-item,[role="group"]') || el.parentElement;
  if (!container) return '';
  const candidate = container.querySelector(selector);
  return normalizedText(candidate?.textContent || '');
}

function findNearbyText(el: HTMLElement): string {
  const parts: string[] = [];
  const prev = el.previousElementSibling;
  const next = el.nextElementSibling;
  if (prev?.textContent) parts.push(normalizedText(prev.textContent));
  if (next?.textContent) parts.push(normalizedText(next.textContent));
  const holder = el.getAttribute('data-placeholder') || '';
  if (holder) parts.push(holder);
  return normalizedText(parts.join(' ')).slice(0, 320);
}

function normalizedText(raw: string): string {
  return raw.replace(/\s+/g, ' ').trim();
}

function isVisible(el: HTMLElement): boolean {
  const style = window.getComputedStyle(el);
  const rect = el.getBoundingClientRect();
  return style.display !== 'none'
    && style.visibility !== 'hidden'
    && Number(style.opacity) !== 0
    && rect.width > 0
    && rect.height > 0;
}

function isDisabled(el: HTMLElement): boolean {
  return (el as HTMLInputElement).disabled || el.getAttribute('aria-disabled') === 'true';
}

function getXPath(el: HTMLElement): string {
  if (el.id) return el.id;
  const parts: string[] = [];
  let current: HTMLElement | null = el;
  while (current && current !== document.body) {
    const parent: HTMLElement | null = current.parentElement;
    if (!parent) break;
    const curTag = current.tagName;
    const siblings = Array.from(parent.children).filter((c: Element) => c.tagName === curTag);
    const index = siblings.indexOf(current) + 1;
    parts.unshift(`${current.tagName.toLowerCase()}${siblings.length > 1 ? `[${index}]` : ''}`);
    current = parent;
  }
  return parts.join('/');
}
