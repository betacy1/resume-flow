/**
 * 使用偏好服务：最近使用 / 收藏 / 站点偏好
 * 本地保存到 chrome.storage.local（离线可用），并通过面板代理同步到后端用户偏好表。
 */

const USAGE_KEY = 'rf_usage_state';

export interface RecentItem {
  /** field=自定义字段 / material=开放题素材 / temp=临时编辑 */
  kind: 'field' | 'material' | 'temp';
  refId: number | null;
  name: string;
  /** 实际填入的内容（撤回与再次填入使用） */
  content: string;
  /** auto=一键填充 / manual=手动点选 */
  fillType: 'auto' | 'manual';
  time: string;
}

export interface SitePref {
  audienceType?: string;
  templateId?: number | null;
  jobDirection?: string;
}

export interface UsageState {
  recentUsed: RecentItem[];
  favoriteFieldIds: number[];
  favoriteMaterialIds: number[];
  /** 站点偏好：域名 → 默认模板受众与岗位方向 */
  sitePrefs: Record<string, SitePref>;
}

export const DEFAULT_USAGE_STATE: UsageState = {
  recentUsed: [],
  favoriteFieldIds: [],
  favoriteMaterialIds: [],
  sitePrefs: {},
};

export function getUsageState(): Promise<UsageState> {
  return new Promise((resolve) => {
    chrome.storage.local.get([USAGE_KEY], (result) => {
      resolve({ ...DEFAULT_USAGE_STATE, ...(result[USAGE_KEY] || {}) });
    });
  });
}

export function saveUsageState(patch: Partial<UsageState>): Promise<UsageState> {
  return new Promise((resolve) => {
    chrome.storage.local.get([USAGE_KEY], (result) => {
      const merged: UsageState = { ...DEFAULT_USAGE_STATE, ...(result[USAGE_KEY] || {}), ...patch };
      chrome.storage.local.set({ [USAGE_KEY]: merged }, () => resolve(merged));
    });
  });
}

/** 记录一次填入（最近使用最多保留 10 条，去重后置顶） */
export async function recordUsage(item: Omit<RecentItem, 'time'>): Promise<UsageState> {
  const state = await getUsageState();
  const rest = state.recentUsed.filter(
    (r) => !(r.kind === item.kind && r.refId === item.refId && r.content === item.content),
  );
  const recentUsed = [{ ...item, time: new Date().toISOString() }, ...rest].slice(0, 10);
  return saveUsageState({ recentUsed });
}

export async function toggleFieldFavorite(fieldId: number): Promise<UsageState> {
  const state = await getUsageState();
  const set = new Set(state.favoriteFieldIds);
  if (set.has(fieldId)) set.delete(fieldId);
  else set.add(fieldId);
  return saveUsageState({ favoriteFieldIds: Array.from(set) });
}

export async function toggleMaterialFavorite(materialId: number): Promise<UsageState> {
  const state = await getUsageState();
  const set = new Set(state.favoriteMaterialIds);
  if (set.has(materialId)) set.delete(materialId);
  else set.add(materialId);
  return saveUsageState({ favoriteMaterialIds: Array.from(set) });
}

export async function saveSitePref(host: string, pref: SitePref): Promise<UsageState> {
  const state = await getUsageState();
  const sitePrefs = { ...state.sitePrefs, [host]: pref };
  return saveUsageState({ sitePrefs });
}
