/**
 * 面板缩放服务
 * 为悬浮面板添加 右 / 下 / 右下角 / 左 / 上 五个缩放手柄：
 * - pointer 事件实现（鼠标与触控板均可），手柄 setPointerCapture 独占事件；
 * - 拖动实时更新尺寸（联动响应式布局），拖动结束回写状态；
 * - 约束：最小 360x420，最大不超过窗口的 90%；
 * - 拖动期间禁用文本选中（面板加 rf-resizing 类），不影响页面滚动与输入；
 * - 缩放与标题栏拖动互斥：手柄事件 stopPropagation，不会触发移动。
 */

export interface PanelResizeRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface PanelResizeOptions {
  /** 最小宽度（默认 360） */
  minWidth?: number;
  /** 最小高度（默认 420） */
  minHeight?: number;
  /** 相对窗口的最大比例（默认 0.9） */
  maxRatio?: number;
  /** 拖动过程中实时回调（布局自适应用） */
  onResize?: (rect: PanelResizeRect) => void;
  /** 松手后回写状态 */
  onCommit?: (rect: PanelResizeRect) => void;
}

type Edge = 'r' | 'b' | 'br' | 'l' | 't';

const EDGE_DEFS: Array<{ edge: Edge; cls: string; title: string }> = [
  { edge: 'r', cls: 'rf-resize-r', title: '拖动调整宽度' },
  { edge: 'b', cls: 'rf-resize-b', title: '拖动调整高度' },
  { edge: 'l', cls: 'rf-resize-l', title: '拖动调整宽度' },
  { edge: 't', cls: 'rf-resize-t', title: '拖动调整高度' },
  { edge: 'br', cls: 'rf-resize-br', title: '拖动调整宽高' },
];

function readRect(panel: HTMLElement): PanelResizeRect {
  return { x: panel.offsetLeft, y: panel.offsetTop, width: panel.offsetWidth, height: panel.offsetHeight };
}

/** 为面板挂载缩放手柄，返回卸载函数 */
export function attachResizeHandles(panel: HTMLElement, opts: PanelResizeOptions = {}): () => void {
  const minW = opts.minWidth ?? 360;
  const minH = opts.minHeight ?? 420;
  const maxRatio = opts.maxRatio ?? 0.9;
  const created: HTMLElement[] = [];

  for (const def of EDGE_DEFS) {
    const handle = document.createElement('div');
    handle.className = def.cls;
    handle.title = def.title;

    let resizing = false;
    let startX = 0;
    let startY = 0;
    let start: PanelResizeRect = { x: 0, y: 0, width: 0, height: 0 };

    handle.addEventListener('pointerdown', (e: PointerEvent) => {
      resizing = true;
      startX = e.clientX;
      startY = e.clientY;
      start = readRect(panel);
      panel.classList.add('rf-resizing');
      handle.setPointerCapture(e.pointerId);
      e.preventDefault();
      e.stopPropagation();
    });

    handle.addEventListener('pointermove', (e: PointerEvent) => {
      if (!resizing) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      const maxW = Math.floor(window.innerWidth * maxRatio);
      const maxH = Math.floor(window.innerHeight * maxRatio);
      let { x, y, width, height } = start;

      if (def.edge === 'r' || def.edge === 'br') {
        width = Math.max(minW, Math.min(start.width + dx, maxW));
      }
      if (def.edge === 'l') {
        const w = Math.max(minW, Math.min(start.width - dx, maxW));
        x = start.x + (start.width - w);
        width = w;
      }
      if (def.edge === 'b' || def.edge === 'br') {
        height = Math.max(minH, Math.min(start.height + dy, maxH));
      }
      if (def.edge === 't') {
        const h = Math.max(minH, Math.min(start.height - dy, maxH));
        y = start.y + (start.height - h);
        height = h;
      }

      panel.style.left = `${x}px`;
      panel.style.top = `${y}px`;
      panel.style.width = `${width}px`;
      panel.style.height = `${height}px`;
      opts.onResize?.({ x, y, width, height });
      e.stopPropagation();
    });

    const finish = (e: PointerEvent) => {
      if (!resizing) return;
      resizing = false;
      panel.classList.remove('rf-resizing');
      opts.onCommit?.(readRect(panel));
      try { handle.releasePointerCapture(e.pointerId); } catch { /* ignore */ }
      e.stopPropagation();
    };
    handle.addEventListener('pointerup', finish);
    handle.addEventListener('pointercancel', finish);

    panel.appendChild(handle);
    created.push(handle);
  }

  return () => created.forEach((n) => n.remove());
}
