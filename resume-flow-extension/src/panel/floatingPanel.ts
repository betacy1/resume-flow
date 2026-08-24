/**
 * ResumeFlow 悬浮面板
 * 由 content script 注入当前页面，承载于 Shadow DOM：
 * - 样式完全隔离，不受招聘网站 CSS 污染；
 * - 始终保留在页面上：不因点击页面其他位置、切换输入框、滚动、选择文本而关闭；
 * - 可拖动（记住位置）、可最小化（保留小按钮）、可关闭（关闭后不自动弹出）；
 * - 面板内事件不冒泡到招聘页面，也不影响页面原有输入/点击/滚动；
 * - 所有 API 请求经 background 代理（避免网页上下文 CORS/混合内容限制）；
 * - 绝不自动点击页面上的提交/确认按钮。
 */

import { MessageType } from '../utils/events';
import { getAuth } from '../services/storageService';
import { getPanelState, savePanelState, type PanelState } from '../services/panelStateService';
import { getSyncCache, saveSyncCache, patchSyncCache, restoreSyncCache, type SyncCache } from '../services/syncCacheService';
import { scanFields, scanElement } from '../services/fieldScanService';
import { applyMatches, fillConfirmItem, setElementValue, locateFieldElement, readElementText, type ConfirmItem } from '../services/autofillService';
import { recommendFields, type RecommendItem } from '../services/localMatchService';
import { checkContent } from '../services/qualityCheck';
import {
  getUsageState, saveUsageState, recordUsage, toggleFieldFavorite, toggleMaterialFavorite, saveSitePref,
  type UsageState, type SitePref,
} from '../services/usageService';
import { setNativeValueForElement } from '../utils/domHelper';
import {
  ConflictError, unwrapOrConflict,
  type AutofillMatchResponse, type CustomFieldItem, type FieldInfo, type MatchResult, type PluginFieldWriteResult,
} from '../services/apiClient';

const HOST_ID = '__resumeflow_panel_host__';
const Z_INDEX = '2147483647';

let host: HTMLDivElement | null = null;
let shadow: ShadowRoot | null = null;
/** 页面上最后一次聚焦的可填写元素（点击面板会转移焦点，需自行记录） */
let lastExternalFocus: HTMLElement | null = null;
/** 最近一次匹配结果（渲染报告与手动绑定用） */
let lastMatchResponse: AutofillMatchResponse | null = null;

let ui: Record<string, HTMLElement> = {};
let cache: SyncCache | null = null;
let state: PanelState | null = null;
/** 内容库当前选中分类（含特殊页签：最近使用 / 收藏内容 / 开放题素材） */
let currentCategory = '';
/** 内容库搜索关键词 */
let cardSearchKeyword = '';
/** 使用偏好（最近使用 / 收藏 / 站点偏好） */
let usage: UsageState | null = null;
/** 撤回栈：每次填入前记录元素原值 */
const undoStack: Array<{ el: HTMLElement; prevText: string }> = [];

/** 开放题素材类型中文标签 */
const MATERIAL_TYPE_LABELS: Record<string, string> = {
  SELF_EVALUATION: '自我评价', INTERNSHIP: '实习经历', PROJECT: '项目经历',
  AI_COLLABORATION: 'AI 协作经历', CAREER_PLAN: '职业规划', HOBBY: '兴趣特长',
  WHY_COMPANY: '为什么选择本公司', WHY_POSITION: '为什么选择本岗位',
  SUPPLEMENT: '补充信息', AI_TOOL_USAGE: 'AI 工具使用', PROJECT_CHALLENGE: '项目挑战',
  TEAM_COLLABORATION: '团队协作', STRESS_RESISTANCE: '抗压能力', WHY_BANK: '为什么选择银行',
  WHY_STATE_OWNED: '为什么选择国央企', WHY_INTERNET: '为什么选择互联网',
  BEST_PROJECT: '最有成就感的项目', HARDEST_PROBLEM: '最困难的问题',
  INTERNSHIP_GAINS: '实习收获', TECH_INTEREST: '技术兴趣', PERSONAL_STRENGTH: '个人优势',
};

/** 站点默认模板/方向预设（域名关键词 → 受众类型 + 岗位方向） */
const SITE_PRESETS: Array<{ match: RegExp; audienceType: string; jobDirection: string }> = [
  { match: /tencent/i, audienceType: 'big_tech', jobDirection: 'ai' },
  { match: /bytedance|toutiao/i, audienceType: 'big_tech', jobDirection: 'backend' },
  { match: /icbc|ccb|abchina|boc|cmbchina|pingan|bank|95559/i, audienceType: 'bank', jobDirection: 'fintech' },
  { match: /guopin|guozhaopin|sasac|state-owned/i, audienceType: 'state_owned', jobDirection: 'backend' },
  { match: /zhaopin|51job|liepin/i, audienceType: 'general_backend', jobDirection: 'backend' },
];

const SPECIAL_TABS = ['最近使用', '收藏内容', '开放题素材'];

// ==================== 对外入口 ====================

export function panelExists(): boolean {
  return !!document.getElementById(HOST_ID);
}

export async function getPanelStatus(): Promise<{ visible: boolean; minimized: boolean }> {
  const st = await getPanelState();
  return { visible: panelExists() && st.visible, minimized: panelExists() && st.minimized };
}

/** 防止图标点击与刷新自动恢复并发创建宿主 */
let openPromise: Promise<void> | null = null;

/** 打开面板（点击插件图标 / 刷新页面恢复时调用） */
export function openPanel(): Promise<void> {
  if (!openPromise) {
    openPromise = (async () => {
      await ensureHost();
      state = await getPanelState();
      await savePanelState({ visible: true });
      await render();
    })().finally(() => {
      openPromise = null;
    });
  }
  return openPromise;
}

/** 关闭面板（仅用户主动点击关闭按钮；关闭后不自动弹出） */
export async function closePanel(): Promise<void> {
  await savePanelState({ visible: false });
  if (host) {
    host.remove();
    host = null;
    shadow = null;
  }
}

/** 图标点击切换：未显示则打开；已显示则切换最小化 */
export async function togglePanel(): Promise<void> {
  const st = await getPanelState();
  if (!panelExists() || !st.visible) {
    await openPanel();
    return;
  }
  await applyMinimized(!st.minimized);
}

// ==================== 宿主与样式 ====================

async function ensureHost(): Promise<void> {
  if (panelExists()) return;
  host = document.createElement('div');
  host.id = HOST_ID;
  host.style.cssText = `all: initial; position: fixed; z-index: ${Z_INDEX};`;
  shadow = host.attachShadow({ mode: 'closed' });

  const style = document.createElement('style');
  style.textContent = PANEL_CSS;
  shadow.appendChild(style);

  // 面板内事件不冒泡到招聘页面
  for (const type of ['pointerdown', 'pointerup', 'pointermove', 'mousedown', 'mouseup', 'click',
    'dblclick', 'keydown', 'keyup', 'keypress', 'input', 'change', 'focusin', 'focusout',
    'wheel', 'touchstart', 'touchmove', 'touchend', 'contextmenu', 'selectionchange']) {
    host.addEventListener(type, (e) => e.stopPropagation(), true);
  }

  (document.body || document.documentElement).appendChild(host);
  trackExternalFocus();
}

/** 记录页面上最后聚焦的可填写元素（忽略面板自身），并刷新“已选中输入框”信息条 */
function trackExternalFocus(): void {
  const flag = window as unknown as { __rfFocusTracked?: boolean };
  if (flag.__rfFocusTracked) return;
  flag.__rfFocusTracked = true;
  document.addEventListener('focusin', (e) => {
    const target = e.target as HTMLElement | null;
    if (!target || !host || host.contains(target)) return;
    if (isPageFillable(target)) {
      lastExternalFocus = target;
      renderTargetInfo();
    }
  }, true);
}

function isPageFillable(el: HTMLElement): boolean {
  const tag = el.tagName.toLowerCase();
  if (tag === 'input') {
    const type = ((el as HTMLInputElement).type || 'text').toLowerCase();
    return !['hidden', 'submit', 'button', 'file', 'image', 'reset', 'checkbox', 'radio'].includes(type);
  }
  if (tag === 'textarea' || tag === 'select') return true;
  if (el.getAttribute('contenteditable') === 'true' || el.getAttribute('contenteditable') === '') return true;
  return el.classList.contains('ql-editor') || el.classList.contains('w-e-text') || el.classList.contains('ProseMirror');
}

// ==================== 渲染 ====================

async function render(): Promise<void> {
  if (!shadow) return;
  // 清空旧内容（保留 style）
  shadow.querySelectorAll('.rf-panel, .rf-mini').forEach((n) => n.remove());
  state = state || await getPanelState();
  cache = await getSyncCache();
  usage = await loadUsageFromBackend();
  const auth = await getAuth();

  buildPanel();
  buildMiniButton();
  applyPosition();
  await applyMinimized(!!state.minimized);

  if (!auth) {
    setToast('尚未登录：请点击插件弹窗登录后再使用', 'error');
    return;
  }
  ui['user-text'].textContent = `用户：${auth.username}`;
  await applySitePreference();
  await refreshSelections();
  renderTargetInfo();
  renderCards();
  await checkSync(false);
}

function buildPanel(): void {
  if (!shadow) return;
  const panel = el('div', 'rf-panel');
  ui = {};

  // 头部（拖动区）
  const header = el('div', 'rf-header');
  header.innerHTML = `<span class="rf-title">ResumeFlow 填表助手</span>`;
  const headerBtns = el('div', 'rf-header-btns');
  const btnWide = el('button', 'rf-icon-btn', '⤢');
  btnWide.title = state?.wideMode ? '切换小窗模式（360px）' : '展开编辑（宽屏 640px）';
  const btnMin = el('button', 'rf-icon-btn', '—');
  btnMin.title = '最小化';
  const btnClose = el('button', 'rf-icon-btn', '×');
  btnClose.title = '关闭面板';
  headerBtns.append(btnWide, btnMin, btnClose);
  header.appendChild(headerBtns);
  panel.appendChild(header);

  const body = el('div', 'rf-body');

  // 用户与同步状态
  const syncRow = el('div', 'rf-row');
  const userText = el('span', 'rf-user', '用户：-');
  const syncBadge = el('span', 'rf-badge rf-badge-idle', '同步状态检测中');
  syncRow.append(userText, syncBadge);
  body.appendChild(syncRow);

  // 模板 / 方向 / 优先经历
  const tplSel = selectEl('rf-select', []);
  const dirSel = selectEl('rf-select', [
    ['', '岗位方向：自动'], ['backend', '后端开发'], ['ai', 'AI 应用工程化'], ['fintech', '金融科技'],
  ]);
  const expSel = selectEl('rf-select', [['', '优先实习经历：自动']]);
  body.append(labeled('当前模板', tplSel), labeled('岗位方向', dirSel), labeled('优先经历', expSel));

  // 主操作
  const btnFill = el('button', 'rf-btn rf-btn-primary', '一键填写当前页面');
  const fillRow = el('div', 'rf-row');
  const btnFillCurrent = el('button', 'rf-btn', '填写当前输入框');
  const btnUndo = el('button', 'rf-btn', '↩ 撤回上次填充');
  fillRow.append(btnFillCurrent, btnUndo);
  body.append(btnFill, fillRow);

  // 快捷填入
  const quickTitle = el('div', 'rf-subtitle', '快捷填入（填入最后点击的输入框）');
  const quickGrid = el('div', 'rf-quick-grid');
  const quickDefs: Array<[string, string]> = [
    ['skill', '专业技能'], ['self', '自我评价'], ['intern', '实习经历'],
    ['project', '项目经历'], ['ai', 'AI 协作经历'],
  ];
  for (const [key, label] of quickDefs) {
    const b = el('button', 'rf-btn rf-btn-small', label);
    b.dataset.quick = key;
    quickGrid.appendChild(b);
  }
  body.append(quickTitle, quickGrid);

  // 已选中输入框信息条（先点招聘网站输入框，再点内容卡片填入）
  const targetBar = el('div', 'rf-target-bar', '未选中：请先点击招聘网站中的一个输入框');
  body.appendChild(targetBar);

  // 推荐填入区（根据当前输入框智能推荐最匹配的 3 条内容）
  const recommend = el('div', 'rf-recommend');
  recommend.style.display = 'none';
  body.appendChild(recommend);

  // 内容库：搜索 + 分类标签 + 内容卡片（每条可填入/编辑/复制/启停/删除）+ 新增字段表单
  const contentHeader = el('div', 'rf-content-header');
  contentHeader.appendChild(el('span', 'rf-subtitle', '内容库'));
  const btnNewField = el('button', 'rf-btn rf-btn-tiny', '+ 新增字段');
  contentHeader.appendChild(btnNewField);
  const searchInput = el('input', 'rf-search') as HTMLInputElement;
  searchInput.placeholder = '搜索字段名 / 内容 / 关键词…';
  const tabs = el('div', 'rf-tabs');
  const newForm = el('div', 'rf-new-form');
  newForm.style.display = 'none';
  const cards = el('div', 'rf-cards');
  body.append(contentHeader, searchInput, tabs, newForm, cards);

  // 同步区
  const syncTitle = el('div', 'rf-subtitle', '数据同步');
  const syncInfo = el('div', 'rf-sync-info', '版本：-　上次同步：-');
  const syncBtnRow = el('div', 'rf-row');
  const btnSync = el('button', 'rf-btn rf-btn-small', '手动同步');
  const btnExport = el('button', 'rf-btn rf-btn-small', '导出本地缓存');
  const btnImport = el('button', 'rf-btn rf-btn-small', '导入本地缓存');
  const importInput = el('input', '') as HTMLInputElement;
  importInput.type = 'file';
  importInput.accept = 'application/json';
  importInput.style.display = 'none';
  syncBtnRow.append(btnSync, btnExport, btnImport);
  body.append(syncTitle, syncInfo, syncBtnRow, importInput);

  // 报告区
  const report = el('div', 'rf-report');
  report.style.display = 'none';
  body.appendChild(report);

  // 状态提示与右下角拖拽调节手柄
  const toast = el('div', 'rf-toast');
  toast.style.display = 'none';
  body.appendChild(toast);
  const resizeHandle = el('div', 'rf-resize', '◢');

  panel.appendChild(body);
  panel.appendChild(resizeHandle);
  shadow.appendChild(panel);

  ui['panel'] = panel;
  ui['header'] = header;
  ui['user-text'] = userText;
  ui['sync-badge'] = syncBadge;
  ui['tpl-select'] = tplSel;
  ui['dir-select'] = dirSel;
  ui['exp-select'] = expSel;
  ui['target-bar'] = targetBar;
  ui['recommend'] = recommend;
  ui['tabs'] = tabs;
  ui['cards'] = cards;
  ui['card-search'] = searchInput;
  ui['new-form'] = newForm;
  ui['sync-info'] = syncInfo;
  ui['report'] = report;
  ui['toast'] = toast;

  // 事件
  btnWide.addEventListener('click', () => toggleWideMode());
  btnMin.addEventListener('click', () => applyMinimized(true));
  btnClose.addEventListener('click', () => closePanel());
  btnFill.addEventListener('click', () => oneClickFill());
  btnFillCurrent.addEventListener('click', () => fillCurrentInput());
  btnUndo.addEventListener('click', () => undoLastFill());
  btnSync.addEventListener('click', () => checkSync(true));
  btnExport.addEventListener('click', () => exportLocalCache());
  btnImport.addEventListener('click', () => importInput.click());
  importInput.addEventListener('change', () => importLocalCache(importInput));
  searchInput.addEventListener('input', () => {
    cardSearchKeyword = searchInput.value.trim().toLowerCase();
    renderCards();
  });
  quickGrid.addEventListener('click', (e) => {
    const btn = (e.target as HTMLElement).closest('[data-quick]') as HTMLElement | null;
    if (btn) quickFill(btn.dataset.quick || '');
  });
  tplSel.addEventListener('change', async () => {
    const id = Number(tplSel.value || 0) || null;
    const tpl = (cache?.cachedTemplates || []).find((t: any) => t.id === id);
    await savePanelState({ selectedTemplateId: id, selectedAudienceType: tpl?.audienceType || '' });
    await patchSyncCache({ currentTemplateId: id });
    await persistSitePreference();
    renderCards();
  });
  dirSel.addEventListener('change', async () => {
    await savePanelState({ selectedJobDirection: dirSel.value });
    await persistSitePreference();
  });
  expSel.addEventListener('change', () => savePanelState({
    selectedPriorityExperience: Number(expSel.value || 0) || null,
  }));
  btnNewField.addEventListener('click', () => toggleNewForm());

  enableDrag(panel, header);
  enableResize(panel, resizeHandle);
}

function buildMiniButton(): void {
  if (!shadow) return;
  const mini = el('button', 'rf-mini', 'RF');
  mini.title = '展开 ResumeFlow 面板';
  mini.addEventListener('click', () => applyMinimized(false));
  shadow.appendChild(mini);
  ui['mini'] = mini;
}

/** 最小化切换：最小化时仅保留小按钮 */
async function applyMinimized(minimized: boolean): Promise<void> {
  await savePanelState({ minimized });
  if (!ui['panel'] || !ui['mini']) return;
  ui['panel'].style.display = minimized ? 'none' : '';
  ui['mini'].style.display = minimized ? '' : 'none';
  if (minimized) {
    ui['mini'].style.left = ui['panel'].style.left;
    ui['mini'].style.top = ui['panel'].style.top;
  }
}

function applyPosition(): void {
  const st = state!;
  const width = st.width || (st.wideMode ? 640 : 360);
  const height = st.height || 560;
  const x = st.x < 0 ? Math.max(8, window.innerWidth - width - 24) : Math.min(st.x, Math.max(0, window.innerWidth - 80));
  const y = Math.max(0, Math.min(st.y, window.innerHeight - 60));
  const panel = ui['panel'];
  panel.style.left = `${x}px`;
  panel.style.top = `${y}px`;
  panel.style.width = `${width}px`;
  panel.style.maxHeight = `${Math.min(height, window.innerHeight - 16)}px`;
}

/** 小窗（360px）/ 宽屏编辑（640px）模式切换 */
async function toggleWideMode(): Promise<void> {
  const wide = !state?.wideMode;
  await savePanelState({ wideMode: wide, width: wide ? 640 : 360 });
  state = await getPanelState();
  applyPosition();
  setToast(wide ? '已切换宽屏编辑模式' : '已切换小窗模式', 'info');
}

/** 右下角拖拽调节面板宽高 */
function enableResize(panel: HTMLElement, handle: HTMLElement): void {
  let resizing = false;
  let startX = 0;
  let startY = 0;
  let startW = 0;
  let startH = 0;
  handle.addEventListener('pointerdown', (e: PointerEvent) => {
    resizing = true;
    startX = e.clientX;
    startY = e.clientY;
    startW = panel.offsetWidth;
    startH = panel.offsetHeight;
    handle.setPointerCapture(e.pointerId);
    e.preventDefault();
    e.stopPropagation();
  });
  handle.addEventListener('pointermove', (e: PointerEvent) => {
    if (!resizing) return;
    const width = Math.max(300, Math.min(startW + e.clientX - startX, window.innerWidth - 24));
    const height = Math.max(240, Math.min(startH + e.clientY - startY, window.innerHeight - 24));
    panel.style.width = `${width}px`;
    panel.style.maxHeight = `${height}px`;
  });
  const finish = (e: PointerEvent) => {
    if (!resizing) return;
    resizing = false;
    savePanelState({ width: panel.offsetWidth, height: panel.offsetHeight });
    try { handle.releasePointerCapture(e.pointerId); } catch { /* ignore */ }
  };
  handle.addEventListener('pointerup', finish);
  handle.addEventListener('pointercancel', finish);
}

/** 头部拖动：指针事件实现，松手后保存位置 */
function enableDrag(panel: HTMLElement, handle: HTMLElement): void {
  let startX = 0;
  let startY = 0;
  let originX = 0;
  let originY = 0;
  let dragging = false;
  handle.addEventListener('pointerdown', (e: PointerEvent) => {
    if ((e.target as HTMLElement).closest('.rf-icon-btn')) return;
    dragging = true;
    startX = e.clientX;
    startY = e.clientY;
    originX = panel.offsetLeft;
    originY = panel.offsetTop;
    handle.setPointerCapture(e.pointerId);
    e.preventDefault();
  });
  handle.addEventListener('pointermove', (e: PointerEvent) => {
    if (!dragging) return;
    const x = Math.max(0, Math.min(originX + e.clientX - startX, window.innerWidth - 80));
    const y = Math.max(0, Math.min(originY + e.clientY - startY, window.innerHeight - 40));
    panel.style.left = `${x}px`;
    panel.style.top = `${y}px`;
  });
  const finish = (e: PointerEvent) => {
    if (!dragging) return;
    dragging = false;
    savePanelState({ x: panel.offsetLeft, y: panel.offsetTop });
    try { handle.releasePointerCapture(e.pointerId); } catch { /* ignore */ }
  };
  handle.addEventListener('pointerup', finish);
  handle.addEventListener('pointercancel', finish);
}

// ==================== 数据与同步 ====================

/** 经 background 代理调用后端（避免网页上下文 CORS/混合内容限制） */
async function api<T>(path: string, method = 'GET', body?: any): Promise<T> {
  const resp = await chrome.runtime.sendMessage({ type: MessageType.API_PROXY, path, method, body });
  if (chrome.runtime.lastError) throw new Error(chrome.runtime.lastError.message || '通信失败');
  if (!resp || !resp.ok) throw new Error(resp?.message || `请求失败: ${path}`);
  return resp.data as T;
}

async function refreshSelections(): Promise<void> {
  state = await getPanelState();
  const tplSel = ui['tpl-select'] as HTMLSelectElement;
  const dirSel = ui['dir-select'] as HTMLSelectElement;
  const expSel = ui['exp-select'] as HTMLSelectElement;

  // 模板下拉（缓存优先）
  const templates: any[] = cache?.cachedTemplates || [];
  tplSel.innerHTML = '';
  if (templates.length === 0) {
    tplSel.appendChild(new Option('（暂无模板，请先同步）', ''));
  }
  for (const t of templates) {
    tplSel.appendChild(new Option(t.name, String(t.id)));
  }
  if (state.selectedTemplateId) tplSel.value = String(state.selectedTemplateId);

  dirSel.value = state.selectedJobDirection || '';

  // 优先实习经历下拉
  expSel.innerHTML = '';
  expSel.appendChild(new Option('优先实习经历：自动', ''));
  for (const i of cache?.cachedInternships || []) {
    const label = i.shortName || i.company || `实习#${i.id}`;
    expSel.appendChild(new Option(label, String(i.id)));
  }
  if (state.selectedPriorityExperience) expSel.value = String(state.selectedPriorityExperience);
}

/**
 * 检查同步状态：版本落后或哈希不一致时自动全量同步；
 * 网络不可用时降级使用本地缓存并提示。
 */
async function checkSync(manual: boolean): Promise<void> {
  const badge = ui['sync-badge'];
  const info = ui['sync-info'];
  cache = await getSyncCache();
  renderSyncInfo();
  try {
    const status = await api<{ profileVersion: number; dataHash: string; updatedAt: string }>('/api/sync/status');
    const local = cache;
    const outdated = !local
      || local.currentProfileVersion < status.profileVersion
      || local.currentDataHash !== status.dataHash;
    if (outdated) {
      badge.textContent = '检测到更新，同步中…';
      badge.className = 'rf-badge rf-badge-warn';
      await doFullSync();
      badge.textContent = '已是最新';
      badge.className = 'rf-badge rf-badge-ok';
      if (manual) setToast('同步完成，数据已更新', 'success');
    } else {
      badge.textContent = '已是最新';
      badge.className = 'rf-badge rf-badge-ok';
      if (manual) setToast('数据已是最新', 'success');
    }
  } catch (err: any) {
    badge.textContent = cache ? '离线：使用本地缓存' : '无法连接后端';
    badge.className = 'rf-badge rf-badge-err';
    setToast(cache
      ? '当前使用本地缓存数据，可能不是最新版本'
      : `网络不可用：${err?.message || '请检查后端服务'}`, 'warn');
  }
  renderSyncInfo();
}

async function doFullSync(): Promise<void> {
  const payload = await api<any>('/api/sync/full');
  cache = await saveSyncCache(payload);
  const st = await getPanelState();
  if (!st.selectedTemplateId && payload.templates?.length) {
    const def = payload.templates.find((t: any) => t.isDefault) || payload.templates[0];
    await savePanelState({ selectedTemplateId: def.id, selectedAudienceType: def.audienceType || '' });
  }
  await refreshSelections();
}

function renderSyncInfo(): void {
  if (!ui['sync-info']) return;
  const version = cache?.currentProfileVersion ?? '-';
  const lastSync = cache?.lastSyncTime ? new Date(cache.lastSyncTime).toLocaleString() : '从未';
  ui['sync-info'].textContent = `数据版本：${version}　上次同步：${lastSync}`;
}

// ==================== 填写能力 ====================

function currentTemplate(): any | null {
  const id = Number((ui['tpl-select'] as HTMLSelectElement)?.value || 0);
  return (cache?.cachedTemplates || []).find((t: any) => t.id === id) || null;
}

function currentAudience(): string {
  const tpl = currentTemplate();
  const audience = tpl?.audienceType || '';
  return audience === 'general_backend' || !audience ? 'general' : audience;
}

/** 一键填写当前页面：扫描 → 后端匹配 → 预览确认 → 批量填入（不自动提交任何表单） */
async function oneClickFill(): Promise<void> {
  try {
    setToast('扫描页面字段中…', 'info');
    const fields = scanFields();
    if (fields.length === 0) {
      setToast('未发现可填写字段', 'warn');
      return;
    }
    const st = await getPanelState();
    const tpl = currentTemplate();
    const resp = await api<AutofillMatchResponse>('/api/autofill/match', 'POST', {
      templateId: tpl?.id || null,
      pageUrl: location.href,
      pageTitle: document.title,
      fields,
      audienceType: tpl?.audienceType || undefined,
      jobDirection: st.selectedJobDirection || undefined,
      preferredInternshipId: st.selectedPriorityExperience || undefined,
      fillType: 'auto',
    });
    lastMatchResponse = resp;
    showFillPreview(resp, fields);
  } catch (err: any) {
    setToast(`填写失败：${err?.message || err}`, 'error');
  }
}

interface PreviewRow {
  match: MatchResult;
  fieldInfo: FieldInfo | null;
  checked: boolean;
}

/** 填入预览弹窗：展示字段名/类型/即将填入内容/字数/超字数/敏感/需确认，用户确认后才批量填入 */
function showFillPreview(resp: AutofillMatchResponse, fields: FieldInfo[]): void {
  if (!shadow) return;
  shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
  const matches = resp.matches || [];
  if (matches.length === 0) {
    setToast(`无匹配结果：跳过 ${(resp.skipped || []).length} 项，未匹配 ${(resp.unmatched || []).length} 项`, 'warn');
    return;
  }
  const infoById = new Map(fields.map((f) => [f.fieldId, f]));
  const rows: PreviewRow[] = matches.map((m) => {
    const info = infoById.get(m.fieldId) || null;
    const limit = info?.wordLimit ?? null;
    const overLimit = limit != null && String(m.value || '').length > limit;
    const needConfirm = m.confidence < 0.75;
    return { match: m, fieldInfo: info, checked: !overLimit };
  });

  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog rf-dialog-wide');
  box.appendChild(el('div', 'rf-dialog-title',
    `填充预览：匹配 ${matches.length} 项，跳过 ${(resp.skipped || []).length} 项，未匹配 ${(resp.unmatched || []).length} 项`));
  const list = el('div', 'rf-preview-list');
  for (const row of rows) {
    const m = row.match;
    const label = row.fieldInfo?.label || row.fieldInfo?.questionText || row.fieldInfo?.placeholder || m.fieldId;
    const content = String(m.value || '');
    const limit = row.fieldInfo?.wordLimit ?? null;
    const item = el('div', 'rf-preview-item');
    const chk = document.createElement('input');
    chk.type = 'checkbox';
    chk.checked = row.checked;
    chk.addEventListener('change', () => { row.checked = chk.checked; });
    const meta = el('div', 'rf-preview-meta');
    const tags: string[] = [row.fieldInfo?.type || '', `${content.length} 字`];
    if (limit != null) tags.push(`限 ${limit} 字`);
    if (limit != null && content.length > limit) tags.push('⚠ 超字数');
    if (m.sensitive) tags.push('敏感字段');
    if (m.confidence < 0.75) tags.push('需确认');
    meta.appendChild(el('div', 'rf-preview-title', `${label} ← ${m.matchedFieldName}`));
    meta.appendChild(el('div', 'rf-preview-tags', tags.filter(Boolean).join(' · ')));
    const preview = el('div', 'rf-preview-content', content.slice(0, 60) + (content.length > 60 ? '…' : ''));
    meta.appendChild(preview);
    item.append(chk, meta);
    list.appendChild(item);
  }
  box.appendChild(list);

  const actions = el('div', 'rf-card-actions');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnCancel.addEventListener('click', () => mask.remove());
  const btnConfirm = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '确认填入');
  btnConfirm.addEventListener('click', async () => {
    mask.remove();
    await applyPreviewFills(rows, resp);
  });
  actions.append(btnCancel, btnConfirm);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
}

/** 预览确认后批量填入：逐个定位元素 → 快照原值（可撤回）→ 填入 → 记录最近使用 */
async function applyPreviewFills(rows: PreviewRow[], resp: AutofillMatchResponse): Promise<void> {
  let filled = 0;
  let skipped = 0;
  const details: string[] = [];
  for (const row of rows) {
    if (!row.checked) {
      skipped++;
      details.push(`已取消: ${row.match.fieldId}`);
      continue;
    }
    const target = locateFieldElement(row.match.fieldId);
    if (!target) {
      skipped++;
      details.push(`未找到字段: ${row.match.fieldId}`);
      continue;
    }
    undoStack.push({ el: target, prevText: readElementText(target) });
    const res = setElementValue(target, row.match.value);
    if (res.success) {
      filled++;
      details.push(`已填充: ${row.match.fieldId} -> ${row.match.matchedFieldName}${row.match.sensitive ? '(敏感)' : ''}`);
      recordUsage({
        kind: 'field', refId: findFieldIdByKey(row.match.matchedFieldKey),
        name: row.match.matchedFieldName, content: row.match.value, fillType: 'auto',
      }).then(() => { usage && pushUsageToBackend(); });
    } else {
      skipped++;
      details.push(`填充失败: ${row.match.fieldId}`);
    }
  }
  for (const skip of resp.skipped || []) {
    skipped++;
    details.push(`跳过: ${skip.fieldId} - ${skip.reason}`);
  }
  for (const item of resp.unmatched || []) {
    details.push(`未匹配: ${item.fieldId} - ${item.reason}`);
  }
  const result = {
    filled, skipped, sensitive: (resp.skipped || []).filter((s) => s.sensitive).length,
    needConfirm: 0, unmatched: (resp.unmatched || []).length, details, confirmItems: [] as ConfirmItem[],
  };
  chrome.storage.session?.set?.({
    lastFillReport: {
      time: new Date().toLocaleString(), total: rows.length + (resp.skipped || []).length,
      filled: result.filled, skipped: result.skipped, sensitive: result.sensitive,
      needConfirm: 0, unmatched: result.unmatched, details: result.details,
    },
  });
  renderReport(result, resp);
  usage = await getUsageState();
  renderCards();
  setToast(`已填 ${filled} 项，跳过 ${skipped} 项，未匹配 ${result.unmatched} 项`, 'success');
}

function findFieldIdByKey(fieldKey?: string): number | null {
  if (!fieldKey) return null;
  return (cache?.cachedFields || []).find((f) => f.fieldKey === fieldKey)?.id ?? null;
}

/** 填写当前输入框：对最后聚焦的页面输入框单独匹配并填入 */
async function fillCurrentInput(): Promise<void> {
  const target = lastExternalFocus && document.contains(lastExternalFocus)
    ? lastExternalFocus
    : (isPageFillable(document.activeElement as HTMLElement) ? document.activeElement as HTMLElement : null);
  if (!target) {
    setToast('请先点击网页中的目标输入框', 'warn');
    return;
  }
  try {
    const info = scanElement(target);
    if (!info) throw new Error('无法识别该输入框');
    const st = await getPanelState();
    const tpl = currentTemplate();
    const resp = await api<AutofillMatchResponse>('/api/autofill/match', 'POST', {
      templateId: tpl?.id || null,
      pageUrl: location.href,
      pageTitle: document.title,
      fields: [info],
      audienceType: tpl?.audienceType || undefined,
      jobDirection: st.selectedJobDirection || undefined,
      preferredInternshipId: st.selectedPriorityExperience || undefined,
      fillType: 'manual',
    });
    const best = (resp.matches || []).sort((a, b) => b.confidence - a.confidence)[0];
    if (!best || !best.value) {
      setToast('未匹配到合适内容，请尝试推荐填入或一键填写查看报告', 'warn');
      return;
    }
    await fillValueToTarget(best.value, best.matchedFieldName, {
      kind: 'field', refId: findFieldIdByKey(best.matchedFieldKey), fillType: 'manual',
    });
  } catch (err: any) {
    setToast(`填写失败：${err?.message || err}`, 'error');
  }
}

// ==================== 核心填入：字数限制 / 撤回 / 最近使用 ====================

type FillSource = { kind: 'field' | 'material' | 'temp'; refId?: number | null; fillType?: 'auto' | 'manual' };

/**
 * 把内容填入当前选中输入框（统一入口）：
 * 识别 maxlength 与附近文本字数限制；超限时不直接填入，弹窗确认后自动缩短到限制字数；
 * 填入前快照原值（可撤回）；成功后记录最近使用。
 */
async function fillValueToTarget(value: string, name: string, source: FillSource): Promise<boolean> {
  const target = getCurrentTarget();
  if (!target) {
    setToast('请先点击招聘网站中的一个输入框', 'warn');
    return false;
  }
  const info = scanElement(target);
  const limit = info?.wordLimit ?? maxlengthOf(target) ?? null;
  let final = value;
  if (limit != null && value.length > limit) {
    const ok = await confirmDialog(
      `「${name}」共 ${value.length} 字，超过当前输入框限制 ${limit} 字。是否自动缩短到限制字数后填入？`,
      '自动缩短并填入', '取消，改选短版本',
    );
    if (!ok) return false;
    final = value.slice(0, limit);
  }
  undoStack.push({ el: target, prevText: readElementText(target) });
  if (undoStack.length > 30) undoStack.shift();
  const res = setElementValue(target, final);
  if (res.success) {
    await recordUsage({
      kind: source.kind, refId: source.refId ?? null, name, content: final,
      fillType: source.fillType || 'manual',
    });
    usage = await getUsageState();
    pushUsageToBackend();
    if (SPECIAL_TABS.includes(currentCategory)) renderCards();
    setToast(`已填入：${name}${final.length < value.length ? `（已缩短至 ${final.length} 字）` : ''}`, 'success');
  } else {
    undoStack.pop();
    setToast(`填入失败：${res.reason}`, 'error');
  }
  return res.success;
}

/** 撤回上一次填充：恢复填入前的原值 */
function undoLastFill(): void {
  const item = undoStack.pop();
  if (!item || !document.contains(item.el)) {
    setToast('没有可撤回的填充', 'warn');
    return;
  }
  const res = setElementValue(item.el, item.prevText);
  setToast(res.success ? '已撤回上次填充' : `撤回失败：${res.reason}`, res.success ? 'success' : 'error');
}

/** 通用确认弹窗：返回是否点击了主按钮 */
function confirmDialog(text: string, okLabel: string, cancelLabel: string): Promise<boolean> {
  return new Promise((resolve) => {
    if (!shadow) {
      resolve(false);
      return;
    }
    shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
    const mask = el('div', 'rf-dialog-mask');
    const box = el('div', 'rf-dialog');
    box.appendChild(el('div', 'rf-dialog-title', '提示'));
    box.appendChild(el('div', 'rf-dialog-text', text));
    const actions = el('div', 'rf-card-actions');
    const btnCancel = el('button', 'rf-btn rf-btn-tiny', cancelLabel);
    const btnOk = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', okLabel);
    btnCancel.addEventListener('click', () => { mask.remove(); resolve(false); });
    btnOk.addEventListener('click', () => { mask.remove(); resolve(true); });
    actions.append(btnCancel, btnOk);
    box.appendChild(actions);
    mask.appendChild(box);
    shadow.appendChild(mask);
  });
}

// ==================== 推荐填入 ====================

/** 根据当前选中输入框在本地缓存中推荐最匹配的 3 条内容 */
function renderRecommendations(): void {
  const box = ui['recommend'];
  if (!box) return;
  box.innerHTML = '';
  const target = getCurrentTarget();
  if (!target) {
    box.style.display = 'none';
    return;
  }
  const info = scanElement(target);
  if (!info) {
    box.style.display = 'none';
    return;
  }
  const tplId = Number((ui['tpl-select'] as HTMLSelectElement)?.value || 0) || null;
  const recs = recommendFields(info, cache?.cachedFields || [], { templateId: tplId });
  box.style.display = '';
  box.appendChild(el('div', 'rf-subtitle', `推荐填入（识别：${info.label || info.placeholder || info.name || '当前输入框'}${info.wordLimit ? `，限 ${info.wordLimit} 字` : ''}）`));
  if (recs.length === 0) {
    box.appendChild(el('div', 'rf-empty', '未找到合适内容，可手动搜索或新增字段'));
    return;
  }
  for (const rec of recs) {
    box.appendChild(buildRecommendItem(rec));
  }
}

function buildRecommendItem(rec: RecommendItem): HTMLElement {
  const item = el('div', 'rf-rec-item');
  const meta = el('div', 'rf-rec-meta');
  const title = el('div', 'rf-preview-title', rec.field.fieldName);
  const tags: string[] = [`${rec.content.length} 字`];
  if (rec.limit != null) tags.push(`限 ${rec.limit} 字`);
  if (rec.overLimit) tags.push('⚠ 超字数，填入时自动缩短');
  meta.appendChild(title);
  meta.appendChild(el('div', 'rf-preview-tags', tags.join(' · ')));
  const preview = el('div', 'rf-preview-content',
    rec.content.slice(0, 50) + (rec.content.length > 50 ? '…' : ''));
  meta.appendChild(preview);
  const btn = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入推荐内容');
  btn.addEventListener('click', () => fillValueToTarget(rec.content, rec.field.fieldName, {
    kind: 'field', refId: rec.field.id ?? null, fillType: 'manual',
  }));
  item.append(meta, btn);
  return item;
}

// ==================== 站点偏好与使用偏好同步 ====================

/** 打开面板时从后端拉取偏好（失败则用本地，离线可用）；本地非空时保留本地 */
async function loadUsageFromBackend(): Promise<UsageState> {
  const local = await getUsageState();
  try {
    const remote = await api<any>('/api/preferences');
    if (!remote || Object.keys(remote).length === 0) return local;
    const merged: UsageState = {
      recentUsed: (remote.recentUsed || []).length > local.recentUsed.length
        ? remote.recentUsed : local.recentUsed,
      favoriteFieldIds: Array.from(new Set([...(local.favoriteFieldIds || []), ...(remote.favoriteFieldIds || [])])),
      favoriteMaterialIds: Array.from(new Set([...(local.favoriteMaterialIds || []), ...(remote.favoriteMaterialIds || [])])),
      sitePrefs: { ...(remote.sitePrefs || {}), ...(local.sitePrefs || {}) },
    };
    await usageServiceSave(merged);
    return merged;
  } catch {
    return local;
  }
}

function usageServiceSave(state: UsageState): Promise<UsageState> {
  return saveUsageState(state);
}

/** 偏好变更后同步到后端用户偏好表（失败仅提示，不阻断） */
function pushUsageToBackend(): void {
  api('/api/preferences', 'PUT', usage || {}).catch(() => { /* 离线时静默，下次打开重试 */ });
}

/** 按当前域名恢复站点偏好：用户保存过则优先，其次内置预设 */
async function applySitePreference(): Promise<void> {
  const host = location.hostname;
  usage = usage || await getUsageState();
  const saved: SitePref | undefined = usage.sitePrefs?.[host];
  let pref: SitePref | null = saved || null;
  if (!pref) {
    const preset = SITE_PRESETS.find((p) => p.match.test(host));
    if (preset) {
      pref = { audienceType: preset.audienceType, jobDirection: preset.jobDirection };
      await saveSitePref(host, pref);
      usage = await getUsageState();
      pushUsageToBackend();
    }
  }
  if (!pref) return;
  const templates: any[] = cache?.cachedTemplates || [];
  const tpl = templates.find((t) => pref!.audienceType && t.audienceType === pref!.audienceType)
    || (pref.templateId ? templates.find((t) => t.id === pref!.templateId) : null);
  if (!tpl) return;
  await savePanelState({
    selectedTemplateId: tpl.id,
    selectedAudienceType: tpl.audienceType || '',
    selectedJobDirection: pref.jobDirection || state?.selectedJobDirection || '',
  });
  state = await getPanelState();
}

/** 用户切换模板/方向后：记住当前站点偏好 */
async function persistSitePreference(): Promise<void> {
  const tpl = currentTemplate();
  const dirSel = ui['dir-select'] as HTMLSelectElement;
  await saveSitePref(location.hostname, {
    audienceType: tpl?.audienceType || '',
    templateId: tpl?.id ?? null,
    jobDirection: dirSel?.value || '',
  });
  usage = await getUsageState();
  pushUsageToBackend();
}

/** 快捷填入：专业技能 / 自我评价 / 实习经历 / 项目经历 / AI 协作经历 */
async function quickFill(kind: string): Promise<void> {
  const target = lastExternalFocus && document.contains(lastExternalFocus)
    ? lastExternalFocus
    : (isPageFillable(document.activeElement as HTMLElement) ? document.activeElement as HTMLElement : null);
  if (!target) {
    setToast('请先点击网页中的目标输入框', 'warn');
    return;
  }
  cache = cache || await getSyncCache();
  if (!cache) {
    setToast('本地无缓存数据，请先同步', 'warn');
    return;
  }
  const content = kind === 'skill' ? pickSkillContent(target) : pickMaterialContent(kind, target);
  if (!content) {
    setToast('未找到对应内容，请先在管理后台维护或手动同步', 'warn');
    return;
  }
  const ok = setNativeValueForElement(target, content);
  setToast(ok ? '已填入' : '填入失败', ok ? 'success' : 'error');
}

/** 技能内容选择：富文本→完整版；单行 input/select→关键词；其余→简短版；有字数限制时按档位取版本 */
function pickSkillContent(target: HTMLElement): string {
  const audience = currentAudience();
  const tag = target.tagName.toLowerCase();
  const editable = target.getAttribute('contenteditable');
  const rich = editable === 'true' || editable === ''
    || target.classList.contains('ql-editor') || target.classList.contains('w-e-text')
    || target.classList.contains('ProseMirror');
  if (tag === 'input' || tag === 'select') {
    // 关键词形式：优先取当前模板的技能关键词字段，其次取关键词版本内容（限字数）
    const limit = maxlengthOf(target);
    const tplKeywords = currentTemplate()?.skillKeywords || '';
    if (tplKeywords) {
      return limit != null ? tplKeywords.slice(0, limit) : tplKeywords;
    }
    return pickVariantContent('skill', 0, audience, 'skill_keywords', limit) || '';
  }
  const fieldType = rich ? 'skill_full' : 'skill_short';
  return pickVariantContent('skill', 0, audience, fieldType, maxlengthOf(target)) || '';
}

function maxlengthOf(target: HTMLElement): number | null {
  const maxlength = target.getAttribute('maxlength');
  return maxlength && /^\d+$/.test(maxlength) ? Number(maxlength) : null;
}

/** 素材类快捷填入：优先取对应受众的内容版本，缺失回退素材原文 */
function pickMaterialContent(kind: string, target: HTMLElement): string {
  const audience = currentAudience();
  const limit = maxlengthOf(target);

  if (kind === 'intern') {
    const internId = Number(state?.selectedPriorityExperience || 0)
      || (cache?.cachedInternships || [])[0]?.id;
    if (internId) {
      const v = pickVariantContent('internship', internId, audience, 'internship_combined', limit);
      if (v) return v;
    }
    const first: any = (cache?.cachedInternships || [])[0];
    return first?.description || first?.responsibilities || '';
  }
  if (kind === 'project') {
    const first: any = (cache?.cachedProjects || [])[0];
    if (first?.id) {
      const v = pickVariantContent('project', first.id, audience, 'project_combined', limit);
      if (v) return v;
    }
    return first?.projectIntro || first?.responsibilities || '';
  }

  const typeMap: Record<string, string> = {
    self: 'SELF_EVALUATION',
    ai: 'AI_COLLABORATION',
  };
  const materialType = typeMap[kind];
  const material: any = (cache?.cachedMaterials || []).find((m: any) => m.materialType === materialType);
  if (!material) return '';
  return pickVariantContent('material', material.id, audience, 'combined', limit) || material.content || '';
}

/** 从缓存内容版本中按 受众 → 字数（不越档） 挑选 */
function pickVariantContent(sourceType: string, sourceId: number, audience: string,
                            fieldType: string, limit: number | null): string {
  const variants = (cache?.cachedContentVariants || []).filter((v: any) =>
    v.sourceType === sourceType && Number(v.sourceId) === Number(sourceId)
    && v.fieldType === fieldType && v.enabled !== false);
  const LENGTH_ORDER = ['within_100', 'within_200', 'within_300', 'within_500', 'within_1000', 'full'];
  const cap = limit == null ? Number.MAX_SAFE_INTEGER : limit;
  const candidates = variants
    .filter((v: any) => (v.lengthType === 'full' ? cap >= 1000 : lengthLimit(v.lengthType) <= cap))
    .filter((v: any) => v.audienceType === audience || v.audienceType === 'general')
    .sort((a: any, b: any) => {
      const audScore = (x: any) => (x.audienceType === audience ? 1 : 0);
      const lenScore = (x: any) => LENGTH_ORDER.indexOf(x.lengthType);
      return audScore(b) - audScore(a) || lenScore(b) - lenScore(a);
    });
  const best = candidates[0];
  if (!best) return '';
  return limit != null && String(best.content || '').length > limit
    ? String(best.content).slice(0, limit)
    : (best.content || '');
}

function lengthLimit(lengthType: string): number {
  const map: Record<string, number> = {
    within_100: 100, within_200: 200, within_300: 300, within_500: 500, within_1000: 1000, full: 100000,
  };
  return map[lengthType] ?? 1000;
}

// ==================== 报告 ====================

function renderReport(result: ReturnType<typeof applyMatches>, resp: AutofillMatchResponse): void {
  const report = ui['report'];
  report.style.display = '';
  report.innerHTML = '';
  report.appendChild(el('div', 'rf-subtitle',
    `填写报告：已填 ${result.filled}，跳过 ${result.skipped}（敏感 ${result.sensitive}），待确认 ${result.needConfirm}，未匹配 ${result.unmatched}`));

  if (result.confirmItems.length > 0) {
    report.appendChild(el('div', 'rf-report-label', '建议人工确认（置信度 0.5~0.75）：'));
    for (const item of result.confirmItems) {
      const row = el('div', 'rf-report-row');
      row.appendChild(el('span', 'rf-report-text', `${item.matchedFieldName}（${item.confidence.toFixed(2)}）`));
      const btn = el('button', 'rf-btn rf-btn-tiny', '填入');
      btn.addEventListener('click', () => {
        const ok = fillConfirmItem(item);
        setToast(ok ? `已填入 ${item.matchedFieldName}` : '填入失败', ok ? 'success' : 'error');
      });
      row.appendChild(btn);
      report.appendChild(row);
    }
  }

  const unmatched = resp.unmatched || [];
  if (unmatched.length > 0) {
    report.appendChild(el('div', 'rf-report-label', '未匹配字段（可手动绑定）：'));
    const fields: CustomFieldItem[] = cache?.cachedFields || [];
    for (const item of unmatched.slice(0, 10)) {
      const row = el('div', 'rf-report-row');
      row.appendChild(el('span', 'rf-report-text', item.reason || item.fieldId));
      const sel = selectEl('rf-select rf-select-tiny', []);
      sel.appendChild(new Option('选择简历字段…', ''));
      for (const f of fields) {
        if (f.enabled === false) continue;
        sel.appendChild(new Option(f.fieldName, String(f.id)));
      }
      const btn = el('button', 'rf-btn rf-btn-tiny', '填入');
      btn.addEventListener('click', () => {
        const f = fields.find((x) => String(x.id) === sel.value);
        if (!f?.fieldValue) {
          setToast('请先选择有内容的简历字段', 'warn');
          return;
        }
        const ok = fillConfirmItem({
          fieldId: item.fieldId, matchedFieldName: f.fieldName, confidence: 1, value: f.fieldValue,
        } as ConfirmItem);
        setToast(ok ? `已填入 ${f.fieldName}` : '填入失败', ok ? 'success' : 'error');
      });
      row.append(sel, btn);
      report.appendChild(row);
    }
    if (unmatched.length > 10) {
      report.appendChild(el('div', 'rf-report-text', `… 其余 ${unmatched.length - 10} 项未展示`));
    }
  }

  const detailBox = el('details', 'rf-details');
  const summary = document.createElement('summary');
  summary.textContent = '详细日志';
  detailBox.appendChild(summary);
  const pre = el('div', 'rf-details-body', result.details.slice(0, 60).join('\n'));
  detailBox.appendChild(pre);
  report.appendChild(detailBox);
}

// ==================== 已选中输入框 ====================

/** 当前目标输入框：最后聚焦的页面元素，回退为当前 activeElement */
function getCurrentTarget(): HTMLElement | null {
  if (lastExternalFocus && document.contains(lastExternalFocus)) return lastExternalFocus;
  const active = document.activeElement as HTMLElement | null;
  return active && isPageFillable(active) ? active : null;
}

/** 刷新“已选中输入框”信息条：标签 / placeholder / name / id / maxlength / 类型，并刷新推荐区 */
function renderTargetInfo(): void {
  const bar = ui['target-bar'];
  if (!bar) return;
  const target = getCurrentTarget();
  if (!target) {
    bar.textContent = '未选中：请先点击招聘网站中的一个输入框';
    bar.className = 'rf-target-bar';
    if (ui['recommend']) ui['recommend'].style.display = 'none';
    return;
  }
  const info = scanElement(target);
  const label = info?.label || info?.questionText || info?.ariaLabel || info?.placeholder || '(无标签)';
  const parts: string[] = [];
  if (info?.placeholder) parts.push(`placeholder: ${info.placeholder}`);
  if (info?.name) parts.push(`name: ${info.name}`);
  if (info?.id) parts.push(`id: ${info.id}`);
  if (info?.wordLimit) parts.push(`限 ${info.wordLimit} 字`);
  bar.innerHTML = '';
  bar.appendChild(el('div', 'rf-target-title', `已选中输入框：${label}`));
  bar.appendChild(el('div', 'rf-target-detail',
    `${info?.type || target.tagName.toLowerCase()}${parts.length ? ' · ' + parts.join(' · ') : ''}`));
  bar.className = 'rf-target-bar rf-target-bar-active';
  renderRecommendations();
}

// ==================== 插件字段接口（经 background 代理，避免网页 CORS/混合内容限制） ====================

type PluginMethod = 'POST' | 'PUT' | 'PATCH' | 'DELETE';

async function pluginApi(path: string, method: PluginMethod, body?: any): Promise<PluginFieldWriteResult> {
  return unwrapOrConflict(await api<any>(path, method, body));
}

const pluginCreateField = (data: CustomFieldItem) => pluginApi('/api/plugin/fields', 'POST', data);
const pluginUpdateField = (id: number, data: CustomFieldItem) => pluginApi(`/api/plugin/fields/${id}`, 'PUT', data);
const pluginDeleteField = (id: number) => pluginApi(`/api/plugin/fields/${id}`, 'DELETE');
const pluginToggleField = (id: number) => pluginApi(`/api/plugin/fields/${id}/toggle`, 'PATCH');

/** 写操作成功后：本地缓存对齐（版本号/哈希/字段列表）并刷新卡片 */
async function applyWriteResult(res: PluginFieldWriteResult, removedId?: number): Promise<void> {
  const fields: CustomFieldItem[] = cache?.cachedFields || [];
  if (removedId != null) {
    cache!.cachedFields = fields.filter((f) => f.id !== removedId);
  } else if (res.field?.id != null) {
    const idx = fields.findIndex((f) => f.id === res.field!.id);
    if (idx >= 0) fields[idx] = res.field;
    else fields.push(res.field);
    cache!.cachedFields = fields;
  }
  await patchSyncCache({
    currentProfileVersion: res.profileVersion,
    currentDataHash: res.dataHash,
    cachedFields: cache!.cachedFields,
  });
  renderSyncInfo();
  renderCards();
}

// ==================== 内容库：分类卡片 ====================

/** 渲染分类标签（含 最近使用/收藏内容/开放题素材 页签）+ 当前页签内容卡片 */
function renderCards(): void {
  const tabs = ui['tabs'];
  const cards = ui['cards'];
  if (!tabs || !cards) return;
  const fields: CustomFieldItem[] = cache?.cachedFields || [];
  const cats = Array.from(new Set(fields.map((f) => f.fieldCategory || '其他')));
  const allTabs = [...SPECIAL_TABS, ...cats];
  if (!currentCategory || !allTabs.includes(currentCategory)) {
    currentCategory = allTabs[0] || '';
  }
  tabs.innerHTML = '';
  for (const c of allTabs) {
    const tab = el('button', 'rf-tab' + (c === currentCategory ? ' rf-tab-active' : ''), c);
    tab.addEventListener('click', () => {
      currentCategory = c;
      renderCards();
    });
    tabs.appendChild(tab);
  }
  cards.innerHTML = '';

  if (currentCategory === '最近使用') {
    renderRecentCards(cards);
    return;
  }
  if (currentCategory === '收藏内容') {
    renderFavoriteCards(cards);
    return;
  }
  if (currentCategory === '开放题素材') {
    renderMaterialCards(cards);
    return;
  }

  const matchesSearch = (f: CustomFieldItem) => !cardSearchKeyword
    || (f.fieldName || '').toLowerCase().includes(cardSearchKeyword)
    || (f.fieldValue || '').toLowerCase().includes(cardSearchKeyword)
    || (f.matchKeywords || []).some((k) => k.toLowerCase().includes(cardSearchKeyword));
  const list = fields
    .filter((f) => (f.fieldCategory || '其他') === currentCategory && matchesSearch(f))
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  if (list.length === 0) {
    cards.appendChild(el('div', 'rf-empty', cardSearchKeyword ? '无匹配搜索结果' : '该分类暂无内容，可点击右上角“+ 新增字段”添加'));
    return;
  }
  for (const f of list) {
    cards.appendChild(buildCard(f));
  }
}

/** 最近使用卡片（最多 10 条，可再次填入） */
function renderRecentCards(cards: HTMLElement): void {
  const items = (usage?.recentUsed || []).filter((r) =>
    !cardSearchKeyword || r.name.toLowerCase().includes(cardSearchKeyword)
    || r.content.toLowerCase().includes(cardSearchKeyword));
  if (items.length === 0) {
    cards.appendChild(el('div', 'rf-empty', '暂无最近使用记录，填入内容后自动记录'));
    return;
  }
  for (const r of items) {
    const card = el('div', 'rf-card');
    const head = el('div', 'rf-card-head');
    head.appendChild(el('span', 'rf-card-name', r.name));
    head.appendChild(el('span', 'rf-card-key',
      `${r.fillType === 'auto' ? '自动' : '手动'} · ${new Date(r.time).toLocaleString()}`));
    card.appendChild(head);
    const content = el('div', 'rf-card-content', r.content);
    content.title = r.content;
    card.appendChild(content);
    const actions = el('div', 'rf-card-actions');
    const btnFill = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入当前输入框');
    btnFill.addEventListener('click', () => fillValueToTarget(r.content, r.name, {
      kind: r.kind, refId: r.refId, fillType: 'manual',
    }));
    const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
    btnCopy.addEventListener('click', () => copyText(r.content));
    actions.append(btnFill, btnCopy);
    card.appendChild(actions);
    cards.appendChild(card);
  }
}

/** 收藏内容卡片（字段 + 素材） */
function renderFavoriteCards(cards: HTMLElement): void {
  const fields: CustomFieldItem[] = cache?.cachedFields || [];
  const favFields = fields.filter((f) => f.id != null && (usage?.favoriteFieldIds || []).includes(f.id)
    && (!cardSearchKeyword || (f.fieldName || '').toLowerCase().includes(cardSearchKeyword)
      || (f.fieldValue || '').toLowerCase().includes(cardSearchKeyword)));
  const materials: any[] = cache?.cachedMaterials || [];
  const favMaterials = materials.filter((m) => m.id != null && (usage?.favoriteMaterialIds || []).includes(m.id));
  if (favFields.length === 0 && favMaterials.length === 0) {
    cards.appendChild(el('div', 'rf-empty', '暂无收藏内容，点击卡片上的 ☆ 收藏'));
    return;
  }
  for (const f of favFields) {
    cards.appendChild(buildCard(f));
  }
  for (const m of favMaterials) {
    cards.appendChild(buildMaterialCard(m));
  }
}

/** 开放题素材卡片列表：支持搜索 / 收藏 / 手动填入 / 编辑 / 同步管理后台 */
function renderMaterialCards(cards: HTMLElement): void {
  const materials: any[] = (cache?.cachedMaterials || []).filter((m) => m.enabled !== false)
    .filter((m) => !cardSearchKeyword
      || (m.title || '').toLowerCase().includes(cardSearchKeyword)
      || (m.content || '').toLowerCase().includes(cardSearchKeyword)
      || (MATERIAL_TYPE_LABELS[m.materialType] || '').includes(cardSearchKeyword));
  if (materials.length === 0) {
    cards.appendChild(el('div', 'rf-empty', cardSearchKeyword ? '无匹配搜索结果' : '暂无素材，可在管理后台“开放题素材”新增'));
    return;
  }
  for (const m of materials) {
    cards.appendChild(buildMaterialCard(m));
  }
}

/** 单条内容卡片：字段名 / key / 内容 / 关键词 + 填入/临时编辑/编辑/复制/收藏/启停/删除 */
function buildCard(f: CustomFieldItem): HTMLElement {
  const card = el('div', 'rf-card' + (f.enabled === false ? ' rf-card-disabled' : ''));
  const head = el('div', 'rf-card-head');
  head.appendChild(el('span', 'rf-card-name', f.fieldName));
  head.appendChild(el('span', 'rf-card-key', f.fieldKey));
  if (f.enabled === false) head.appendChild(el('span', 'rf-card-tag rf-card-tag-off', '已禁用'));
  if (f.sensitive) head.appendChild(el('span', 'rf-card-tag rf-card-tag-sens', '敏感'));
  const isFav = f.id != null && (usage?.favoriteFieldIds || []).includes(f.id);
  const btnStar = el('button', 'rf-btn rf-btn-tiny rf-btn-star' + (isFav ? ' rf-btn-star-on' : ''), isFav ? '★' : '☆');
  btnStar.title = isFav ? '取消收藏' : '收藏';
  btnStar.addEventListener('click', async () => {
    if (f.id == null) return;
    usage = await toggleFieldFavorite(f.id);
    pushUsageToBackend();
    renderCards();
  });
  head.appendChild(btnStar);
  card.appendChild(head);

  const content = el('div', 'rf-card-content', f.fieldValue || '(空内容)');
  content.title = f.fieldValue || '';
  card.appendChild(content);

  if ((f.matchKeywords || []).length > 0) {
    card.appendChild(el('div', 'rf-card-keywords', `匹配：${f.matchKeywords!.join(' / ')}`));
  }

  const actions = el('div', 'rf-card-actions');
  const btnFill = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入当前输入框');
  btnFill.addEventListener('click', () => fillCardToTarget(f));
  const btnTemp = el('button', 'rf-btn rf-btn-tiny', '临时编辑后填入');
  btnTemp.addEventListener('click', () => tempEditDialog(f));
  const btnEdit = el('button', 'rf-btn rf-btn-tiny', '编辑');
  btnEdit.addEventListener('click', () => openCardEdit(card, f));
  const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
  btnCopy.addEventListener('click', () => copyText(f.fieldValue || ''));
  const btnToggle = el('button', 'rf-btn rf-btn-tiny', f.enabled === false ? '启用' : '禁用');
  btnToggle.addEventListener('click', () => doToggleField(f));
  const btnDelete = el('button', 'rf-btn rf-btn-tiny rf-btn-danger', '删除');
  btnDelete.addEventListener('click', () => doDeleteField(f));
  actions.append(btnFill, btnTemp, btnEdit, btnCopy, btnToggle, btnDelete);
  card.appendChild(actions);
  return card;
}

/** 卡片内容填入当前选中输入框（超字数时弹窗确认后自动缩短） */
function fillCardToTarget(f: CustomFieldItem): void {
  if (f.enabled === false) {
    setToast('该字段已禁用，请先启用', 'warn');
    return;
  }
  fillValueToTarget(f.fieldValue || '', f.fieldName, {
    kind: 'field', refId: f.id ?? null, fillType: 'manual',
  });
}

/**
 * 临时编辑后填入：弹出小编辑框，可临时修改文本后仅填入当前输入框（不保存数据库）；
 * 也可点击“保存为新素材”调用后端接口另存。
 */
function tempEditDialog(f: CustomFieldItem): void {
  if (!shadow) return;
  shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog rf-dialog-wide');
  box.appendChild(el('div', 'rf-dialog-title', `临时编辑：${f.fieldName}（不保存到数据库）`));
  const area = el('textarea', 'rf-edit-area rf-edit-area-tall') as HTMLTextAreaElement;
  area.value = f.fieldValue || '';
  const counter = el('div', 'rf-edit-counter', `${area.value.length} 字`);
  area.addEventListener('input', () => { counter.textContent = `${area.value.length} 字`; });
  box.append(area, counter);
  const actions = el('div', 'rf-card-actions');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnCancel.addEventListener('click', () => mask.remove());
  const btnSaveNew = el('button', 'rf-btn rf-btn-tiny', '保存为新素材');
  btnSaveNew.addEventListener('click', async () => {
    const text = area.value.trim();
    if (!text) {
      setToast('内容不能为空', 'warn');
      return;
    }
    try {
      const res = await pluginCreateField({
        ...f,
        id: undefined,
        version: undefined,
        fieldName: `${f.fieldName}-副本`,
        fieldKey: `${f.fieldKey}_copy_${Date.now() % 100000}`,
        fieldValue: text,
      });
      mask.remove();
      await applyWriteResult(res);
      setToast(`已保存为新素材：${res.field?.fieldName}`, 'success');
    } catch (err: any) {
      setToast(`保存失败：${err?.message || err}`, 'error');
    }
  });
  const btnFill = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入当前输入框');
  btnFill.addEventListener('click', async () => {
    const text = area.value;
    mask.remove();
    await fillValueToTarget(text, f.fieldName, { kind: 'temp', refId: f.id ?? null, fillType: 'manual' });
  });
  actions.append(btnCancel, btnSaveNew, btnFill);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
}

/** 开放题素材卡片：标题/类型/内容 + 填入/编辑/复制/收藏 */
function buildMaterialCard(m: any): HTMLElement {
  const card = el('div', 'rf-card');
  const head = el('div', 'rf-card-head');
  head.appendChild(el('span', 'rf-card-name', m.title || '(无标题)'));
  head.appendChild(el('span', 'rf-card-key', MATERIAL_TYPE_LABELS[m.materialType] || m.materialType || ''));
  const isFav = m.id != null && (usage?.favoriteMaterialIds || []).includes(m.id);
  const btnStar = el('button', 'rf-btn rf-btn-tiny rf-btn-star' + (isFav ? ' rf-btn-star-on' : ''), isFav ? '★' : '☆');
  btnStar.title = isFav ? '取消收藏' : '收藏';
  btnStar.addEventListener('click', async () => {
    if (m.id == null) return;
    usage = await toggleMaterialFavorite(m.id);
    pushUsageToBackend();
    renderCards();
  });
  head.appendChild(btnStar);
  card.appendChild(head);

  const content = el('div', 'rf-card-content', m.content || '(空内容)');
  content.title = m.content || '';
  card.appendChild(content);

  const actions = el('div', 'rf-card-actions');
  const btnFill = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入当前输入框');
  btnFill.addEventListener('click', () => fillValueToTarget(m.content || '', m.title || '素材', {
    kind: 'material', refId: m.id ?? null, fillType: 'manual',
  }));
  const btnEdit = el('button', 'rf-btn rf-btn-tiny', '编辑');
  btnEdit.addEventListener('click', () => materialEditDialog(m));
  const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
  btnCopy.addEventListener('click', () => copyText(m.content || ''));
  actions.append(btnFill, btnEdit, btnCopy);
  card.appendChild(actions);
  return card;
}

/** 素材编辑弹窗：修改后同步到管理后台数据库（后端更新版本号，随后自动同步） */
function materialEditDialog(m: any): void {
  if (!shadow) return;
  shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog rf-dialog-wide');
  box.appendChild(el('div', 'rf-dialog-title', `编辑素材：${m.title || ''}`));
  const titleInput = el('input', 'rf-edit-input') as HTMLInputElement;
  titleInput.value = m.title || '';
  const typeSel = selectEl('rf-select', Object.entries(MATERIAL_TYPE_LABELS));
  typeSel.value = m.materialType || 'SELF_EVALUATION';
  const area = el('textarea', 'rf-edit-area rf-edit-area-tall') as HTMLTextAreaElement;
  area.value = m.content || '';
  box.append(editRow('标题', titleInput), editRow('类型', typeSel), editRow('内容', area));
  const actions = el('div', 'rf-card-actions');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnCancel.addEventListener('click', () => mask.remove());
  const btnSave = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '保存并同步');
  btnSave.addEventListener('click', async () => {
    if (!titleInput.value.trim() || !area.value.trim()) {
      setToast('标题与内容不能为空', 'warn');
      return;
    }
    const warnings = checkContent(area.value, undefined);
    try {
      await api(`/api/materials/${m.id}`, 'PUT', {
        ...m, title: titleInput.value.trim(), materialType: typeSel.value, content: area.value,
      });
      mask.remove();
      const materials: any[] = cache?.cachedMaterials || [];
      const idx = materials.findIndex((x) => x.id === m.id);
      if (idx >= 0) {
        materials[idx] = { ...materials[idx], title: titleInput.value.trim(), materialType: typeSel.value, content: area.value };
        await patchSyncCache({ cachedMaterials: materials });
      }
      renderCards();
      setToast(warnings.length ? `已保存（提醒：${warnings[0]}）` : '已保存素材，同步到管理后台', warnings.length ? 'warn' : 'success');
      // 后端已更新版本号：重新对齐同步状态与缓存
      checkSync(false);
    } catch (err: any) {
      setToast(`保存失败（修改已保留）：${err?.message || err}`, 'error');
    }
  });
  actions.append(btnCancel, btnSave);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    setToast('已复制到剪贴板', 'success');
  } catch {
    setToast('复制失败：浏览器拒绝访问剪贴板', 'error');
  }
}

// ==================== 卡片编辑 ====================

/** 卡片切换为编辑态：可改字段名/内容/关键词/字数档位/是否参与一键填充/敏感 */
function openCardEdit(card: HTMLElement, f: CustomFieldItem): void {
  card.innerHTML = '';
  const nameInput = el('input', 'rf-edit-input') as HTMLInputElement;
  nameInput.value = f.fieldName;
  const contentArea = el('textarea', 'rf-edit-area') as HTMLTextAreaElement;
  contentArea.value = f.fieldValue || '';
  const kwInput = el('input', 'rf-edit-input') as HTMLInputElement;
  kwInput.placeholder = '多个关键词用逗号分隔';
  kwInput.value = (f.matchKeywords || []).join(',');
  const lenSel = selectEl('rf-select', [
    ['', '字数档位：不限'], ['within_200', 'within_200'], ['within_300', 'within_300'],
    ['within_500', 'within_500'], ['within_1000', 'within_1000'], ['full', 'full'],
  ]);
  lenSel.value = f.lengthType || '';
  const autoChk = checkboxEl('参与一键填充', f.autoFillEnabled !== false);
  const manualChk = checkboxEl('允许手动填充', f.manualFillEnabled !== false);
  const sensitiveChk = checkboxEl('敏感字段', !!f.sensitive);

  card.append(
    editRow('字段名称', nameInput),
    editRow('内容正文', contentArea),
    editRow('匹配关键词', kwInput),
    editRow('字数档位', lenSel),
    autoChk.wrap, manualChk.wrap, sensitiveChk.wrap,
  );

  const actions = el('div', 'rf-card-actions');
  const btnSave = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '保存');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnSave.addEventListener('click', () => saveCardEdit(f, {
    fieldName: nameInput.value,
    fieldValue: contentArea.value,
    matchKeywords: kwInput.value,
    lengthType: lenSel.value,
    autoFillEnabled: autoChk.box.checked,
    manualFillEnabled: manualChk.box.checked,
    sensitive: sensitiveChk.box.checked,
  }));
  btnCancel.addEventListener('click', () => renderCards());
  actions.append(btnSave, btnCancel);
  card.appendChild(actions);
}

async function saveCardEdit(origin: CustomFieldItem, form: {
  fieldName: string; fieldValue: string; matchKeywords: string;
  lengthType: string; autoFillEnabled: boolean; manualFillEnabled: boolean; sensitive: boolean;
}, forceVersion?: number): Promise<void> {
  if (!form.fieldName.trim()) {
    setToast('字段名称不能为空', 'warn');
    return;
  }
  if (!form.fieldValue.trim()) {
    setToast('内容正文不能为空', 'warn');
    return;
  }
  const warnings = checkContent(form.fieldValue, form.lengthType || undefined);
  const payload: CustomFieldItem = {
    ...origin,
    fieldName: form.fieldName.trim(),
    fieldValue: form.fieldValue,
    matchKeywords: form.matchKeywords.split(/[,，]/).map((s) => s.trim()).filter(Boolean),
    lengthType: form.lengthType || undefined,
    autoFillEnabled: form.autoFillEnabled,
    manualFillEnabled: form.manualFillEnabled,
    sensitive: form.sensitive,
    version: forceVersion != null ? forceVersion : origin.version,
  };
  try {
    const res = await pluginUpdateField(origin.id!, payload);
    await applyWriteResult(res);
    const allWarnings = [...warnings, ...(res.warnings || [])];
    setToast(allWarnings.length
      ? `已保存：${payload.fieldName}（提醒：${allWarnings[0]}）`
      : `已保存：${payload.fieldName}（同步到管理后台）`,
      allWarnings.length ? 'warn' : 'success');
  } catch (err: any) {
    if (err instanceof ConflictError) {
      showConflictDialog(err.serverField, form, origin);
      return;
    }
    setToast(`保存失败（修改已保留）：${err?.message || err}`, 'error');
  }
}

/** 409 冲突对话框：拉取最新 / 覆盖保存 */
function showConflictDialog(serverField: CustomFieldItem, form: any, origin: CustomFieldItem): void {
  if (!shadow) return;
  shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog');
  box.appendChild(el('div', 'rf-dialog-title', '内容冲突'));
  box.appendChild(el('div', 'rf-dialog-text',
    '该内容已在网页端更新，是否覆盖服务端内容，或拉取最新内容？'));
  const actions = el('div', 'rf-card-actions');
  const btnPull = el('button', 'rf-btn rf-btn-tiny', '拉取最新');
  btnPull.addEventListener('click', async () => {
    mask.remove();
    const fields: CustomFieldItem[] = cache?.cachedFields || [];
    const idx = fields.findIndex((x) => x.id === serverField.id);
    if (idx >= 0) fields[idx] = serverField;
    await patchSyncCache({ cachedFields: fields });
    renderCards();
    setToast('已拉取服务端最新内容', 'success');
  });
  const btnOverwrite = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '覆盖保存');
  btnOverwrite.addEventListener('click', async () => {
    mask.remove();
    await saveCardEdit(origin, form, serverField.version);
  });
  actions.append(btnPull, btnOverwrite);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
}

// ==================== 新增字段 ====================

const CATEGORY_OPTIONS: Array<[string, string]> = [
  ['basic', '基础信息'], ['education', '教育经历'], ['skill', '专业技能'],
  ['internship', '实习经历'], ['project', '项目经历'], ['self_evaluation', '自我评价'],
  ['ai_collaboration', 'AI 协作经历'], ['award', '获奖经历'], ['interest', '兴趣特长'],
  ['open_question', '开放题素材'], ['custom', '自定义字段'],
];

function toggleNewForm(): void {
  const form = ui['new-form'];
  if (!form) return;
  if (form.style.display === 'none') {
    buildNewForm();
    form.style.display = '';
  } else {
    form.style.display = 'none';
  }
}

function buildNewForm(): void {
  const form = ui['new-form'];
  form.innerHTML = '';
  const nameInput = el('input', 'rf-edit-input') as HTMLInputElement;
  nameInput.placeholder = '字段名称，如：到岗时间';
  const keyInput = el('input', 'rf-edit-input') as HTMLInputElement;
  keyInput.placeholder = '字段 key（可留空自动生成），如：arrival_date';
  const catSel = selectEl('rf-select', CATEGORY_OPTIONS);
  const contentArea = el('textarea', 'rf-edit-area') as HTMLTextAreaElement;
  contentArea.placeholder = '内容正文';
  const kwInput = el('input', 'rf-edit-input') as HTMLInputElement;
  kwInput.placeholder = '匹配关键词，逗号分隔';
  const autoChk = checkboxEl('参与一键填充', true);
  const manualChk = checkboxEl('允许手动填充', true);
  const sensitiveChk = checkboxEl('敏感字段', false);
  const orderInput = el('input', 'rf-edit-input') as HTMLInputElement;
  orderInput.type = 'number';
  orderInput.value = '100';

  form.append(
    el('div', 'rf-subtitle', '新增字段'),
    editRow('字段名称', nameInput),
    editRow('字段 key', keyInput),
    editRow('分类', catSel),
    editRow('内容正文', contentArea),
    editRow('匹配关键词', kwInput),
    autoChk.wrap, manualChk.wrap, sensitiveChk.wrap,
    editRow('排序', orderInput),
  );
  const actions = el('div', 'rf-card-actions');
  const btnSave = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '保存并同步');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnSave.addEventListener('click', async () => {
    if (!nameInput.value.trim() || !contentArea.value.trim()) {
      setToast('字段名称与内容不能为空', 'warn');
      return;
    }
    const warnings = checkContent(contentArea.value, undefined);
    const payload: CustomFieldItem = {
      fieldName: nameInput.value.trim(),
      fieldKey: keyInput.value.trim() || `custom_${Date.now()}`,
      fieldType: 'textarea',
      fieldCategory: catSel.value,
      fieldValue: contentArea.value,
      matchKeywords: kwInput.value.split(/[,，]/).map((s) => s.trim()).filter(Boolean),
      autoFillEnabled: autoChk.box.checked,
      manualFillEnabled: manualChk.box.checked,
      sensitive: sensitiveChk.box.checked,
      sortOrder: Number(orderInput.value || 100),
    };
    try {
      const res = await pluginCreateField(payload);
      await applyWriteResult(res);
      currentCategory = res.field?.fieldCategory || currentCategory;
      renderCards();
      ui['new-form'].style.display = 'none';
      const allWarnings = [...warnings, ...(res.warnings || [])];
      setToast(allWarnings.length
        ? `已新增字段：${payload.fieldName}（提醒：${allWarnings[0]}）`
        : `已新增字段：${payload.fieldName}（同步到管理后台）`,
        allWarnings.length ? 'warn' : 'success');
    } catch (err: any) {
      setToast(`新增失败（内容已保留）：${err?.message || err}`, 'error');
    }
  });
  btnCancel.addEventListener('click', () => {
    form.style.display = 'none';
  });
  actions.append(btnSave, btnCancel);
  form.appendChild(actions);
}

// ==================== 删除 / 启停 ====================

async function doDeleteField(f: CustomFieldItem): Promise<void> {
  if (!confirm(`确定删除字段「${f.fieldName}」？（逻辑删除，可在管理后台数据库恢复）`)) return;
  try {
    const res = await pluginDeleteField(f.id!);
    await applyWriteResult(res, f.id);
    setToast(`已删除：${f.fieldName}`, 'success');
  } catch (err: any) {
    setToast(`删除失败：${err?.message || err}`, 'error');
  }
}

async function doToggleField(f: CustomFieldItem): Promise<void> {
  try {
    const res = await pluginToggleField(f.id!);
    await applyWriteResult(res);
    setToast(res.field?.enabled ? `已启用：${f.fieldName}` : `已禁用：${f.fieldName}`, 'success');
  } catch (err: any) {
    setToast(`操作失败：${err?.message || err}`, 'error');
  }
}

// ==================== 本地缓存导入导出 ====================

/** 导出本地缓存（同步缓存 + 使用偏好）为 JSON 文件 */
async function exportLocalCache(): Promise<void> {
  const syncCache = await getSyncCache();
  const usageState = await getUsageState();
  if (!syncCache) {
    setToast('暂无本地缓存可导出', 'warn');
    return;
  }
  const payload = {
    app: 'ResumeFlow',
    exportedAt: new Date().toISOString(),
    syncCache,
    usage: usageState,
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `resumeflow-cache-${new Date().toISOString().slice(0, 10)}.json`;
  (document.body || document.documentElement).appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 3000);
  setToast('本地缓存已导出', 'success');
}

/** 导入本地缓存 JSON，成功后询问是否立即从云端同步 */
async function importLocalCache(input: HTMLInputElement): Promise<void> {
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  try {
    const data = JSON.parse(await file.text());
    const importedCache = data?.syncCache;
    if (!importedCache || !Array.isArray(importedCache.cachedFields)) {
      throw new Error('文件格式不正确（缺少 syncCache）');
    }
    await restoreSyncCache(importedCache);
    if (data?.usage && typeof data.usage === 'object') {
      usage = await saveUsageState(data.usage);
      pushUsageToBackend();
    }
    cache = await getSyncCache();
    renderCards();
    const ok = await confirmDialog('导入成功，是否立即从云端同步最新数据？', '立即同步', '仅使用本地');
    if (ok) {
      await checkSync(true);
    } else {
      setToast('当前使用本地缓存数据，可能不是最新版本', 'info');
    }
  } catch (err: any) {
    setToast(`导入失败：${err?.message || err}`, 'error');
  }
}

// ==================== 工具 ====================

function setToast(text: string, level: 'info' | 'success' | 'warn' | 'error'): void {
  const toast = ui['toast'];
  if (!toast) return;
  toast.textContent = text;
  toast.className = `rf-toast rf-toast-${level}`;
  toast.style.display = '';
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, cls: string, text?: string): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
}

function labeled(label: string, control: HTMLElement): HTMLElement {
  const row = el('div', 'rf-field-row');
  const span = el('span', 'rf-label', label);
  row.append(span, control);
  return row;
}

function editRow(label: string, control: HTMLElement): HTMLElement {
  const row = el('div', 'rf-edit-row');
  row.appendChild(el('div', 'rf-edit-label', label));
  row.appendChild(control);
  return row;
}

function checkboxEl(label: string, checked: boolean): { wrap: HTMLLabelElement; box: HTMLInputElement } {
  const wrap = el('label', 'rf-check') as HTMLLabelElement;
  const box = document.createElement('input');
  box.type = 'checkbox';
  box.checked = checked;
  wrap.appendChild(box);
  wrap.appendChild(el('span', 'rf-check-text', label));
  return { wrap, box };
}

function selectEl(cls: string, options: Array<[string, string]>): HTMLSelectElement {
  const sel = document.createElement('select');
  sel.className = cls;
  for (const [value, label] of options) {
    sel.appendChild(new Option(label, value));
  }
  return sel;
}

// ==================== 样式（Shadow DOM 内，完全隔离） ====================

const PANEL_CSS = `
.rf-panel {
  position: fixed;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  color: #1f2329;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  font: 13px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  z-index: ${Z_INDEX};
  overflow: hidden;
  box-sizing: border-box;
}
.rf-panel * { box-sizing: border-box; }
.rf-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px;
  background: linear-gradient(90deg, #409eff, #36cfc9);
  color: #fff; cursor: move; user-select: none;
}
.rf-title { font-weight: 600; font-size: 13px; }
.rf-header-btns { display: flex; gap: 6px; }
.rf-icon-btn {
  width: 22px; height: 22px; border: none; border-radius: 4px;
  background: rgba(255,255,255,0.25); color: #fff; cursor: pointer;
  font-size: 13px; line-height: 1; padding: 0;
}
.rf-icon-btn:hover { background: rgba(255,255,255,0.45); }
.rf-body { padding: 10px 12px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.rf-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.rf-user { color: #4e5969; }
.rf-badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; white-space: nowrap; }
.rf-badge-ok { background: #e8ffea; color: #00b42a; }
.rf-badge-warn { background: #fff7e8; color: #ff7d00; }
.rf-badge-err { background: #ffece8; color: #f53f3f; }
.rf-badge-idle { background: #f2f3f5; color: #86909c; }
.rf-field-row { display: flex; align-items: center; gap: 8px; }
.rf-label { flex: 0 0 58px; color: #4e5969; }
.rf-select {
  flex: 1; min-width: 0; height: 28px; padding: 0 6px;
  border: 1px solid #e5e6eb; border-radius: 6px; background: #fff; color: #1f2329;
  font-size: 12px;
}
.rf-select-tiny { flex: 1; height: 24px; font-size: 11px; }
.rf-btn {
  border: 1px solid #e5e6eb; background: #fff; color: #1f2329;
  border-radius: 6px; padding: 7px 10px; cursor: pointer; font-size: 13px;
  transition: all 0.15s;
}
.rf-btn:hover { border-color: #409eff; color: #409eff; }
.rf-btn-primary { background: #409eff; border-color: #409eff; color: #fff; }
.rf-btn-primary:hover { background: #3387e0; color: #fff; }
.rf-btn-small { padding: 5px 8px; font-size: 12px; }
.rf-btn-tiny { padding: 2px 8px; font-size: 11px; flex: 0 0 auto; }
.rf-quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.rf-subtitle { font-weight: 600; color: #4e5969; margin-top: 2px; }
.rf-sync-info { color: #86909c; font-size: 12px; }
.rf-report { border-top: 1px dashed #e5e6eb; padding-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.rf-report-label { font-weight: 600; color: #4e5969; }
.rf-report-row { display: flex; align-items: center; gap: 6px; }
.rf-report-text { flex: 1; min-width: 0; color: #4e5969; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rf-details summary { cursor: pointer; color: #86909c; font-size: 12px; }
.rf-details-body { white-space: pre-wrap; font-size: 11px; color: #86909c; max-height: 160px; overflow-y: auto; }
.rf-toast {
  border-radius: 6px; padding: 6px 10px; font-size: 12px; word-break: break-all;
}
.rf-toast-info { background: #e8f3ff; color: #165dff; }
.rf-toast-success { background: #e8ffea; color: #00b42a; }
.rf-toast-warn { background: #fff7e8; color: #ff7d00; }
.rf-toast-error { background: #ffece8; color: #f53f3f; }
.rf-mini {
  position: fixed; width: 46px; height: 46px; border-radius: 50%;
  border: none; cursor: pointer; z-index: ${Z_INDEX};
  background: linear-gradient(135deg, #409eff, #36cfc9); color: #fff;
  font-weight: 700; font-size: 13px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.25);
  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
}
.rf-mini:hover { transform: scale(1.06); }
.rf-target-bar {
  border: 1px dashed #c9cdd4; border-radius: 6px; padding: 6px 8px;
  color: #86909c; font-size: 12px; word-break: break-all;
}
.rf-target-bar-active { border-color: #409eff; background: #f0f7ff; color: #1f2329; }
.rf-target-title { font-weight: 600; }
.rf-target-detail { color: #86909c; margin-top: 2px; }
.rf-content-header { display: flex; align-items: center; justify-content: space-between; }
.rf-tabs { display: flex; flex-wrap: wrap; gap: 4px; }
.rf-tab {
  border: 1px solid #e5e6eb; background: #fff; color: #4e5969;
  border-radius: 12px; padding: 2px 8px; font-size: 11px; cursor: pointer;
}
.rf-tab-active { background: #409eff; border-color: #409eff; color: #fff; }
.rf-cards { display: flex; flex-direction: column; gap: 6px; }
.rf-empty { color: #86909c; font-size: 12px; padding: 8px 0; }
.rf-card {
  border: 1px solid #e5e6eb; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; gap: 4px; background: #fff;
}
.rf-card-disabled { opacity: 0.6; }
.rf-card-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.rf-card-name { font-weight: 600; font-size: 12px; }
.rf-card-key { color: #86909c; font-size: 11px; }
.rf-card-tag { font-size: 10px; padding: 0 6px; border-radius: 8px; }
.rf-card-tag-off { background: #f2f3f5; color: #86909c; }
.rf-card-content {
  font-size: 12px; color: #4e5969; max-height: 72px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical;
  word-break: break-all;
}
.rf-card-keywords { color: #86909c; font-size: 11px; word-break: break-all; }
.rf-card-actions { display: flex; flex-wrap: wrap; gap: 4px; }
.rf-btn-danger { color: #f53f3f; }
.rf-btn-danger:hover { border-color: #f53f3f; color: #f53f3f; }
.rf-new-form {
  border: 1px solid #e5e6eb; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; gap: 6px; background: #fafbfc;
}
.rf-edit-row { display: flex; flex-direction: column; gap: 2px; }
.rf-edit-label { color: #86909c; font-size: 11px; }
.rf-edit-input {
  height: 28px; padding: 0 6px; border: 1px solid #e5e6eb; border-radius: 6px;
  font-size: 12px; background: #fff; color: #1f2329;
}
.rf-edit-area {
  min-height: 72px; padding: 6px; border: 1px solid #e5e6eb; border-radius: 6px;
  font-size: 12px; resize: vertical; background: #fff; color: #1f2329;
  font-family: inherit;
}
.rf-check { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #4e5969; }
.rf-dialog-mask {
  position: fixed; inset: 0; background: rgba(0,0,0,0.35); z-index: ${Z_INDEX};
  display: flex; align-items: center; justify-content: center;
}
.rf-dialog {
  width: 300px; background: #fff; border-radius: 10px; padding: 14px;
  display: flex; flex-direction: column; gap: 8px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.25);
}
.rf-dialog-title { font-weight: 700; }
.rf-dialog-text { color: #4e5969; font-size: 12px; }
.rf-dialog-wide { width: 420px; max-width: calc(100vw - 40px); max-height: calc(100vh - 40px); overflow-y: auto; }
.rf-search {
  height: 28px; padding: 0 8px; border: 1px solid #e5e6eb; border-radius: 6px;
  font-size: 12px; background: #fff; color: #1f2329;
}
.rf-recommend {
  border: 1px solid #bedaff; background: #f0f7ff; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; gap: 6px;
}
.rf-rec-item {
  border: 1px solid #e5e6eb; background: #fff; border-radius: 6px; padding: 6px 8px;
  display: flex; flex-direction: column; gap: 4px;
}
.rf-rec-meta { display: flex; flex-direction: column; gap: 2px; }
.rf-preview-list { display: flex; flex-direction: column; gap: 6px; max-height: 320px; overflow-y: auto; }
.rf-preview-item {
  border: 1px solid #e5e6eb; border-radius: 6px; padding: 6px 8px;
  display: flex; flex-direction: column; gap: 4px; background: #fff;
}
.rf-preview-meta { display: flex; align-items: flex-start; gap: 6px; }
.rf-preview-title { font-weight: 600; font-size: 12px; word-break: break-all; }
.rf-preview-tags { color: #86909c; font-size: 11px; word-break: break-all; }
.rf-preview-content {
  color: #4e5969; font-size: 12px; word-break: break-all;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.rf-edit-area-tall { min-height: 140px; }
.rf-edit-counter { color: #86909c; font-size: 11px; text-align: right; }
.rf-btn-star { color: #c9cdd4; }
.rf-btn-star-on { color: #ff7d00; border-color: #ffd77a; }
.rf-card-tag-sens { background: #fff1f0; color: #f53f3f; }
.rf-resize {
  position: absolute; right: 0; bottom: 0; width: 16px; height: 16px;
  cursor: nwse-resize; color: #c9cdd4; font-size: 10px; line-height: 16px;
  text-align: center; user-select: none;
}
`;
