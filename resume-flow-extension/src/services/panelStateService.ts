/**
 * 悬浮面板状态服务
 * 面板的显示/最小化/位置/尺寸与选择项持久化到 chrome.storage.local，
 * 刷新页面后按上次状态自动恢复；关闭面板后不自动弹出。
 */

const STATE_KEY = 'rf_panel_state';

export interface PanelState {
  /** 是否显示面板（关闭后为 false，不自动弹出） */
  visible: boolean;
  /** 是否最小化（保留小按钮） */
  minimized: boolean;
  x: number;
  y: number;
  width: number;
  height: number;
  selectedTemplateId: number | null;
  selectedAudienceType: string;
  selectedJobDirection: string;
  selectedPriorityExperience: number | null;
  lastSyncTime: string | null;
  /** 宽屏编辑模式（宽度 640，适合长文本编辑与新增字段） */
  wideMode: boolean;
  /** 是否最大化（再点还原上次尺寸） */
  maximized: boolean;
  /** 最大化前的尺寸（还原用） */
  preMaxRect: { x: number; y: number; width: number; height: number } | null;
  /** 字号预设：小 12 / 标准 14 / 大 16 / 超大 18 / 自定义 */
  fontSizePreset: 'small' | 'normal' | 'large' | 'xlarge' | 'custom';
  /** 面板整体字号（px，12-22，仅作用于 Shadow DOM 内部） */
  fontSize: number;
  /** 行高：紧凑 1.3 / 标准 1.5 / 宽松 1.8 */
  lineHeight: 'compact' | 'normal' | 'loose';
  /** 内容显示模式：简洁（标题+摘要）/ 详细 / 调试（fieldKey、命中原因） */
  displayMode: 'simple' | 'detail' | 'debug';
  /** 横向滚动条：自动 / 始终显示 */
  scrollbarMode: 'auto' | 'always';
  /** 搜索是否仅限当前分类（默认全库搜索） */
  searchTabOnly: boolean;
  /** 最近一次状态更新时间 */
  lastUpdatedAt: string;
}

export const DEFAULT_PANEL_STATE: PanelState = {
  visible: false,
  minimized: false,
  x: -1,
  y: 96,
  width: 380,
  height: 560,
  selectedTemplateId: null,
  selectedAudienceType: '',
  selectedJobDirection: '',
  selectedPriorityExperience: null,
  lastSyncTime: null,
  wideMode: false,
  maximized: false,
  preMaxRect: null,
  fontSizePreset: 'normal',
  fontSize: 14,
  lineHeight: 'normal',
  displayMode: 'detail',
  scrollbarMode: 'auto',
  searchTabOnly: false,
  lastUpdatedAt: '',
};

/** 读取面板状态（不存在时返回默认值，首次打开时面板默认靠右显示） */
export function getPanelState(): Promise<PanelState> {
  return new Promise((resolve) => {
    chrome.storage.local.get([STATE_KEY], (result) => {
      const saved = result[STATE_KEY] || {};
      resolve({ ...DEFAULT_PANEL_STATE, ...saved });
    });
  });
}

/** 保存面板状态（合并写入） */
export function savePanelState(patch: Partial<PanelState>): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.get([STATE_KEY], (result) => {
      const merged = { ...DEFAULT_PANEL_STATE, ...(result[STATE_KEY] || {}), ...patch, lastUpdatedAt: new Date().toISOString() };
      chrome.storage.local.set({ [STATE_KEY]: merged }, () => resolve());
    });
  });
}
