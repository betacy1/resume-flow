/**
 * DOM 工具函数
 */

/** 安全地获取元素文本 */
export function getElementText(el: HTMLElement): string {
  return (el.textContent || el.innerText || '').trim();
}

/** 判断元素是否可见 */
export function isVisible(el: HTMLElement): boolean {
  const rect = el.getBoundingClientRect();
  const style = window.getComputedStyle(el);
  return (
    rect.width > 0 &&
    rect.height > 0 &&
    style.visibility !== 'hidden' &&
    style.display !== 'none'
  );
}

/** 判断元素是否在视口内 */
export function isInViewport(el: HTMLElement): boolean {
  const rect = el.getBoundingClientRect();
  return (
    rect.top >= 0 &&
    rect.left >= 0 &&
    rect.bottom <= window.innerHeight &&
    rect.right <= window.innerWidth
  );
}

/** 滚动到元素位置 */
export function scrollToElement(el: HTMLElement): void {
  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/** 高亮元素（添加临时边框） */
export function highlightElement(el: HTMLElement, duration = 2000): void {
  const originalBorder = el.style.border;
  const originalTransition = el.style.transition;
  el.style.transition = 'border 0.3s ease';
  el.style.border = '2px solid #409eff';
  setTimeout(() => {
    el.style.border = originalBorder;
    el.style.transition = originalTransition;
  }, duration);
}
