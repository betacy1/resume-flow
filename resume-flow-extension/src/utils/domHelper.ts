/**
 * DOM 填充辅助函数
 * 用于 content script 中直接对元素设置值
 */

/**
 * 对指定元素设置值（React/Vue 兼容）
 */
export function setNativeValueForElement(el: HTMLElement, value: string): boolean {
  const tag = el.tagName.toLowerCase();

  if (tag === 'input' || tag === 'textarea') {
    return setNativeInputValue(el as HTMLInputElement | HTMLTextAreaElement, value);
  }
  if (tag === 'select') {
    return setSelectValue(el as HTMLSelectElement, value);
  }
  // contenteditable 或富文本编辑器
  el.textContent = value;
  el.dispatchEvent(new InputEvent('input', { bubbles: true, data: value }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  el.dispatchEvent(new Event('blur', { bubbles: true }));
  return true;
}

/**
 * 填入当前焦点元素
 */
export function fillFocusedElement(el: HTMLElement, content: string): boolean {
  return setNativeValueForElement(el, content);
}

/** 设置 input/textarea 的值 */
function setNativeInputValue(el: HTMLInputElement | HTMLTextAreaElement, value: string): boolean {
  try {
    const proto = el.tagName === 'INPUT' ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
    if (setter) {
      setter.call(el, value);
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

/** 设置 select 的值 */
function setSelectValue(el: HTMLSelectElement, value: string): boolean {
  let matched = false;
  for (const option of el.options) {
    if (option.value === value || option.text.trim() === value) {
      el.value = option.value;
      matched = true;
      break;
    }
  }
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  el.dispatchEvent(new Event('blur', { bubbles: true }));
  return matched;
}
