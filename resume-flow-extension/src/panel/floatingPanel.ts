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
import { getAuth, getBackendUrl } from '../services/storageService';
import { getPanelState, savePanelState, type PanelState } from '../services/panelStateService';
import { getSyncCache, saveSyncCache, patchSyncCache, restoreSyncCache, type SyncCache } from '../services/syncCacheService';
import { scanFields, scanElement, countBlocks } from '../services/fieldScanService';
import { ensureBlocks, type RepeatableBlockType } from '../services/blockService';
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
  type ApplicationCapturePayload, type ApplicationCaptureResult,
  type AutofillMatchResponse, type CustomFieldItem, type FieldInfo, type MatchResult, type PluginFieldWriteResult,
} from '../services/apiClient';
import {
  detectJobPage, installSaveActionWatcher, installUrlWatcher,
  type JobPageDetectResult,
} from '../services/jobPageDetectService';
import {
  buildSearchIndex, unifiedSearch, SEARCH_GROUP_LABELS, SEARCH_SUGGESTIONS,
  type ScoredSearchResult, type SearchEntry, type SearchGroup,
} from '../services/unifiedSearchService';
import { attachResizeHandles } from '../services/panelResizeService';

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
/** 搜索是否仅限当前分类（默认全库搜索，不受当前页签限制） */
let searchTabOnly = false;
/** 统一搜索本地索引：同步/字段变更后按数据版本自动重建，搜索只查本地不请求后端 */
let searchIndex: SearchEntry[] | null = null;
let searchIndexHash = '';
/** 搜索防抖计时器（200ms）与结果分页上限 */
let searchDebounceTimer: number | null = null;
let searchResultLimit = 50;
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

  // 面板内事件不冒泡到招聘页面。
  // 必须用冒泡阶段（第三参数为 false）：捕获阶段拦截会阻断事件进入 Shadow DOM，
  // 导致面板内所有按钮/拖动/输入全部失效。
  for (const type of ['pointerdown', 'pointerup', 'pointermove', 'mousedown', 'mouseup', 'click',
    'dblclick', 'keydown', 'keyup', 'keypress', 'input', 'change', 'focusin', 'focusout',
    'wheel', 'touchstart', 'touchmove', 'touchend', 'contextmenu']) {
    host.addEventListener(type, (e) => e.stopPropagation());
  }

  (document.body || document.documentElement).appendChild(host);
  trackExternalFocus();
  watchAuthChange();
}

/** 监听登录态变化：弹窗登录成功后无需关闭/刷新页面，面板自动重新渲染 */
function watchAuthChange(): void {
  const flag = window as unknown as { __rfAuthWatched?: boolean };
  if (flag.__rfAuthWatched) return;
  flag.__rfAuthWatched = true;
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area !== 'local' || !changes.rf_token || !panelExists()) return;
    render().catch(() => { /* 面板可能已关闭 */ });
  });
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
  watchWindowResize();
  applyDisplaySettings();
  await applyMinimized(!!state.minimized);

  if (!auth) {
    buildLoginForm();
    setToast('尚未登录：请在上方表单填写账号信息登录', 'warn');
    return;
  }
  ui['login-form']?.remove();
  ui['user-text'].textContent = `用户：${auth.username}`;
  await applySitePreference();
  await refreshSelections();
  renderTargetInfo();
  renderCards();
  // 招聘页面采集：安装保存动作/SPA 路由监听，并执行一次检测与自动写入
  installSaveActionWatcher();
  installUrlWatcher(() => {
    if (panelExists()) refreshApplicationTrack().catch(() => { /* 面板可能已关闭 */ });
  });
  await refreshApplicationTrack();
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
  const btnSettings = el('button', 'rf-icon-btn', '⚙');
  btnSettings.title = '显示设置（字号 / 行距 / 显示模式）';
  const btnMax = el('button', 'rf-icon-btn', '□');
  btnMax.title = state?.maximized ? '还原上次尺寸' : '最大化（窗口 90%，保留边距）';
  const btnMin = el('button', 'rf-icon-btn', '—');
  btnMin.title = '最小化';
  const btnClose = el('button', 'rf-icon-btn', '×');
  btnClose.title = '关闭面板（关闭后不自动弹出）';
  headerBtns.append(btnSettings, btnMax, btnMin, btnClose);
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

  // 投递记录区（招聘网站页面自动采集，写入投递信息表）
  const trackHeader = el('div', 'rf-content-header');
  trackHeader.appendChild(el('span', 'rf-subtitle', '投递记录'));
  const btnTrackRefresh = el('button', 'rf-btn rf-btn-tiny', '↻ 重新检测');
  trackHeader.appendChild(btnTrackRefresh);
  const trackBox = el('div', 'rf-track-box', '正在检测当前页面…');
  body.append(trackHeader, trackBox);

  // 内容库：搜索 + 分类标签 + 内容卡片（每条可填入/编辑/复制/启停/删除）+ 新增字段表单
  const contentHeader = el('div', 'rf-content-header');
  contentHeader.appendChild(el('span', 'rf-subtitle', '内容库'));
  const btnNewField = el('button', 'rf-btn rf-btn-tiny', '+ 新增字段');
  contentHeader.appendChild(btnNewField);
  const searchInput = el('input', 'rf-search') as HTMLInputElement;
  searchInput.placeholder = '全库搜索：公司 / 单位 / 职位 / 时间 / 证明人 / 电话 / 项目…';
  searchTabOnly = !!state?.searchTabOnly;
  const scopeChk = checkboxEl('仅搜当前分类', searchTabOnly);
  const searchRow = el('div', 'rf-search-row');
  searchRow.append(searchInput, scopeChk.wrap);
  const tabs = el('div', 'rf-tabs');
  const newForm = el('div', 'rf-new-form');
  newForm.style.display = 'none';
  const cards = el('div', 'rf-cards');
  body.append(contentHeader, searchRow, tabs, newForm, cards);

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

  // 状态提示（缩放由边框手柄承载，见 panelResizeService）
  const toast = el('div', 'rf-toast');
  toast.style.display = 'none';
  body.appendChild(toast);

  panel.appendChild(body);
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
  ui['track-box'] = trackBox;
  ui['tabs'] = tabs;
  ui['cards'] = cards;
  ui['card-search'] = searchInput;
  ui['new-form'] = newForm;
  ui['sync-info'] = syncInfo;
  ui['report'] = report;
  ui['toast'] = toast;

  // 事件
  btnSettings.addEventListener('click', () => openDisplaySettings());
  btnMax.addEventListener('click', () => toggleMaximize());
  btnMin.addEventListener('click', () => applyMinimized(true));
  btnClose.addEventListener('click', () => closePanel());
  btnFill.addEventListener('click', () => oneClickFill());
  btnTrackRefresh.addEventListener('click', () => refreshApplicationTrack());
  btnFillCurrent.addEventListener('click', () => fillCurrentInput());
  btnUndo.addEventListener('click', () => undoLastFill());
  btnSync.addEventListener('click', () => checkSync(true));
  btnExport.addEventListener('click', () => exportLocalCache());
  btnImport.addEventListener('click', () => importInput.click());
  importInput.addEventListener('change', () => importLocalCache(importInput));
  searchInput.addEventListener('input', () => {
    // debounce 200ms：避免每个字符都重建结果；搜索只查本地索引，不请求后端
    if (searchDebounceTimer != null) window.clearTimeout(searchDebounceTimer);
    searchDebounceTimer = window.setTimeout(() => {
      cardSearchKeyword = searchInput.value.trim().toLowerCase();
      searchResultLimit = 50;
      renderCards();
    }, 200);
  });
  scopeChk.box.addEventListener('change', async () => {
    searchTabOnly = scopeChk.box.checked;
    await savePanelState({ searchTabOnly });
    if (cardSearchKeyword) renderCards();
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
  // 边框缩放：右/下/右下角/左/上 手柄；拖动实时更新并自适应布局，松手保存尺寸（刷新后恢复）
  attachResizeHandles(panel, {
    onResize: () => applyLayoutClasses(),
    onCommit: async (rect) => {
      await savePanelState({ x: rect.x, y: rect.y, width: rect.width, height: rect.height, maximized: false });
      state = await getPanelState();
      applyLayoutClasses();
    },
  });
}

function buildMiniButton(): void {
  if (!shadow) return;
  const mini = el('button', 'rf-mini', 'RF');
  mini.title = '展开 ResumeFlow 面板';
  mini.addEventListener('click', () => applyMinimized(false));
  shadow.appendChild(mini);
  ui['mini'] = mini;
}

/** 未登录时在面板内展示登录表单（经 background 代理，避免网页上下文限制） */
function buildLoginForm(): void {
  const body = ui['panel']?.querySelector('.rf-body');
  if (!body || ui['login-form']) return;
  const form = el('div', 'rf-login-form');
  form.appendChild(el('div', 'rf-login-title', '登录 ResumeFlow'));
  const urlInput = el('input', 'rf-edit-input') as HTMLInputElement;
  urlInput.placeholder = '后端地址';
  getBackendUrl().then((u) => { if (!urlInput.value) urlInput.value = u; });
  const userInput = el('input', 'rf-edit-input') as HTMLInputElement;
  userInput.placeholder = '用户名';
  const pwdInput = el('input', 'rf-edit-input') as HTMLInputElement;
  pwdInput.type = 'password';
  pwdInput.placeholder = '密码';
  const btn = el('button', 'rf-btn rf-btn-primary', '登录');
  const msg = el('div', 'rf-login-msg');
  btn.addEventListener('click', async () => {
    const backendUrl = urlInput.value.trim();
    const username = userInput.value.trim();
    const password = pwdInput.value.trim();
    if (!backendUrl || !username || !password) {
      msg.textContent = '请填写完整登录信息';
      msg.className = 'rf-login-msg rf-login-msg-err';
      return;
    }
    btn.disabled = true;
    msg.textContent = '登录中…';
    msg.className = 'rf-login-msg';
    try {
      const resp = await chrome.runtime.sendMessage({ type: MessageType.LOGIN, username, password, backendUrl });
      if (chrome.runtime.lastError) throw new Error(chrome.runtime.lastError.message || '通信失败');
      if (!resp?.ok) throw new Error(resp?.message || '登录失败');
      msg.textContent = '登录成功';
      msg.className = 'rf-login-msg rf-login-msg-ok';
      await render();
    } catch (err: any) {
      msg.textContent = `登录失败：${err?.message || err}`;
      msg.className = 'rf-login-msg rf-login-msg-err';
    } finally {
      btn.disabled = false;
    }
  });
  form.append(editRow('后端地址', urlInput), editRow('用户名', userInput), editRow('密码', pwdInput), btn, msg);
  body.prepend(form);
  ui['login-form'] = form;
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
  // 最小 360x420，最大不超过窗口 90%，面板整体不超出屏幕
  const width = Math.min(Math.max(360, st.width || 380), Math.floor(window.innerWidth * 0.9));
  const height = Math.min(Math.max(420, st.height || 560), Math.floor(window.innerHeight * 0.9));
  const x = st.x < 0 ? Math.max(8, window.innerWidth - width - 24) : Math.min(st.x, Math.max(0, window.innerWidth - 80));
  const y = Math.max(0, Math.min(st.y, window.innerHeight - 60));
  const panel = ui['panel'];
  panel.style.left = `${x}px`;
  panel.style.top = `${y}px`;
  panel.style.width = `${width}px`;
  panel.style.height = `${height}px`;
}

/** 最大化 / 还原：宽高扩大到窗口 90%（居中保留边距），再点一次还原上次尺寸 */
async function toggleMaximize(): Promise<void> {
  const st = state!;
  if (st.maximized && st.preMaxRect) {
    await savePanelState({ maximized: false, ...st.preMaxRect });
  } else {
    const width = Math.floor(window.innerWidth * 0.9);
    const height = Math.floor(window.innerHeight * 0.9);
    await savePanelState({
      maximized: true,
      preMaxRect: { x: st.x, y: st.y, width: st.width, height: st.height },
      x: Math.floor((window.innerWidth - width) / 2), y: 8, width, height,
    });
  }
  state = await getPanelState();
  applyPosition();
  applyDisplaySettings();
  setToast(state.maximized ? '已最大化，再点一次还原上次尺寸' : '已还原上次尺寸', 'info');
}

/** 重置布局：恢复默认位置、默认尺寸、默认字号/行距/显示模式 */
async function resetPanelLayout(): Promise<void> {
  await savePanelState({
    x: -1, y: 96, width: 380, height: 560,
    maximized: false, preMaxRect: null,
    fontSizePreset: 'normal', fontSize: 14, lineHeight: 'normal',
    displayMode: 'detail', scrollbarMode: 'auto',
  });
  state = await getPanelState();
  applyPosition();
  applyDisplaySettings();
  setToast('已重置面板布局与显示设置', 'success');
}

/** 字号预设与行高取值（显示设置用） */
const FONT_PRESETS: Record<string, number> = { small: 12, normal: 14, large: 16, xlarge: 18 };
const LINE_HEIGHTS: Record<string, string> = { compact: '1.3', normal: '1.5', loose: '1.8' };

/**
 * 应用显示设置：字号（12-22）/ 行高 / 显示模式 / 滚动条策略。
 * 通过 CSS 变量作用于 Shadow DOM 内部，不影响招聘网站页面样式；刷新后从 chrome.storage.local 恢复。
 */
function applyDisplaySettings(): void {
  if (!host || !state) return;
  const fs = Math.max(12, Math.min(22, state.fontSize || 14));
  host.style.setProperty('--rf-fs', `${fs}px`);
  host.style.setProperty('--rf-lh', LINE_HEIGHTS[state.lineHeight] || '1.5');
  const panel = ui['panel'];
  if (panel) {
    panel.classList.toggle('rf-mode-simple', state.displayMode === 'simple');
    panel.classList.toggle('rf-mode-debug', state.displayMode === 'debug');
    panel.classList.toggle('rf-scroll-always', state.scrollbarMode === 'always');
  }
  applyLayoutClasses();
}

/** 响应式布局类：<420 窄屏（紧凑/折叠）/ 420-640 标准 / >=640 宽屏（卡片双列）；高度不足时折叠次要区域 */
function applyLayoutClasses(): void {
  const panel = ui['panel'];
  if (!panel) return;
  const w = panel.offsetWidth;
  const h = panel.offsetHeight;
  panel.classList.toggle('rf-narrow', w > 0 && w < 420);
  panel.classList.toggle('rf-medium', w >= 420 && w < 640);
  panel.classList.toggle('rf-wide', w >= 640);
  panel.classList.toggle('rf-short', h > 0 && h < 480);
}

/** 窗口尺寸变化时重新约束位置尺寸并刷新自适应布局 */
function watchWindowResize(): void {
  const flag = window as unknown as { __rfWinResizeWatched?: boolean };
  if (flag.__rfWinResizeWatched) return;
  flag.__rfWinResizeWatched = true;
  window.addEventListener('resize', () => {
    if (!panelExists() || !ui['panel']) return;
    applyPosition();
    applyDisplaySettings();
  });
}

/** 显示设置弹窗：字号（小/标准/大/超大/自定义 12-22）/ 行距 / 面板宽度 / 显示模式 / 滚动条 / 重置布局 */
function openDisplaySettings(): void {
  if (!shadow) return;
  shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
  const st = state!;
  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog rf-dialog-wide');
  box.appendChild(el('div', 'rf-dialog-title', '显示设置（仅作用于插件面板，不影响招聘网站）'));

  const reopen = () => { mask.remove(); openDisplaySettings(); };

  // 字号：预设 + 自定义（12-22）
  const fsRow = el('div', 'rf-settings-row');
  fsRow.appendChild(el('span', 'rf-settings-label', '字号'));
  const presets: Array<[string, string]> = [['small', '小'], ['normal', '标准'], ['large', '大'], ['xlarge', '超大']];
  for (const [key, label] of presets) {
    const b = el('button', 'rf-btn rf-btn-tiny' + (st.fontSizePreset === key ? ' rf-btn-primary' : ''), label);
    b.addEventListener('click', async () => {
      await savePanelState({ fontSizePreset: key as PanelState['fontSizePreset'], fontSize: FONT_PRESETS[key] });
      state = await getPanelState();
      applyDisplaySettings();
      reopen();
    });
    fsRow.appendChild(b);
  }
  const customInput = el('input', 'rf-edit-input rf-settings-custom') as HTMLInputElement;
  customInput.type = 'number';
  customInput.min = '12';
  customInput.max = '22';
  customInput.value = String(st.fontSize || 14);
  customInput.title = '自定义字号（12-22px）';
  customInput.addEventListener('change', async () => {
    const v = Math.max(12, Math.min(22, Number(customInput.value) || 14));
    await savePanelState({ fontSizePreset: 'custom', fontSize: v });
    state = await getPanelState();
    applyDisplaySettings();
  });
  fsRow.appendChild(customInput);
  box.appendChild(fsRow);

  // 按钮组：行距 / 面板宽度 / 内容显示模式 / 横向滚动条
  const mkBtnGroup = (label: string, options: Array<[string, string]>, current: string,
    onPick: (v: string) => void): HTMLElement => {
    const row = el('div', 'rf-settings-row');
    row.appendChild(el('span', 'rf-settings-label', label));
    for (const [value, text] of options) {
      const b = el('button', 'rf-btn rf-btn-tiny' + (value === current ? ' rf-btn-primary' : ''), text);
      b.addEventListener('click', () => { onPick(value); reopen(); });
      row.appendChild(b);
    }
    return row;
  };
  box.appendChild(mkBtnGroup('行距', [['compact', '紧凑'], ['normal', '标准'], ['loose', '宽松']],
    st.lineHeight, async (v) => {
      await savePanelState({ lineHeight: v as PanelState['lineHeight'] });
      state = await getPanelState();
      applyDisplaySettings();
    }));
  box.appendChild(mkBtnGroup('面板宽度', [['380', '紧凑'], ['460', '标准'], ['680', '宽屏']],
    '', async (v) => {
      await savePanelState({ width: Number(v), maximized: false });
      state = await getPanelState();
      applyPosition();
      applyDisplaySettings();
    }));
  box.appendChild(mkBtnGroup('内容显示', [['simple', '简洁'], ['detail', '详细'], ['debug', '调试']],
    st.displayMode, async (v) => {
      await savePanelState({ displayMode: v as PanelState['displayMode'] });
      state = await getPanelState();
      applyDisplaySettings();
    }));
  box.appendChild(mkBtnGroup('横向滚动条', [['auto', '自动'], ['always', '始终显示']],
    st.scrollbarMode, async (v) => {
      await savePanelState({ scrollbarMode: v as PanelState['scrollbarMode'] });
      state = await getPanelState();
      applyDisplaySettings();
    }));

  const actions = el('div', 'rf-card-actions');
  const btnReset = el('button', 'rf-btn rf-btn-tiny', '重置面板布局');
  btnReset.addEventListener('click', async () => { mask.remove(); await resetPanelLayout(); openDisplaySettings(); });
  const btnDone = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '完成');
  btnDone.addEventListener('click', () => mask.remove());
  actions.append(btnReset, btnDone);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
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

/** 置信度分层：高可信 ≥0.85 / 中可信 0.70-0.85 / 低可信 0.50-0.70 / 未匹配 <0.50 */
type ConfidenceTier = 'high' | 'medium' | 'low' | 'unmatched';

interface PreviewRow {
  match: MatchResult;
  fieldInfo: FieldInfo | null;
  checked: boolean;
  /** 置信度分层（高/中/低/未匹配） */
  tier: ConfidenceTier;
  /** 疑似错误原因；非空时默认不勾选并醒目标记 */
  suspiciousReason: string;
  /** 展示状态：高可信 / 中可信 / 低可信 / 未匹配 / 超字数 / 疑似错误 */
  status: string;
}

/** 预览分组顺序与标题（教育/工作/项目/荣誉/家庭子组按记录名展开，前缀匹配同一顺序） */
const PREVIEW_GROUPS = [
  '基础信息', '个人信息', '求职意向', '教育经历', '工作/实习经历', '项目经历',
  '家庭情况', '紧急联系人', '语言能力', '荣誉奖项', '专利成果', '专业技能', '科研经历', '校园经历',
  '开放题', '未匹配字段', '低置信度字段',
];

/** 分组排序：子组（如“教育经历 · 北京理工大学”）跟随所属主组 */
function groupOrder(key: string): number {
  const idx = PREVIEW_GROUPS.findIndex((g) => key === g || key.startsWith(g + ' '));
  return idx === -1 ? PREVIEW_GROUPS.length : idx;
}

/** 一键填写当前页面：扫描 → 后端匹配 → 经历块不足时确认并自动新增 → 重扫重匹配 → 分组预览 → 批量填入（不自动提交） */
async function oneClickFill(): Promise<void> {
  try {
    // 一键填写前先采集当前招聘页面写入投递信息表（失败不阻断填写流程）
    captureCurrentPage(false).catch(() => { /* 采集失败不影响填写 */ });
    setToast('扫描页面字段中…', 'info');
    let fields = scanFields();
    if (fields.length === 0) {
      setToast('未发现可填写字段', 'warn');
      return;
    }
    const st = await getPanelState();
    const tpl = currentTemplate();
    const body = {
      templateId: tpl?.id || null,
      pageUrl: location.href,
      pageTitle: document.title,
      audienceType: tpl?.audienceType || undefined,
      jobDirection: st.selectedJobDirection || undefined,
      preferredInternshipId: st.selectedPriorityExperience || undefined,
      fillType: 'auto',
    };
    let resp = await api<AutofillMatchResponse>('/api/autofill/match', 'POST', { ...body, fields });

    // 经历计划比对：块不足时先与用户确认再自动点击“添加经历”按钮
    const addedAny = await ensureExperienceBlocks(resp);
    if (addedAny) {
      setToast('重新扫描新增后的页面…', 'info');
      fields = scanFields();
      resp = await api<AutofillMatchResponse>('/api/autofill/match', 'POST', { ...body, fields });
    }
    lastMatchResponse = resp;
    showFillPreview(resp, fields);
  } catch (err: any) {
    setToast(`填写失败：${err?.message || err}`, 'error');
  }
}

// ==================== 投递记录采集（招聘网站页面 → 投递信息表） ====================

/** 最近一次招聘页面检测结果 */
let trackDetection: JobPageDetectResult | null = null;
/** 采集结果提示（保存/更新/需确认） */
let trackMessage = '';

/** 检测当前页面并渲染投递记录区；招聘网站且置信度足够时自动写入投递信息表 */
async function refreshApplicationTrack(): Promise<void> {
  const box = ui['track-box'];
  if (!box) return;
  const auth = await getAuth();
  if (!auth) {
    box.textContent = '登录后自动采集当前招聘页面的公司/岗位/网址';
    return;
  }
  trackDetection = detectJobPage();
  trackMessage = '';
  renderTrackBox();
  await captureCurrentPage(false);
}

/**
 * 采集并写入投递信息表：
 * - 后端判定已存在相同公司/机构/岗位 → 仅更新最近访问时间/网址；
 * - 新记录 → 默认状态“准备中”、渠道“插件采集”；
 * - 置信度低且未确认 → 不入库，提示用户确认/编辑后保存。
 */
async function captureCurrentPage(confirmed: boolean, overrides?: Partial<ApplicationCapturePayload>): Promise<void> {
  const det = trackDetection;
  if (!det || (!det.isJobSite && !det.companyName)) return;
  try {
    const result = await api<ApplicationCaptureResult>('/api/application-records/capture', 'POST', {
      companyName: det.companyName,
      organizationName: det.organizationName,
      positionName: det.positionName,
      pageUrl: det.pageUrl,
      pageTitle: det.pageTitle,
      domain: det.domain,
      recruitmentUrl: det.recruitmentUrl,
      resumeEditUrl: det.resumeEditUrl,
      resumeModifiedAt: det.resumeModifiedAt || undefined,
      resumeModifiedSource: det.resumeModifiedSource || undefined,
      confidenceScore: det.confidenceScore,
      confirmed,
      ...overrides,
    });
    if (result.action === 'need_confirm') {
      trackMessage = '检测到可能的投递信息（置信度较低），请确认或编辑后保存';
    } else if (result.action === 'updated') {
      trackMessage = `已记录，本次已更新最近访问时间（当前状态：${result.applyStatus || '-'}）`;
    } else {
      trackMessage = `已新增到投递信息表（默认状态：${result.applyStatus || '准备中'}）`;
    }
  } catch (err: any) {
    trackMessage = `采集失败：${err?.message || err}`;
  }
  renderTrackBox();
}

/** 渲染投递记录区：检测结果 + 保存/编辑/打开管理后台按钮 */
function renderTrackBox(): void {
  const box = ui['track-box'];
  if (!box) return;
  box.innerHTML = '';
  const det = trackDetection;
  if (!det || (!det.isJobSite && !det.companyName)) {
    box.appendChild(el('div', 'rf-track-empty',
      '当前页面未识别为招聘网站。打开招聘/网申页面时将自动采集公司、岗位、网址并写入投递信息表。'));
    return;
  }
  const rows: Array<[string, string]> = [
    ['公司', det.companyName || '未识别'],
    ['机构', det.organizationName || '—'],
    ['岗位', det.positionName || '—'],
    ['置信度', `${Math.round(det.confidenceScore * 100)}%`],
    ['页面', det.pageUrl.length > 64 ? det.pageUrl.slice(0, 64) + '…' : det.pageUrl],
  ];
  if (det.resumeModifiedAt) {
    rows.push(['简历修改时间', `${det.resumeModifiedAt.replace('T', ' ').slice(0, 16)}（${det.resumeModifiedSource || '-'}）`]);
  }
  for (const [k, v] of rows) {
    const row = el('div', 'rf-track-row');
    row.append(el('span', 'rf-track-label', k), el('span', 'rf-track-value', v));
    box.appendChild(row);
  }
  if (trackMessage) {
    box.appendChild(el('div', 'rf-track-msg', trackMessage));
  }
  const btnRow = el('div', 'rf-row');
  const btnSave = el('button', 'rf-btn rf-btn-small rf-btn-primary', '保存到投递表');
  btnSave.title = '确认后写入投递信息表（低置信度也保存）';
  btnSave.addEventListener('click', () => captureCurrentPage(true));
  const btnEdit = el('button', 'rf-btn rf-btn-small', '编辑后保存');
  btnEdit.addEventListener('click', () => openTrackEditForm());
  const btnAdmin = el('button', 'rf-btn rf-btn-small', '打开投递表');
  btnAdmin.title = '打开管理后台投递信息表';
  btnAdmin.addEventListener('click', () => openAdminApplications());
  btnRow.append(btnSave, btnEdit, btnAdmin);
  box.appendChild(btnRow);
}

/** 内联编辑表单：用户修正插件识别错误的公司/机构/岗位后保存 */
function openTrackEditForm(): void {
  const box = ui['track-box'];
  const det = trackDetection;
  if (!box || !det) return;
  ui['track-edit-form']?.remove();
  const form = el('div', 'rf-track-edit');
  const inpCompany = el('input', 'rf-search') as HTMLInputElement;
  inpCompany.value = det.companyName;
  inpCompany.placeholder = '公司 / 单位名称';
  const inpOrg = el('input', 'rf-search') as HTMLInputElement;
  inpOrg.value = det.organizationName;
  inpOrg.placeholder = '机构 / 部门（可空）';
  const inpPos = el('input', 'rf-search') as HTMLInputElement;
  inpPos.value = det.positionName;
  inpPos.placeholder = '岗位名称（可空）';
  const btnSave = el('button', 'rf-btn rf-btn-small rf-btn-primary', '确认保存');
  btnSave.addEventListener('click', async () => {
    await captureCurrentPage(true, {
      companyName: inpCompany.value.trim(),
      organizationName: inpOrg.value.trim(),
      positionName: inpPos.value.trim(),
    });
    form.remove();
  });
  const btnCancel = el('button', 'rf-btn rf-btn-small', '取消');
  btnCancel.addEventListener('click', () => form.remove());
  const row = el('div', 'rf-row');
  row.append(btnSave, btnCancel);
  form.append(inpCompany, inpOrg, inpPos, row);
  ui['track-edit-form'] = form;
  box.appendChild(form);
}

/** 打开管理后台投递信息表（同源部署：管理后台与后端同一域名） */
async function openAdminApplications(): Promise<void> {
  try {
    const backendUrl = await getBackendUrl();
    const base = new URL(backendUrl);
    window.open(`${base.origin}/applications`, '_blank');
  } catch {
    setToast('无法打开管理后台：请先在设置中配置后端地址', 'warn');
  }
}

/** 比对后端经历计划与页面已有块数（实习/项目/教育/荣誉/家庭成员），不足时弹窗确认后自动新增 */
async function ensureExperienceBlocks(resp: AutofillMatchResponse): Promise<boolean> {
  const plan = resp.experiencePlan || [];
  const types: RepeatableBlockType[] = ['internship', 'project', 'education', 'award', 'family'];
  const planCounts: Record<string, number> = {};
  for (const item of plan) planCounts[item.type] = (planCounts[item.type] || 0) + 1;

  const missing: Array<{ type: RepeatableBlockType; need: number; add: number }> = [];
  for (const t of types) {
    if (!planCounts[t]) continue;
    const existing = countBlocks(t);
    if (existing < planCounts[t]) {
      missing.push({ type: t, need: planCounts[t], add: planCounts[t] - existing });
    }
  }
  if (missing.length === 0) return false;

  const typeLabel: Record<string, string> = {
    internship: '工作/实习经历', project: '项目经历', education: '教育经历',
    award: '荣誉奖项', family: '家庭成员',
  };
  const lines = missing.map((m) => {
    return `${typeLabel[m.type]}：需要 ${m.need} 段，页面已有 ${m.need - m.add} 段，将自动新增 ${m.add} 段`;
  });
  const ok = await confirmDialog(
    `检测到当前模板需要填写多段经历：\n${lines.join('\n')}\n\n是否自动点击页面上的“添加/新增”按钮新增？`,
    '自动新增并继续', '跳过新增',
  );
  if (!ok) return false;

  let addedAny = false;
  for (const m of missing) {
    const result = await ensureBlocks(m.type, m.need);
    if (result.added > 0) addedAny = true;
    if (result.failed) {
      setToast(`部分经历块新增失败（已新增 ${result.added} 段），请手动添加后重新扫描`, 'warn');
    }
  }
  return addedAny;
}

/** 字段证据文本（疑似检测用）：优先中文 label，其次问题/附近文本 */
function fieldEvidenceText(info: FieldInfo | null): string {
  if (!info) return '';
  return [info.label, info.questionText, info.nearbyText, info.ariaLabel, info.placeholder, info.sectionTitle]
    .filter(Boolean).join(' ');
}

/** 素材类型标签提取：matchedFieldName 形如“xxx[AI_COLLABORATION]” */
function materialTag(matchedFieldName: string): string {
  const m = matchedFieldName.match(/\[([A-Z_]+)\]/);
  return m ? m[1] : '';
}

const RESPONSIBILITY_LABEL = /职责|描述|工作内容|实习内容|主要工作|负责内容|负责事项|业绩|成果|贡献/;
const NAME_LABEL = /姓名|名字|候选人|申请人|真实姓名|\bname\b/i;
const DATE_LIKE = /^\s*\d{4}\s*[-./年]/;

/** 疑似错误检测：命中则默认不勾选并醒目标记（需求第十五节） */
function detectSuspicious(row: { match: MatchResult; fieldInfo: FieldInfo | null }): string {
  const { match, fieldInfo } = row;
  const evidence = fieldEvidenceText(fieldInfo);
  const value = String(match.value || '');
  const isNamePick = match.matchedFieldKey === 'name' && !NAME_LABEL.test(evidence);
  if (isNamePick && /单位|公司|企业|雇主|岗位|职位|职务|城市|地点|月薪|薪资|薪酬|语言|听说|读写|掌握程度|行业|职业|工作年限|出生|部门|学校|专业|学历/.test(evidence)) {
    return '姓名被误匹配到非姓名字段';
  }
  if (/日期|时间/.test(evidence) && value && !DATE_LIKE.test(value) && !/至今|目前/.test(value)) {
    return '日期字段推荐内容不是日期格式';
  }
  const tag = materialTag(match.matchedFieldName);
  if (RESPONSIBILITY_LABEL.test(evidence) && tag && tag !== 'INTERNSHIP' && tag !== 'PROJECT') {
    return `工作职责误匹配到「${MATERIAL_TYPE_LABELS[tag] || tag}」素材`;
  }
  return '';
}

/** 置信度分层：高可信 ≥0.85 默认勾选 / 中可信 0.70-0.85 建议确认 / 低可信 0.50-0.70 默认不勾选 */
function confidenceTier(confidence: number): ConfidenceTier {
  if (confidence >= 0.85) return 'high';
  if (confidence >= 0.70) return 'medium';
  if (confidence >= 0.50) return 'low';
  return 'unmatched';
}

const TIER_LABELS: Record<ConfidenceTier, string> = {
  high: '高可信', medium: '中可信', low: '低可信', unmatched: '未匹配',
};

/** 行状态：疑似错误 / 超字数 / 置信度分层标签 */
function rowStatus(row: { match: MatchResult; fieldInfo: FieldInfo | null }, suspiciousReason: string): string {
  if (suspiciousReason) return '疑似错误';
  const limit = row.fieldInfo?.wordLimit ?? null;
  if (limit != null && String(row.match.value || '').length > limit) return '超字数';
  const tier = confidenceTier(row.match.confidence);
  return tier === 'medium' ? '中可信·建议确认' : TIER_LABELS[tier];
}

/** 客户端 finalValidation：确认填入前对勾选项逐条重校验类型合法性，不通过返回原因 */
function finalValidation(row: PreviewRow): string {
  const evidence = fieldEvidenceText(row.fieldInfo);
  const value = String(row.match.value || '').trim();
  if (!value) return '推荐内容为空';
  if (/邮箱|e-?mail/i.test(evidence) && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) {
    return '邮箱字段内容不是邮箱格式';
  }
  if (/手机号|联系电话|联系方式/.test(evidence) && !/^1\d{10}$/.test(value)) {
    return '手机号字段内容不是 11 位大陆手机号';
  }
  if (/姓名/.test(evidence) && !/证明人|联系人|亲属|家属|紧急|父亲|母亲|推荐人/.test(evidence)
    && !/^[\u4e00-\u9fa5·]{2,15}$/.test(value) && !/^[A-Za-z\s]{2,30}$/.test(value)) {
    return '姓名字段内容不符合姓名格式';
  }
  if (/日期|出生时间|出生年月/.test(evidence) && !DATE_LIKE.test(value) && !/至今|目前/.test(value)) {
    return '日期字段内容不是日期格式';
  }
  return '';
}

/** 预览分组：基础/个人/求职意向/教育(按学校)/工作(按记录)/项目/家庭/语言/荣誉/专利/技能/科研/校园/开放题 */
function buildPreviewGroups(rows: PreviewRow[], resp: AutofillMatchResponse): Map<string, PreviewRow[]> {
  const groups = new Map<string, PreviewRow[]>();
  const push = (key: string, row: PreviewRow) => {
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(row);
  };
  for (const row of rows) {
    const m = row.match;
    const evidence = fieldEvidenceText(row.fieldInfo);
    const blockIdx = (row.fieldInfo?.blockIndex ?? 0) + 1;
    if (m.group === 'work_experience') {
      push(`工作/实习经历 · ${m.recordName || '经历 ' + blockIdx}`, row);
    } else if (m.group === 'project_experience') {
      push(`项目经历 · ${m.recordName || '项目 ' + blockIdx}`, row);
    } else if (m.group === 'education' || row.fieldInfo?.blockType === 'education') {
      push(`教育经历 · ${m.recordName || '学校 ' + blockIdx}`, row);
    } else if (m.group === 'award' || row.fieldInfo?.blockType === 'award') {
      push(`荣誉奖项 · ${m.recordName || '奖项 ' + blockIdx}`, row);
    } else if (m.group === 'family' || row.fieldInfo?.blockType === 'family') {
      push(`家庭情况 · ${m.recordName || '成员 ' + blockIdx}`, row);
    } else if (m.group === 'emergency') {
      push('紧急联系人', row);
    } else if (m.group === 'language' || row.fieldInfo?.blockType === 'language') {
      push('语言能力', row);
    } else if (m.group === 'patent') {
      push('专利成果', row);
    } else if (m.group === 'research') {
      push('科研经历', row);
    } else if (m.group === 'campus') {
      push('校园经历', row);
    } else if (m.group === 'skill') {
      push('专业技能', row);
    } else if (m.group === 'material') {
      push('开放题', row);
    } else if (m.group === 'intent' || /期望|意向|到岗|应聘类别|招聘对象/.test(evidence)) {
      push('求职意向', row);
    } else if (/性别|邮箱|手机|电话|证件|身份证|出生|国籍|民族|政治面貌|婚姻|身高|户籍|户口|籍贯|生源|地址|年龄/.test(evidence)) {
      push('个人信息', row);
    } else {
      push('基础信息', row);
    }
  }
  // 未匹配字段组（默认不勾选，仅提示手动填写）
  const infoById = new Map(rows.map((r) => [r.match.fieldId, r.fieldInfo]));
  for (const item of resp.unmatched || []) {
    const info = infoById.get(item.fieldId) || null;
    push('未匹配字段', {
      match: { fieldId: item.fieldId, matchedFieldKey: '', matchedFieldName: item.reason || '未匹配', value: '', confidence: 0, reason: item.reason },
      fieldInfo: info, checked: false, tier: 'unmatched', suspiciousReason: '', status: '未匹配',
    });
  }
  return groups;
}

/** 分组预览弹窗：置信度分层统计 + 快捷按钮，疑似错误/低可信默认不勾选，确认前执行 finalValidation */
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
    // 疑似错误以后端类型校验结果优先，再叠加客户端检测（禁止复用任何全局推荐结果）
    const suspiciousReason = m.suspicious
      ? (m.suspiciousReason || '值类型与字段语义冲突')
      : detectSuspicious({ match: m, fieldInfo: info });
    const tier = confidenceTier(m.confidence);
    const status = rowStatus({ match: m, fieldInfo: info }, suspiciousReason);
    // 默认勾选策略：仅高可信 + 中可信；疑似错误 / 超字数 / 低可信 / 未匹配不勾选
    const checked = !suspiciousReason && !overLimit && (tier === 'high' || tier === 'medium');
    return { match: m, fieldInfo: info, checked, tier, suspiciousReason, status };
  });
  const tierCount: Record<ConfidenceTier, number> = { high: 0, medium: 0, low: 0, unmatched: 0 };
  for (const r of rows) tierCount[r.tier]++;
  const suspiciousCount = rows.filter((r) => r.suspiciousReason).length;
  const unmatchedCount = (resp.unmatched || []).length;

  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog rf-dialog-wide');
  box.appendChild(el('div', 'rf-dialog-title', `填充预览：匹配 ${matches.length} 项，未匹配 ${unmatchedCount} 项`));
  // 顶部统计：分层数量与默认勾选策略
  box.appendChild(el('div', 'rf-preview-stats',
    `高可信 ${tierCount.high} 项（默认勾选） · 中可信 ${tierCount.medium} 项（建议确认）`
    + ` · 低可信 ${tierCount.low} 项（默认不勾选） · 未匹配 ${unmatchedCount} 项`
    + (suspiciousCount ? ` · 疑似错误 ${suspiciousCount} 项（默认不勾选）` : '')));

  const listHolder = el('div', 'rf-preview-list');
  const renderList = () => {
    listHolder.innerHTML = '';
    const groups = buildPreviewGroups(rows, resp);
    const orderedKeys = Array.from(groups.keys()).sort((a, b) => groupOrder(a) - groupOrder(b));
    for (const key of orderedKeys) {
      listHolder.appendChild(el('div', 'rf-preview-group-title', `${key}（${groups.get(key)!.length}）`));
      for (const row of groups.get(key)!) listHolder.appendChild(buildPreviewItem(row));
    }
  };
  renderList();
  box.appendChild(listHolder);

  // 快捷按钮：按置信度分层批量勾选 / 取消
  const setChecked = (pred: (r: PreviewRow) => boolean) => {
    rows.forEach((r) => { r.checked = pred(r) && !r.suspiciousReason; });
    renderList();
  };
  const quick = el('div', 'rf-card-actions');
  const mkQuickBtn = (label: string, fn: () => void) => {
    const b = el('button', 'rf-btn rf-btn-tiny', label);
    b.addEventListener('click', fn);
    quick.appendChild(b);
  };
  mkQuickBtn('只填高可信', () => setChecked((r) => r.tier === 'high'));
  mkQuickBtn('填高可信+中可信', () => setChecked((r) => r.tier === 'high' || r.tier === 'medium'));
  mkQuickBtn('全部取消', () => setChecked(() => false));
  if (suspiciousCount > 0) {
    mkQuickBtn('取消疑似错误', () => {
      rows.filter((r) => r.suspiciousReason).forEach((r) => { r.checked = false; });
      renderList();
    });
  }
  box.appendChild(quick);

  const actions = el('div', 'rf-card-actions');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnCancel.addEventListener('click', () => mask.remove());
  const btnConfirm = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '确认填入选中项');
  btnConfirm.addEventListener('click', async () => {
    // finalValidation：确认前对所有勾选项重校验类型，不通过自动取消勾选并移入疑似错误，不允许填入
    let invalid = 0;
    for (const r of rows) {
      if (!r.checked) continue;
      const reason = finalValidation(r);
      if (reason) {
        r.checked = false;
        r.suspiciousReason = reason;
        r.status = '疑似错误';
        invalid++;
      }
    }
    if (invalid > 0) {
      renderList();
      setToast(`${invalid} 项未通过最终类型校验，已自动取消勾选，请检查`, 'warn');
      return;
    }
    // 检查用户是否手动勾选了疑似错误字段，需二次确认
    const checkedSusp = rows.filter((r) => r.checked && r.suspiciousReason);
    if (checkedSusp.length > 0) {
      const example = checkedSusp.slice(0, 2).map((r) => {
        const label = r.fieldInfo?.label || r.fieldInfo?.questionText || r.match.fieldId;
        return `「${label}」${r.suspiciousReason}`;
      }).join('；');
      const proceed = await confirmDialog(
        `当前存在 ${checkedSusp.length} 个疑似错误字段，例如：${example}。建议取消这些字段或手动调整后再填入。是否仍要填入这些字段？`,
        '仍要填入', '返回调整',
      );
      if (!proceed) return;
    }
    const checkedCount = rows.filter((r) => r.checked).length;
    if (checkedCount === 0) {
      setToast('当前没有勾选任何字段', 'warn');
      return;
    }
    mask.remove();
    await applyPreviewFills(rows, resp);
  });
  actions.append(btnCancel, btnConfirm);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
}

/** 单个预览项：勾选框 + 字段 label / 来源 / 记录 / 类型 / 字数 / 分数 / 匹配原因 / 状态 */
function buildPreviewItem(row: PreviewRow): HTMLElement {
  const m = row.match;
  const item = el('div', 'rf-preview-item' + (row.suspiciousReason ? ' rf-preview-item-suspicious' : ''));
  const chk = document.createElement('input');
  chk.type = 'checkbox';
  chk.checked = row.checked;
  chk.addEventListener('change', () => { row.checked = chk.checked; });
  const meta = el('div', 'rf-preview-meta');
  const label = row.fieldInfo?.label || row.fieldInfo?.questionText || row.fieldInfo?.placeholder || m.fieldId;
  const content = String(m.value || '');
  const limit = row.fieldInfo?.wordLimit ?? null;
  const tags: string[] = [row.fieldInfo?.type || '', `${content.length} 字`];
  if (limit != null) tags.push(`限 ${limit} 字`);
  if (m.recordName) tags.push(`来源：${m.recordName}`);
  if (m.matchedFieldKey) tags.push(`字段：${m.matchedFieldKey}`);
  tags.push(`分数 ${(m.confidence * 100).toFixed(0)}`);
  tags.push(row.status);
  if (row.suspiciousReason) tags.push(`⚠ ${row.suspiciousReason}`);
  meta.appendChild(el('div', 'rf-preview-title', `${label} ← ${m.matchedFieldName || '（无）'}`));
  meta.appendChild(el('div', 'rf-preview-tags', tags.filter(Boolean).join(' · ')));
  if (m.reason) meta.appendChild(el('div', 'rf-preview-reason', `匹配原因：${m.reason}`));
  if (content) meta.appendChild(el('div', 'rf-preview-content', content.slice(0, 60) + (content.length > 60 ? '…' : '')));
  else meta.appendChild(el('div', 'rf-preview-content', '未匹配，需手动填写'));
  item.append(chk, meta);
  return item;
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
      details.push(`已填充: ${row.match.fieldId} -> ${row.match.matchedFieldName}`);
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
    filled, skipped,
    needConfirm: 0, unmatched: (resp.unmatched || []).length, details, confirmItems: [] as ConfirmItem[],
  };
  chrome.storage.session?.set?.({
    lastFillReport: {
      time: new Date().toLocaleString(), total: rows.length + (resp.skipped || []).length,
      filled: result.filled, skipped: result.skipped,
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

type FillSource = { kind: 'field' | 'material' | 'temp'; refId?: number | null; fillType?: 'auto' | 'manual'; structType?: StructFieldType };

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
  // 强制填入守卫：内容类型与当前输入框类型冲突时二次确认，用户确认后才填入
  const targetType = info ? detectTargetFieldType(info) : null;
  const srcType = source.structType ?? null;
  if (targetType && srcType && !structTypeCompatible(targetType, { structType: srcType, fieldValue: value } as CustomFieldItem & { structType?: StructFieldType })) {
    const ok = await confirmDialog(
      `当前输入框识别为“${STRUCT_TYPE_LABELS[targetType]}”，但你选择的是“${STRUCT_TYPE_LABELS[srcType] || '类型不符'}”内容，可能填错。是否仍然填入？`,
      '仍然填入', '取消',
    );
    if (!ok) return false;
  }
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
  const recs = recommendForTarget(info, tplId);
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

/**
 * 当前输入框智能推荐：fieldType 优先级高于当前 tab。
 * 先识别输入框字段类型，再按类型过滤候选（结构化子字段 + 普通字段）：
 * 单位名称字段只推荐公司名，绝不推荐项目描述/整段经历；日期字段只推荐日期。
 */
function recommendForTarget(info: FieldInfo, tplId: number | null): RecommendItem[] {
  const targetType = detectTargetFieldType(info);
  const candidates: Array<CustomFieldItem & { structType?: StructFieldType }> = [
    ...structuredRecommendCandidates(),
    ...(cache?.cachedFields || []),
  ];
  const filtered = targetType ? candidates.filter((c) => structTypeCompatible(targetType, c)) : candidates;
  return recommendFields(info, filtered, { templateId: tplId });
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
    `填写报告：已填 ${result.filled}，跳过 ${result.skipped}，待确认 ${result.needConfirm}，未匹配 ${result.unmatched}`));

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
  // 识别详情：detectedFieldType / sectionType / blockIndex / 推荐内容类型（推荐优先于当前 tab）
  const fType = info ? detectTargetFieldType(info) : null;
  const meta: string[] = [];
  if (fType) meta.push(`detectedFieldType: ${fType}（${STRUCT_TYPE_LABELS[fType]}）`);
  if (info?.sectionTitle) meta.push(`sectionType: ${info.sectionTitle}`);
  if (info?.blockType) meta.push(`blockIndex: ${(info.blockIndex ?? 0) + 1}（${info.blockType}）`);
  if (fType) meta.push(`推荐内容类型: ${STRUCT_TYPE_LABELS[fType]}`);
  if (meta.length > 0) bar.appendChild(el('div', 'rf-target-detail', meta.join(' · ')));
  bar.className = 'rf-target-bar rf-target-bar-active';
  renderRecommendations();
  // 目标输入框变化后刷新卡片适配状态（适合/需确认/不建议）
  renderCards();
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

/**
 * 结构化记录展开为只读内容卡片：家庭成员（父亲/母亲）、紧急联系人、各段实习的证明人。
 * 空值显示“未填写”且不参与自动填充；编辑请前往管理后台（插件侧只展示与填入）。
 */
function structuredFieldCards(): CustomFieldItem[] {
  const cards: CustomFieldItem[] = [];
  const mk = (category: string, key: string, name: string, value: string | null | undefined, order: number): CustomFieldItem => ({
    fieldKey: key, fieldName: name, fieldType: 'text', fieldCategory: category,
    fieldValue: value && String(value).trim() ? String(value) : '未填写',
    matchKeywords: [], enabled: true, readOnly: true, sortOrder: order,
  });

  (cache?.cachedFamilyList || []).forEach((m: any, i: number) => {
    const rel = m.relation || `家庭成员${i + 1}`;
    const items: Array<[string, string | null | undefined]> = [
      ['姓名', m.name], ['单位', m.company], ['职务', m.position],
      ['联系电话', m.phone], ['邮箱', m.email], ['地址', m.address],
    ];
    items.forEach(([label, v], j) => cards.push(mk('家庭成员', `family_${m.id ?? i}_${label}`, `${rel}${label}`, v, i * 10 + j)));
  });

  (cache?.cachedEmergencyContactList || []).forEach((c: any, i: number) => {
    const items: Array<[string, string | null | undefined]> = [
      ['姓名', c.name], ['与本人关系', c.relation], ['电话', c.phone],
      ['单位', c.company], ['职务', c.position],
    ];
    items.forEach(([label, v], j) => cards.push(mk('紧急联系人', `emergency_${c.id ?? i}_${label}`, `紧急联系人${label}`, v, i * 10 + j)));
  });

  // 实习证明人已并入实习 record card 结构化子字段展示，不再单独生成只读卡片，避免重复。
  return cards;
}

// ==================== 实习/项目结构化记录卡片 ====================

/** 结构化子字段类型：当前输入框智能推荐按类型过滤，禁止把整段描述填入单位/职位等短字段 */
type StructFieldType = 'company' | 'position' | 'startDate' | 'endDate' | 'dateRange'
  | 'personName' | 'email' | 'phone' | 'responsibility' | 'projectName' | 'projectDesc' | 'certifier' | 'other';

/** 结构化记录子字段行：短字段，支持 填入当前输入框 / 临时编辑后填入 / 复制；空值显示“未填写”不允许填入 */
function buildStructRow(recordTitle: string, label: string, value: string | null | undefined, structType?: StructFieldType): HTMLElement {
  const text = value && String(value).trim() ? String(value).trim() : '';
  const row = el('div', 'rf-struct-row');
  row.appendChild(el('div', 'rf-struct-label', label));
  row.appendChild(el('div', text ? 'rf-struct-value' : 'rf-struct-value rf-struct-empty', text || '未填写'));
  const actions = el('div', 'rf-card-actions');
  const name = `${recordTitle}-${label}`;
  if (text) {
    const pseudo: CustomFieldItem = {
      fieldKey: '', fieldName: name, fieldType: 'text', fieldCategory: '', fieldValue: text,
      matchKeywords: [], enabled: true, readOnly: true,
    };
    const btnFill = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入当前输入框');
    btnFill.addEventListener('click', () => fillValueToTarget(text, name, { kind: 'field', refId: null, fillType: 'manual', structType }));
    const btnTemp = el('button', 'rf-btn rf-btn-tiny', '临时编辑后填入');
    btnTemp.addEventListener('click', () => tempEditDialog(pseudo));
    const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
    btnCopy.addEventListener('click', () => copyText(text));
    actions.append(btnFill, btnTemp, btnCopy);
    if (structType) {
      const fit = candidateFitState({ ...pseudo, structType });
      if (fit !== 'unknown') actions.prepend(el('span', FIT_TAG[fit][1], FIT_TAG[fit][0]));
    }
  } else {
    actions.appendChild(el('span', 'rf-card-key', '未填写·管理后台补充'));
  }
  row.appendChild(actions);
  return row;
}

/** 结构化长文本行：预览 + 字数版本（完整/≤500/≤300/≤200/≤100）+ 临时编辑 + 复制；优先用后端下发的字数版本变体，无则截断 */
function buildLongTextRow(recordTitle: string, label: string, value: string, structType?: StructFieldType,
  variants?: Array<{ lengthType?: string; content?: string }>): HTMLElement {
  const row = el('div', 'rf-struct-row rf-struct-row-block');
  row.appendChild(el('div', 'rf-struct-label', `${label}（${value.length} 字）`));
  const preview = el('div', 'rf-struct-value', value.slice(0, 80) + (value.length > 80 ? '…' : ''));
  preview.title = value;
  row.appendChild(preview);
  const actions = el('div', 'rf-card-actions');
  const name = `${recordTitle}-${label}`;
  // 字数版本取内容：优先后端挂载的同档位变体（与自动填充同源），无则本地截断
  const VARIANT_LEN: Record<number, string> = { 200: 'within_200', 300: 'within_300', 500: 'within_500' };
  const contentFor = (limit: number | null): string => {
    if (limit == null) return value;
    const v = (variants || []).find((x) => x.lengthType === VARIANT_LEN[limit] && x.content);
    return v?.content || value.slice(0, limit);
  };
  const versions: Array<[string, number | null]> = [['完整版', null], ['≤500字', 500], ['≤300字', 300], ['≤200字', 200], ['≤100字', 100]];
  for (const [tag, limit] of versions) {
    if (limit != null && value.length <= limit) continue;
    const btn = el('button', 'rf-btn rf-btn-tiny', `填入${tag}`);
    btn.addEventListener('click', () => fillValueToTarget(
      contentFor(limit), `${name}（${tag}）`, { kind: 'field', refId: null, fillType: 'manual', structType }));
    actions.appendChild(btn);
  }
  const pseudo: CustomFieldItem = {
    fieldKey: '', fieldName: name, fieldType: 'text', fieldCategory: '', fieldValue: value,
    matchKeywords: [], enabled: true, readOnly: true,
  };
  const btnTemp = el('button', 'rf-btn rf-btn-tiny', '临时编辑后填入');
  btnTemp.addEventListener('click', () => tempEditDialog(pseudo));
  const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
  btnCopy.addEventListener('click', () => copyText(value));
  actions.append(btnTemp, btnCopy);
  if (structType) {
    const fit = candidateFitState({ ...pseudo, structType });
    if (fit !== 'unknown') actions.prepend(el('span', FIT_TAG[fit][1], FIT_TAG[fit][0]));
  }
  row.appendChild(actions);
  return row;
}

/** 长文本子字段：有值用版本行，空值显示未填写；variants 为后端下发的同字段字数版本 */
function appendLongText(card: HTMLElement, recordTitle: string, label: string, value: string | null | undefined,
  structType?: StructFieldType, variants?: Array<{ lengthType?: string; content?: string }>): void {
  const text = value && String(value).trim() ? String(value).trim() : '';
  card.appendChild(text ? buildLongTextRow(recordTitle, label, text, structType, variants) : buildStructRow(recordTitle, label, '', structType));
}

/** 后端经历计划中与指定块绑定的记录 id（默认推荐当前 block 绑定 record） */
function boundRecordId(blockType: string, blockIndex: number): number | null {
  const plan = (lastMatchResponse?.experiencePlan || []).filter((p) => p.type === blockType);
  return plan[blockIndex]?.id ?? null;
}

/** 当前选中输入框所在块的绑定记录 id（用于 record card 置顶与标记） */
function currentBlockBoundId(blockType: 'internship' | 'project'): number | null {
  const target = getCurrentTarget();
  if (!target) return null;
  const info = scanElement(target);
  if (!info || info.blockType !== blockType) return null;
  return boundRecordId(blockType, info.blockIndex ?? 0);
}

/** 按字段 label 从绑定记录中取对应子字段值：同一块内所有字段只来自同一条记录，禁止跨记录串数据 */
function pickRecordValue(kind: 'internship' | 'project', r: any, f: FieldInfo): string {
  const text = [f.label, f.questionText, f.nearbyText, f.ariaLabel, f.placeholder].filter(Boolean).join(' ');
  if (kind === 'internship') {
    if (/证明人单位及职务|单位及职务/.test(text)) return r.certifierCompanyAndPosition || '';
    if (/证明人.*电话|联系人电话|主管电话/.test(text)) return r.certifierPhone || '';
    if (/证明人.*邮箱/.test(text)) return r.certifierEmail || '';
    if (/证明人|联系人|推荐人|主管/.test(text)) return r.certifierName || '';
    if (/单位|公司|企业|雇主/.test(text)) return r.company || '';
    if (/职位|岗位|职务/.test(text)) return r.position || '';
    if (/开始时间|入职时间|起始/.test(text)) return r.startDate || '';
    if (/结束时间|离职时间|截止/.test(text)) return r.endDate || '';
    if (/部门/.test(text)) return r.department || '';
    if (/职责|工作内容|实习内容|主要职责|描述/.test(text)) return r.description || '';
    if (/成果|业绩/.test(text)) return r.highlights || '';
    return '';
  }
  if (/项目名称/.test(text)) return r.projectName || '';
  if (/角色/.test(text)) return r.role || '';
  if (/技术栈|技术/.test(text)) return r.techStack || '';
  if (/开始时间/.test(text)) return r.startDate || '';
  if (/结束时间/.test(text)) return r.endDate || '';
  if (/主要工作|职责|工作内容/.test(text)) return r.responsibilities || '';
  if (/项目成果|成果|业绩/.test(text)) return r.result || '';
  if (/项目描述|项目简介|项目介绍|描述/.test(text)) return r.projectIntro || r.description || '';
  return '';
}

/** 整组填充：把选定记录绑定到当前输入框所在块，逐字段填入同一记录的子字段；记录中为空的字段跳过不填 */
async function fillRecordToBlock(kind: 'internship' | 'project', record: any): Promise<void> {
  const target = getCurrentTarget();
  const info = target ? scanElement(target) : null;
  if (!info?.blockType) {
    setToast(`请先点击页面中${kind === 'internship' ? '工作/实习经历' : '项目经历'}块内的一个输入框`, 'warn');
    return;
  }
  const fields = scanFields().filter((f) => f.blockType === info.blockType && f.blockIndex === (info.blockIndex ?? 0));
  if (fields.length === 0) {
    setToast('当前块内未识别到字段，请重新扫描', 'warn');
    return;
  }
  let filled = 0;
  let skipped = 0;
  for (const f of fields) {
    const elem = locateFieldElement(f.fieldId);
    if (!elem) { skipped++; continue; }
    let val = pickRecordValue(kind, record, f);
    if (!val) { skipped++; continue; }
    const limit = f.wordLimit ?? null;
    if (limit != null && val.length > limit) val = val.slice(0, limit);
    undoStack.push({ el: elem, prevText: readElementText(elem) });
    const res = setElementValue(elem, val);
    if (res.success) filled++;
    else skipped++;
  }
  setToast(`整块填充完成：已填 ${filled} 个字段，跳过 ${skipped} 个（记录中未填写或未识别）`, filled > 0 ? 'success' : 'warn');
}

/** 实习 record card：基础信息 / 工作内容 / 证明人 三区域，每个子字段可单独填入 */
function buildInternshipRecordCard(n: any, boundToBlock = false): HTMLElement {
  const title = n.shortName || n.company || `实习记录#${n.id ?? ''}`;
  const card = el('div', 'rf-card rf-record-card');
  const head = el('div', 'rf-card-head');
  head.appendChild(el('span', 'rf-card-name', `${title}实习`));
  if (n.startDate || n.endDate) head.appendChild(el('span', 'rf-card-key', `${n.startDate || '?'} ~ ${n.endDate || '至今'}`));
  if (boundToBlock) head.appendChild(el('span', 'rf-fit-tag rf-fit-ok', '当前块绑定'));
  const btnBlock = el('button', 'rf-btn rf-btn-tiny', '填入当前工作经历块');
  btnBlock.addEventListener('click', () => fillRecordToBlock('internship', n));
  head.appendChild(btnBlock);
  card.appendChild(head);

  card.appendChild(el('div', 'rf-struct-section', '基础信息'));
  card.appendChild(buildStructRow(title, '单位名称', n.company, 'company'));
  card.appendChild(buildStructRow(title, '职位名称', n.position, 'position'));
  card.appendChild(buildStructRow(title, '开始时间', n.startDate, 'startDate'));
  card.appendChild(buildStructRow(title, '结束时间', n.endDate, 'endDate'));
  card.appendChild(buildStructRow(title, '时间范围', n.startDate || n.endDate ? `${(n.startDate || '').replace(/-/g, '.')} - ${(n.endDate || '').replace(/-/g, '.')}` : '', 'dateRange'));
  card.appendChild(buildStructRow(title, '部门', n.department, 'other'));
  card.appendChild(buildStructRow(title, '工作地点', n.city, 'other'));
  card.appendChild(buildStructRow(title, '技术栈', n.techStack, 'other'));

  card.appendChild(el('div', 'rf-struct-section', '工作内容'));
  appendLongText(card, title, '工作职责', n.description, 'responsibility', n.responsibilityVariants);
  appendLongText(card, title, '工作成果', n.highlights, 'responsibility', n.resultVariants);

  card.appendChild(el('div', 'rf-struct-section', '证明人'));
  card.appendChild(buildStructRow(title, '证明人姓名', n.certifierName, 'certifier'));
  card.appendChild(buildStructRow(title, '证明人单位', n.certifierCompany, 'other'));
  card.appendChild(buildStructRow(title, '证明人职务', n.certifierPosition, 'other'));
  card.appendChild(buildStructRow(title, '证明人单位及职务', n.certifierCompanyAndPosition, 'certifier'));
  card.appendChild(buildStructRow(title, '证明人联系电话', n.certifierPhone, 'phone'));
  card.appendChild(buildStructRow(title, '证明人邮箱', n.certifierEmail, 'email'));
  card.appendChild(buildStructRow(title, '证明人与本人关系', n.certifierRelation, 'other'));
  card.appendChild(buildStructRow(title, '备注', n.certifierRemark, 'other'));
  return card;
}

/** 项目 record card：项目基础字段 + 简介/主要工作/成果/合并版（各含字数版本） */
function buildProjectRecordCard(p: any, boundToBlock = false): HTMLElement {
  const title = p.shortName || p.projectName || `项目记录#${p.id ?? ''}`;
  const card = el('div', 'rf-card rf-record-card');
  const head = el('div', 'rf-card-head');
  head.appendChild(el('span', 'rf-card-name', title));
  if (p.startDate || p.endDate) head.appendChild(el('span', 'rf-card-key', `${p.startDate || '?'} ~ ${p.endDate || '至今'}`));
  if (boundToBlock) head.appendChild(el('span', 'rf-fit-tag rf-fit-ok', '当前块绑定'));
  const btnBlock = el('button', 'rf-btn rf-btn-tiny', '填入当前项目经历块');
  btnBlock.addEventListener('click', () => fillRecordToBlock('project', p));
  head.appendChild(btnBlock);
  card.appendChild(head);

  card.appendChild(el('div', 'rf-struct-section', '项目信息'));
  card.appendChild(buildStructRow(title, '项目名称', p.projectName, 'projectName'));
  card.appendChild(buildStructRow(title, '项目角色', p.role, 'other'));
  card.appendChild(buildStructRow(title, '开始时间', p.startDate, 'startDate'));
  card.appendChild(buildStructRow(title, '结束时间', p.endDate, 'endDate'));
  card.appendChild(buildStructRow(title, '项目时间范围', p.startDate || p.endDate ? `${(p.startDate || '').replace(/-/g, '.')} - ${(p.endDate || '').replace(/-/g, '.')}` : '', 'dateRange'));
  card.appendChild(buildStructRow(title, '技术栈', p.techStack, 'other'));

  card.appendChild(el('div', 'rf-struct-section', '项目内容'));
  appendLongText(card, title, '项目简介', p.projectIntro || p.description, 'projectDesc', p.descriptionVariants);
  appendLongText(card, title, '主要工作', p.responsibilities, 'responsibility', p.responsibilityVariants);
  appendLongText(card, title, '项目成果', p.result, 'responsibility', p.resultVariants);
  const combined = [p.description, p.projectIntro, p.responsibilities, p.result].filter((s: any) => s && String(s).trim()).map((s: any) => String(s).trim()).join('\n');
  appendLongText(card, title, '合并版', combined, 'projectDesc', p.combinedVariants);
  return card;
}

/** 实习分类：逐段实习渲染 record card（支持搜索过滤，当前块绑定 record 置顶） */
function renderInternshipRecordCards(cards: HTMLElement): void {
  const list = (cache?.cachedInternships || []).filter((n: any) => !cardSearchKeyword
    || JSON.stringify(n).toLowerCase().includes(cardSearchKeyword));
  const boundId = currentBlockBoundId('internship');
  list.sort((a: any, b: any) => ((b.id === boundId ? 1 : 0) - (a.id === boundId ? 1 : 0)));
  if (list.length === 0) {
    cards.appendChild(el('div', 'rf-empty', '暂无实习记录，请在管理后台“实习经历”新增'));
    return;
  }
  for (const n of list) cards.appendChild(buildInternshipRecordCard(n, n.id === boundId));
}

/** 项目分类：逐个项目渲染 record card + 新增项目记录入口 */
function renderProjectRecordCards(cards: HTMLElement): void {
  const list = (cache?.cachedProjects || []).filter((p: any) => !cardSearchKeyword
    || JSON.stringify(p).toLowerCase().includes(cardSearchKeyword));
  const btnAdd = el('button', 'rf-btn rf-btn-tiny', '+ 新增项目记录（同步到数据库）');
  btnAdd.addEventListener('click', () => addProjectRecordDialog());
  const bar = el('div', 'rf-card-actions');
  bar.appendChild(btnAdd);
  cards.appendChild(bar);
  if (list.length === 0) {
    cards.appendChild(el('div', 'rf-empty', '暂无项目记录，点击上方按钮新增'));
    return;
  }
  const boundProjectId = currentBlockBoundId('project');
  list.sort((a: any, b: any) => ((b.id === boundProjectId ? 1 : 0) - (a.id === boundProjectId ? 1 : 0)));
  for (const p of list) cards.appendChild(buildProjectRecordCard(p, p.id === boundProjectId));
}

/** 插件内新增项目记录：保存到数据库，管理后台同步可见，后续参与自动填充 */
function addProjectRecordDialog(): void {
  if (!shadow) return;
  shadow.querySelectorAll('.rf-dialog-mask').forEach((n) => n.remove());
  const mask = el('div', 'rf-dialog-mask');
  const box = el('div', 'rf-dialog rf-dialog-wide');
  box.appendChild(el('div', 'rf-dialog-title', '新增项目经历'));
  const name = el('input', 'rf-edit-input') as HTMLInputElement;
  name.placeholder = '项目名称（必填）';
  const role = el('input', 'rf-edit-input') as HTMLInputElement;
  role.placeholder = '项目角色';
  const start = el('input', 'rf-edit-input') as HTMLInputElement;
  start.placeholder = '开始时间，如 2025-06';
  const end = el('input', 'rf-edit-input') as HTMLInputElement;
  end.placeholder = '结束时间，如 2025-09';
  const tech = el('input', 'rf-edit-input') as HTMLInputElement;
  tech.placeholder = '技术栈';
  const intro = el('textarea', 'rf-edit-area') as HTMLTextAreaElement;
  intro.placeholder = '项目简介/描述';
  const resp = el('textarea', 'rf-edit-area') as HTMLTextAreaElement;
  resp.placeholder = '主要工作/职责';
  const result = el('textarea', 'rf-edit-area') as HTMLTextAreaElement;
  result.placeholder = '项目成果';
  box.append(
    editRow('项目名称', name), editRow('项目角色', role),
    editRow('开始时间', start), editRow('结束时间', end), editRow('技术栈', tech),
    editRow('项目简介', intro), editRow('主要工作', resp), editRow('项目成果', result),
  );
  const actions = el('div', 'rf-card-actions');
  const btnCancel = el('button', 'rf-btn rf-btn-tiny', '取消');
  btnCancel.addEventListener('click', () => mask.remove());
  const btnSave = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '保存并同步');
  btnSave.addEventListener('click', async () => {
    if (!name.value.trim()) {
      setToast('项目名称不能为空', 'warn');
      return;
    }
    try {
      await api('/api/profile/project', 'POST', {
        projectName: name.value.trim(), role: role.value.trim(), shortName: name.value.trim(),
        startDate: start.value.trim(), endDate: end.value.trim(), techStack: tech.value.trim(),
        projectIntro: intro.value.trim(), description: intro.value.trim(),
        responsibilities: resp.value.trim(), result: result.value.trim(),
      });
      mask.remove();
      setToast('已保存项目记录，同步中…', 'success');
      checkSync(false);
    } catch (err: any) {
      setToast(`保存失败：${err?.message || err}`, 'error');
    }
  });
  actions.append(btnCancel, btnSave);
  box.appendChild(actions);
  mask.appendChild(box);
  shadow.appendChild(mask);
}

/**
 * 结构化子字段推荐候选：实习/项目/家庭/紧急联系人的子字段转为带关键词的伪字段，
 * 供当前输入框智能推荐使用（fieldType 优先级高于当前 tab）。
 */
function structuredRecommendCandidates(): Array<CustomFieldItem & { structType?: StructFieldType }> {
  const out: Array<CustomFieldItem & { structType?: StructFieldType }> = [];
  const mk = (cat: string, name: string, value: string | null | undefined, kws: string[], structType: StructFieldType) => {
    const v = value && String(value).trim() ? String(value).trim() : '';
    if (!v) return;
    out.push({
      fieldKey: '', fieldName: name, fieldType: 'text', fieldCategory: cat, fieldValue: v,
      matchKeywords: kws, enabled: true, readOnly: true, structType,
    });
  };
  (cache?.cachedInternships || []).forEach((n: any) => {
    const t = n.shortName || n.company || '实习';
    mk('工作/实习经历', `${t}-单位名称`, n.company, ['单位名称', '公司名称', '实习单位', '工作单位', '单位', '公司', '企业', '雇主'], 'company');
    mk('工作/实习经历', `${t}-职位名称`, n.position, ['职位名称', '岗位名称', '岗位', '职务', '职位'], 'position');
    mk('工作/实习经历', `${t}-开始时间`, n.startDate, ['开始时间', '入职时间', '起始时间', '开始日期'], 'startDate');
    mk('工作/实习经历', `${t}-结束时间`, n.endDate, ['结束时间', '离职时间', '截止时间', '结束日期'], 'endDate');
    if (n.startDate || n.endDate) {
      mk('工作/实习经历', `${t}-时间范围`, `${(n.startDate || '').replace(/-/g, '.')} - ${(n.endDate || '').replace(/-/g, '.')}`, ['时间范围', '实习时间', '任职时间', '起止时间'], 'dateRange');
    }
    mk('工作/实习经历', `${t}-部门`, n.department, ['部门', '所在部门'], 'other');
    mk('工作/实习经历', `${t}-工作职责`, n.description, ['工作职责', '工作内容', '实习内容', '主要职责', '工作描述'], 'responsibility');
    mk('工作/实习经历', `${t}-工作成果`, n.highlights, ['工作成果', '业绩', '实习成果'], 'responsibility');
    mk('工作/实习经历', `${t}-证明人姓名`, n.certifierName, ['证明人', '证明人姓名', '联系人', '推荐人', '主管'], 'certifier');
    mk('工作/实习经历', `${t}-证明人单位及职务`, n.certifierCompanyAndPosition, ['证明人单位', '单位及职务'], 'certifier');
    mk('工作/实习经历', `${t}-证明人联系电话`, n.certifierPhone, ['证明人联系电话', '证明人电话', '联系人电话', '主管电话'], 'phone');
    mk('工作/实习经历', `${t}-证明人邮箱`, n.certifierEmail, ['证明人邮箱'], 'email');
  });
  (cache?.cachedProjects || []).forEach((p: any) => {
    const t = p.shortName || p.projectName || '项目';
    mk('项目经历', `${t}-项目名称`, p.projectName, ['项目名称', '项目名'], 'projectName');
    mk('项目经历', `${t}-项目角色`, p.role, ['项目角色', '担任角色', '角色'], 'other');
    mk('项目经历', `${t}-开始时间`, p.startDate, ['开始时间', '项目开始'], 'startDate');
    mk('项目经历', `${t}-结束时间`, p.endDate, ['结束时间', '项目结束'], 'endDate');
    mk('项目经历', `${t}-技术栈`, p.techStack, ['技术栈', '使用技术', '技术'], 'other');
    mk('项目经历', `${t}-项目描述`, p.projectIntro || p.description, ['项目描述', '项目简介', '项目介绍'], 'projectDesc');
    mk('项目经历', `${t}-主要工作`, p.responsibilities, ['主要工作', '项目职责', '工作内容', '主要职责'], 'responsibility');
    mk('项目经历', `${t}-项目成果`, p.result, ['项目成果', '项目业绩'], 'responsibility');
  });
  (cache?.cachedFamilyList || []).forEach((m: any) => {
    const t = m.relation || '家庭成员';
    mk('家庭成员', `${t}-姓名`, m.name, ['姓名'], 'personName');
    mk('家庭成员', `${t}-单位`, m.company, ['单位', '工作单位'], 'company');
    mk('家庭成员', `${t}-职务`, m.position, ['职务'], 'position');
    mk('家庭成员', `${t}-联系电话`, m.phone, ['联系电话', '电话', '手机'], 'phone');
  });
  (cache?.cachedEmergencyContactList || []).forEach((c: any) => {
    mk('紧急联系人', '紧急联系人-姓名', c.name, ['紧急联系人', '姓名'], 'personName');
    mk('紧急联系人', '紧急联系人-电话', c.phone, ['联系电话', '电话', '手机'], 'phone');
  });
  return out;
}

/** 识别当前输入框的字段类型（label/nearbyText 为主，name/id 为辅） */
function detectTargetFieldType(info: FieldInfo): StructFieldType | null {
  const strong = [info.label, info.questionText, info.nearbyText, info.ariaLabel, info.placeholder, info.sectionTitle]
    .filter(Boolean).join(' ');
  const weak = `${info.name || ''} ${info.id || ''}`.toLowerCase();
  const text = `${strong} ${weak}`;
  const noCertifier = !/证明人|联系人|推荐人|主管/.test(text);
  if (/证明人|联系人|推荐人|主管/.test(text)) return 'certifier';
  if (/单位|公司|企业|雇主|company/.test(text) && noCertifier) return 'company';
  if (/职位|岗位|职务|position|jobtitle/.test(text)) return 'position';
  if (/开始时间|入职时间|起始|开始日期|startdate/.test(text)) return 'startDate';
  if (/结束时间|离职时间|截止|结束日期|enddate/.test(text)) return 'endDate';
  if (/项目名称/.test(text)) return 'projectName';
  if (/项目描述|项目简介|项目介绍/.test(text)) return 'projectDesc';
  if (/职责|工作内容|实习内容|主要工作/.test(text)) return 'responsibility';
  if (/姓名/.test(text)) return 'personName';
  if (/邮箱|email/i.test(text)) return 'email';
  if (/手机|电话|联系方式/.test(text)) return 'phone';
  return null;
}

/** 目标字段类型与候选类型的兼容性：短字段类型绝不接受整段描述类候选 */
function structTypeCompatible(targetType: StructFieldType, c: CustomFieldItem & { structType?: StructFieldType }): boolean {
  const COMPAT: Record<string, StructFieldType[]> = {
    company: ['company'],
    position: ['position'],
    startDate: ['startDate', 'dateRange'],
    endDate: ['endDate', 'dateRange'],
    projectName: ['projectName'],
    projectDesc: ['projectDesc', 'responsibility'],
    responsibility: ['responsibility', 'projectDesc'],
    certifier: ['certifier'],
    personName: ['personName'],
    email: ['email'],
    phone: ['phone'],
  };
  if (!c.structType) {
    // 普通自定义字段：短字段类型输入框不推荐长文本，防止整段描述填入单位/职位/日期等字段
    if (['company', 'position', 'startDate', 'endDate', 'projectName', 'personName', 'email', 'phone'].includes(targetType)) {
      return String(c.fieldValue || '').length <= 50;
    }
    return true;
  }
  const allow = COMPAT[targetType];
  return allow ? allow.includes(c.structType) : true;
}

/** 结构化类型的中文标签（适配提示与强制填入确认用） */
const STRUCT_TYPE_LABELS: Record<StructFieldType, string> = {
  company: '单位名称', position: '职位名称', startDate: '开始时间', endDate: '结束时间',
  dateRange: '时间范围', personName: '姓名', email: '邮箱', phone: '手机号',
  responsibility: '职责/描述', projectName: '项目名称', projectDesc: '项目描述',
  certifier: '证明人信息', other: '其他字段',
};

/** 卡片相对当前输入框的适配状态：适合 / 需确认 / 不建议 / 未选中输入框 */
type FitState = 'fit' | 'confirm' | 'discourage' | 'unknown';

/** 当前输入框字段类型（实时识别，用于卡片适配状态与强制填入冲突检查） */
function currentTargetType(): StructFieldType | null {
  const target = getCurrentTarget();
  if (!target) return null;
  const info = scanElement(target);
  return info ? detectTargetFieldType(info) : null;
}

/** 根据字段名/内容推断普通自定义字段的内容类型（适配状态展示用） */
function guessContentStructType(f: CustomFieldItem): StructFieldType | null {
  const name = f.fieldName || '';
  const value = String(f.fieldValue || '');
  if (/单位名称|公司名称|公司|雇主|实习单位/.test(name)) return 'company';
  if (/职位名称|岗位|职务|职位/.test(name)) return 'position';
  if (/开始时间|开始日期|入职时间/.test(name)) return 'startDate';
  if (/结束时间|结束日期|离职时间/.test(name)) return 'endDate';
  if (/项目名称/.test(name)) return 'projectName';
  if (/邮箱/.test(name)) return 'email';
  if (/手机|电话|联系电话/.test(name)) return 'phone';
  if (/姓名/.test(name) && value.length <= 20) return 'personName';
  if (/职责|描述|成果|主要工作|内容/.test(name) && value.length > 50) return 'responsibility';
  return null;
}

/** 候选内容对当前输入框的适配状态：类型完全匹配=适合；相近=需确认；冲突=不建议 */
function candidateFitState(c: CustomFieldItem & { structType?: StructFieldType }): FitState {
  const t = currentTargetType();
  if (!t) return 'unknown';
  const st = c.structType ?? guessContentStructType(c) ?? undefined;
  if (st) {
    if (structTypeCompatible(t, { ...c, structType: st })) return 'fit';
    if (st === 'other' || st === 'dateRange') return 'confirm';
    return 'discourage';
  }
  // 类型不可推断：短字段目标 + 长文本内容 → 不建议；其余需确认
  const SHORT: StructFieldType[] = ['company', 'position', 'startDate', 'endDate', 'projectName', 'personName', 'email', 'phone'];
  if (SHORT.includes(t) && String(c.fieldValue || '').length > 50) return 'discourage';
  return 'confirm';
}

/** 适配状态标签文案与样式 */
const FIT_TAG: Record<FitState, [string, string]> = {
  fit: ['✓ 适合当前输入框', 'rf-fit-tag rf-fit-ok'],
  confirm: ['可选·需确认', 'rf-fit-tag rf-fit-warn'],
  discourage: ['⚠ 不建议填入', 'rf-fit-tag rf-fit-bad'],
  unknown: ['', ''],
};

/** 渲染分类标签（含 最近使用/收藏内容/开放题素材 页签）+ 当前页签内容卡片 */
function renderCards(): void {
  const tabs = ui['tabs'];
  const cards = ui['cards'];
  if (!tabs || !cards) return;
  const fields: CustomFieldItem[] = [...(cache?.cachedFields || []), ...structuredFieldCards()];
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

  // 搜索框有内容：默认全库搜索（不被当前页签限制），分组展示；页签仅用于浏览筛选，可勾选“仅搜当前分类”
  if (cardSearchKeyword) {
    renderSearchResults(cards, cardSearchKeyword);
    return;
  }

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

  // 结构化 record card：实习/项目分类先展示记录卡片（子字段可单独填入），下方继续追加该分类普通字段卡片
  if (currentCategory === '工作/实习经历') renderInternshipRecordCards(cards);
  if (currentCategory === '项目经历') renderProjectRecordCards(cards);

  // tab 与当前输入框类型冲突提示：当前 tab 只是浏览筛选，不决定最终推荐类型
  const targetTypeHint = currentTargetType();
  const TAB_MISMATCH: Record<string, StructFieldType[]> = {
    '项目经历': ['company', 'position', 'startDate', 'endDate', 'personName', 'email', 'phone'],
    '工作/实习经历': ['projectName'],
  };
  if (targetTypeHint && (TAB_MISMATCH[currentCategory] || []).includes(targetTypeHint)) {
    cards.appendChild(el('div', 'rf-fit-hint',
      `当前输入框需要“${STRUCT_TYPE_LABELS[targetTypeHint]}”，当前页签内容不建议填入，请参考上方推荐区或切换记录子字段。`));
  }

  const matchesSearch = (f: CustomFieldItem) => !cardSearchKeyword
    || (f.fieldName || '').toLowerCase().includes(cardSearchKeyword)
    || (f.fieldValue || '').toLowerCase().includes(cardSearchKeyword)
    || (f.matchKeywords || []).some((k) => k.toLowerCase().includes(cardSearchKeyword));
  const list = fields
    .filter((f) => (f.fieldCategory || '其他') === currentCategory && matchesSearch(f))
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  // 适配状态排序：适合当前输入框的卡片置顶，不建议填入的沉底（当前 tab 仅浏览筛选）
  const fitRank: Record<FitState, number> = { fit: 0, confirm: 1, unknown: 1, discourage: 2 };
  list.sort((a, b) => fitRank[candidateFitState(a)] - fitRank[candidateFitState(b)]);
  if (list.length === 0) {
    cards.appendChild(el('div', 'rf-empty', cardSearchKeyword ? '无匹配搜索结果' : '该分类暂无内容，可点击右上角“+ 新增字段”添加'));
    return;
  }
  for (const f of list) {
    cards.appendChild(buildCard(f));
  }
}

/** 获取统一搜索索引：同步数据版本/字段数量变化时自动重建（只搜本地缓存，不请求后端） */
function getSearchIndex(): SearchEntry[] {
  const hash = `${cache?.currentDataHash || ''}|${cache?.currentProfileVersion || 0}|${(cache?.cachedFields || []).length}`;
  if (!searchIndex || searchIndexHash !== hash) {
    searchIndex = buildSearchIndex(cache);
    searchIndexHash = hash;
  }
  return searchIndex;
}

/** 搜索条目相对当前输入框的适配状态（复用卡片适配逻辑：适合/需确认/不建议） */
function entryFitState(entry: SearchEntry): FitState {
  const pseudo: CustomFieldItem & { structType?: StructFieldType } = {
    fieldKey: entry.fieldKey, fieldName: entry.fieldName, fieldType: 'text',
    fieldCategory: entry.category, fieldValue: entry.content,
    matchKeywords: entry.matchKeywords, enabled: true, readOnly: true,
    structType: entry.structType as StructFieldType,
  };
  return candidateFitState(pseudo);
}

/** 搜索结果填入当前输入框：原字段走既有卡片填入；结构化子字段带类型守卫（冲突时二次确认） */
function fillSearchResult(entry: SearchEntry): void {
  if (entry.kind === 'field' && entry.refField) {
    fillCardToTarget(entry.refField);
    return;
  }
  const name = `${entry.recordName ? entry.recordName + '-' : ''}${entry.fieldName}`;
  fillValueToTarget(entry.content, name, {
    kind: entry.kind === 'material' ? 'material' : 'field',
    refId: entry.materialId ?? entry.fieldId, fillType: 'manual',
    structType: entry.structType as StructFieldType,
  });
}

/**
 * 全库搜索结果：按匹配质量分组（精确/同义词/内容/相关）展示；
 * 联动当前输入框：适配结果置顶、不建议填入的沉底单独分组；默认前 50 条，可加载更多。
 */
function renderSearchResults(cards: HTMLElement, keyword: string): void {
  const results = unifiedSearch(keyword, getSearchIndex(), { onlyCategory: searchTabOnly ? currentCategory : undefined });
  cards.appendChild(el('div', 'rf-search-note',
    searchTabOnly
      ? `在分类「${currentCategory}」中搜索“${keyword}”：共 ${results.length} 条`
      : `全库搜索“${keyword}”：共 ${results.length} 条（未限定当前页签，可勾选“仅搜当前分类”）`));
  if (results.length === 0) {
    renderSearchEmpty(cards, keyword);
    return;
  }

  // 适配状态优先：适合当前输入框的置顶，不建议填入的沉底（仍可强制填入，二次确认）
  const fitRank: Record<FitState, number> = { fit: 0, confirm: 1, unknown: 1, discourage: 2 };
  const ranked = results.map((r) => ({ ...r, fit: entryFitState(r.entry) }))
    .sort((a, b) => fitRank[a.fit] - fitRank[b.fit] || b.score - a.score);
  const okItems = ranked.filter((r) => r.fit !== 'discourage');
  const badItems = ranked.filter((r) => r.fit === 'discourage');

  let shown = 0;
  const tryAppend = (node: HTMLElement): boolean => {
    if (shown >= searchResultLimit) return false;
    cards.appendChild(node);
    shown++;
    return true;
  };
  for (const g of ['exact', 'synonym', 'content', 'related'] as SearchGroup[]) {
    const items = okItems.filter((r) => r.group === g);
    if (items.length === 0) continue;
    cards.appendChild(el('div', 'rf-search-group-title', `${SEARCH_GROUP_LABELS[g]}（${items.length}）`));
    for (const r of items) {
      if (!tryAppend(buildSearchResultCard(r))) break;
    }
  }
  if (badItems.length > 0) {
    cards.appendChild(el('div', 'rf-search-group-title rf-search-group-bad', `不建议填入当前输入框（${badItems.length}，类型冲突）`));
    for (const r of badItems) {
      if (!tryAppend(buildSearchResultCard(r))) break;
    }
  }

  const total = ranked.length;
  if (shown < total) {
    const btnMore = el('button', 'rf-btn rf-btn-small', `加载更多（已显示 ${shown}/${total}）`);
    btnMore.addEventListener('click', () => {
      searchResultLimit += 50;
      renderCards();
    });
    cards.appendChild(btnMore);
  }
}

/** 搜索结果单条：分类 / 记录名·子字段 / 内容预览 / 字数 / 适配标签 + 填入/临时编辑/复制/转到分类 */
function buildSearchResultCard(r: ScoredSearchResult & { fit: FitState }): HTMLElement {
  const entry = r.entry;
  const card = el('div', 'rf-card rf-search-card');
  const head = el('div', 'rf-card-head');
  head.appendChild(el('span', 'rf-card-tag rf-card-tag-cat', entry.category));
  const path = entry.recordName ? `${entry.recordName} · ${entry.fieldName}` : entry.fieldName;
  head.appendChild(el('span', 'rf-card-name', path));
  head.appendChild(el('span', 'rf-card-key', `${entry.content.length} 字`));
  if (r.fit !== 'unknown') head.appendChild(el('span', FIT_TAG[r.fit][1], FIT_TAG[r.fit][0]));
  card.appendChild(head);

  const content = el('div', 'rf-card-content', entry.content);
  content.title = entry.content;
  card.appendChild(content);

  // 调试模式可见：命中原因 / fieldKey / 类型 / 得分（简洁模式隐藏）
  card.appendChild(el('div', 'rf-debug-info',
    `命中：${r.reasons.join('，')} · 分组：${SEARCH_GROUP_LABELS[r.group]} · 得分：${r.score} · key：${entry.fieldKey || '-'} · 类型：${entry.structType}`));

  const actions = el('div', 'rf-card-actions');
  const btnFill = el('button', 'rf-btn rf-btn-tiny rf-btn-primary', '填入当前输入框');
  btnFill.addEventListener('click', () => fillSearchResult(entry));
  const btnTemp = el('button', 'rf-btn rf-btn-tiny', '临时编辑后填入');
  btnTemp.addEventListener('click', () => tempEditDialog({
    fieldKey: entry.fieldKey, fieldName: path, fieldType: 'text', fieldCategory: entry.category,
    fieldValue: entry.content, matchKeywords: entry.matchKeywords, enabled: true, readOnly: true,
  }));
  const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
  btnCopy.addEventListener('click', () => copyText(entry.content));
  const btnJump = el('button', 'rf-btn rf-btn-tiny', `转到「${entry.category}」`);
  btnJump.addEventListener('click', () => {
    currentCategory = entry.category;
    cardSearchKeyword = '';
    const input = ui['card-search'] as HTMLInputElement;
    if (input) input.value = '';
    renderCards();
  });
  actions.append(btnFill, btnTemp, btnCopy, btnJump);
  card.appendChild(actions);
  return card;
}

/** 搜索为空时的引导：建议词 + 新增字段 / 新增素材 / 从当前输入框生成字段 */
function renderSearchEmpty(cards: HTMLElement, keyword: string): void {
  const box = el('div', 'rf-search-empty');
  box.appendChild(el('div', 'rf-search-empty-title', `未找到与“${keyword}”相关的内容`));
  box.appendChild(el('div', 'rf-search-empty-tip', '可尝试搜索：'));
  const chips = el('div', 'rf-chips');
  for (const s of SEARCH_SUGGESTIONS) {
    const chip = el('button', 'rf-chip', s);
    chip.addEventListener('click', () => {
      const input = ui['card-search'] as HTMLInputElement;
      if (input) input.value = s;
      cardSearchKeyword = s.toLowerCase();
      searchResultLimit = 50;
      renderCards();
    });
    chips.appendChild(chip);
  }
  box.appendChild(chips);
  const actions = el('div', 'rf-card-actions');
  const btnNew = el('button', 'rf-btn rf-btn-tiny', '+ 新增字段');
  btnNew.addEventListener('click', () => {
    toggleNewForm();
    ui['new-form']?.scrollIntoView({ block: 'nearest' });
  });
  const btnMaterial = el('button', 'rf-btn rf-btn-tiny', '+ 新增素材（管理后台）');
  btnMaterial.addEventListener('click', () => setToast('开放题素材请在管理后台“开放题素材”页新增，同步后即可搜索', 'info'));
  const btnFromTarget = el('button', 'rf-btn rf-btn-tiny', '从当前输入框生成字段');
  btnFromTarget.addEventListener('click', () => openNewFormFromTarget());
  actions.append(btnNew, btnMaterial, btnFromTarget);
  box.appendChild(actions);
  cards.appendChild(box);
}

/** 从当前输入框生成字段：打开新增表单并预填页面 label 与已有值 */
function openNewFormFromTarget(): void {
  const target = getCurrentTarget();
  const info = target ? scanElement(target) : null;
  if (!info) {
    setToast('请先点击招聘网站中的一个输入框', 'warn');
    return;
  }
  const form = ui['new-form'];
  if (form.style.display === 'none') {
    buildNewForm();
    form.style.display = '';
  }
  const nameInput = form.querySelector('input.rf-edit-input') as HTMLInputElement | null;
  const contentArea = form.querySelector('textarea.rf-edit-area') as HTMLTextAreaElement | null;
  const label = info.label || info.placeholder || info.name || '未命名字段';
  if (nameInput && !nameInput.value) nameInput.value = label;
  const existing = target ? readElementText(target) : '';
  if (contentArea && !contentArea.value && existing) contentArea.value = existing;
  form.scrollIntoView({ block: 'nearest' });
  setToast(`已从当前输入框预填字段名：${label}`, 'info');
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
  // 已移除敏感字段概念：所有字段均按普通字段展示与填充
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
  const btnCopy = el('button', 'rf-btn rf-btn-tiny', '复制');
  btnCopy.addEventListener('click', () => copyText(f.fieldValue || ''));
  if (f.readOnly) {
    // 结构化记录卡片：仅展示与填入，编辑请前往管理后台（空值不参与自动填充）
    actions.append(btnFill, btnTemp, btnCopy, el('span', 'rf-card-key', '结构化字段 · 管理后台编辑'));
  } else {
    const btnEdit = el('button', 'rf-btn rf-btn-tiny', '编辑');
    btnEdit.addEventListener('click', () => openCardEdit(card, f));
    const btnToggle = el('button', 'rf-btn rf-btn-tiny', f.enabled === false ? '启用' : '禁用');
    btnToggle.addEventListener('click', () => doToggleField(f));
    const btnDelete = el('button', 'rf-btn rf-btn-tiny rf-btn-danger', '删除');
    btnDelete.addEventListener('click', () => doDeleteField(f));
    actions.append(btnFill, btnTemp, btnEdit, btnCopy, btnToggle, btnDelete);
  }
  // 适配状态标签：当前输入框适合 / 需确认 / 不建议（未选中输入框时不显示）
  const fit = candidateFitState(f);
  if (fit !== 'unknown') actions.prepend(el('span', FIT_TAG[fit][1], FIT_TAG[fit][0]));
  card.appendChild(actions);
  return card;
}

/** 卡片内容填入当前选中输入框（超字数时弹窗确认后自动缩短） */
function fillCardToTarget(f: CustomFieldItem): void {
  if (f.enabled === false) {
    setToast('该字段已禁用，请先启用', 'warn');
    return;
  }
  if (f.readOnly && (f.fieldValue || '') === '未填写') {
    setToast('该字段暂无内容，需补充：请在管理后台填写，或点“临时编辑后填入”', 'warn');
    return;
  }
  fillValueToTarget(f.fieldValue || '', f.fieldName, {
    kind: 'field', refId: f.id ?? null, fillType: 'manual',
    structType: guessContentStructType(f) ?? undefined,
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

/** 卡片切换为编辑态：可改字段名/内容/关键词/字数档位/是否参与一键填充 */
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

  card.append(
    editRow('字段名称', nameInput),
    editRow('内容正文', contentArea),
    editRow('匹配关键词', kwInput),
    editRow('字数档位', lenSel),
    autoChk.wrap, manualChk.wrap,
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
  }));
  btnCancel.addEventListener('click', () => renderCards());
  actions.append(btnSave, btnCancel);
  card.appendChild(actions);
}

async function saveCardEdit(origin: CustomFieldItem, form: {
  fieldName: string; fieldValue: string; matchKeywords: string;
  lengthType: string; autoFillEnabled: boolean; manualFillEnabled: boolean;
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
    autoChk.wrap, manualChk.wrap,
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
:host {
  --rf-fs: 14px;
  --rf-lh: 1.5;
}
.rf-panel {
  position: fixed;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  color: #1f2329;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: var(--rf-fs, 14px);
  line-height: var(--rf-lh, 1.5);
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
.rf-body { padding: 10px 12px; overflow: auto; display: flex; flex-direction: column; gap: 8px; flex: 1; min-height: 0; }
.rf-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.rf-user { color: #4e5969; }
.rf-badge { font-size: calc(var(--rf-fs, 14px) - 3px); padding: 2px 8px; border-radius: 10px; white-space: nowrap; }
.rf-badge-ok { background: #e8ffea; color: #00b42a; }
.rf-badge-warn { background: #fff7e8; color: #ff7d00; }
.rf-badge-err { background: #ffece8; color: #f53f3f; }
.rf-badge-idle { background: #f2f3f5; color: #86909c; }
.rf-field-row { display: flex; align-items: center; gap: 8px; }
.rf-label { flex: 0 0 58px; color: #4e5969; }
.rf-select {
  flex: 1; min-width: 0; height: 28px; padding: 0 6px;
  border: 1px solid #e5e6eb; border-radius: 6px; background: #fff; color: #1f2329;
  font-size: calc(var(--rf-fs, 14px) - 2px);
}
.rf-select-tiny { flex: 1; height: 24px; font-size: calc(var(--rf-fs, 14px) - 3px); }
.rf-btn {
  border: 1px solid #e5e6eb; background: #fff; color: #1f2329;
  border-radius: 6px; padding: 7px 10px; cursor: pointer; font-size: var(--rf-fs, 14px);
  transition: all 0.15s;
}
.rf-btn:hover { border-color: #409eff; color: #409eff; }
.rf-btn-primary { background: #409eff; border-color: #409eff; color: #fff; }
.rf-btn-primary:hover { background: #3387e0; color: #fff; }
.rf-btn-small { padding: 5px 8px; font-size: 12px; }
.rf-btn-tiny { padding: 2px 8px; font-size: calc(var(--rf-fs, 14px) - 3px); flex: 0 0 auto; }
.rf-quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; }
.rf-subtitle { font-weight: 600; color: #4e5969; margin-top: 2px; }
.rf-sync-info { color: #86909c; font-size: 12px; }
.rf-report { border-top: 1px dashed #e5e6eb; padding-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.rf-report-label { font-weight: 600; color: #4e5969; }
.rf-report-row { display: flex; align-items: center; gap: 6px; }
.rf-report-text { flex: 1; min-width: 0; color: #4e5969; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rf-details summary { cursor: pointer; color: #86909c; font-size: 12px; }
.rf-details-body { white-space: pre-wrap; font-size: calc(var(--rf-fs, 14px) - 3px); color: #86909c; max-height: 160px; overflow-y: auto; }
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
  font-weight: 700; font-size: var(--rf-fs, 14px);
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
  border-radius: 12px; padding: 2px 8px; font-size: calc(var(--rf-fs, 14px) - 3px); cursor: pointer;
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
.rf-card-key { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 3px); }
.rf-card-tag { font-size: calc(var(--rf-fs, 14px) - 4px); padding: 0 6px; border-radius: 8px; }
.rf-card-tag-off { background: #f2f3f5; color: #86909c; }
.rf-card-content {
  font-size: 12px; color: #4e5969; max-height: 72px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical;
  word-break: break-all;
}
.rf-card-keywords { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 3px); word-break: break-all; }
.rf-card-actions { display: flex; flex-wrap: wrap; gap: 4px; }
.rf-btn-danger { color: #f53f3f; }
.rf-btn-danger:hover { border-color: #f53f3f; color: #f53f3f; }
.rf-new-form {
  border: 1px solid #e5e6eb; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; gap: 6px; background: #fafbfc;
}
.rf-login-form {
  border: 1px solid #bedaff; border-radius: 8px; padding: 10px;
  display: flex; flex-direction: column; gap: 6px; background: #f0f7ff;
}
.rf-login-title { font-weight: 600; color: #1f2329; }
.rf-login-msg { font-size: 12px; min-height: 14px; word-break: break-all; }
.rf-login-msg-err { color: #f53f3f; }
.rf-login-msg-ok { color: #00b42a; }
.rf-edit-row { display: flex; flex-direction: column; gap: 2px; }
.rf-edit-label { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 3px); }
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
/* ===== 投递记录采集区 ===== */
.rf-track-box {
  border: 1px solid #e5e6eb; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; gap: 4px; background: #fafbfc;
}
.rf-track-empty { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 2px); line-height: 1.6; }
.rf-track-row { display: flex; gap: 6px; font-size: calc(var(--rf-fs, 14px) - 2px); }
.rf-track-label { flex: 0 0 84px; color: #86909c; flex-shrink: 0; }
.rf-track-value { color: #1f2329; word-break: break-all; }
.rf-track-msg { color: #165dff; background: #f0f7ff; border-radius: 6px; padding: 4px 8px; word-break: break-all; }
.rf-track-edit { display: flex; flex-direction: column; gap: 6px; margin-top: 4px; }
.rf-preview-list { display: flex; flex-direction: column; gap: 6px; max-height: 320px; overflow-y: auto; }
.rf-preview-stats {
  color: #4e5969; font-size: calc(var(--rf-fs, 14px) - 3px); line-height: 1.7;
  background: #f7f8fa; border-radius: 6px; padding: 6px 8px;
}
.rf-preview-group-title {
  font-weight: 600; font-size: 12px; color: #165dff; background: #f0f7ff;
  border-radius: 4px; padding: 3px 8px; margin-top: 4px;
}
.rf-preview-item {
  border: 1px solid #e5e6eb; border-radius: 6px; padding: 6px 8px;
  display: flex; flex-direction: row; align-items: flex-start; gap: 6px; background: #fff;
}
.rf-preview-item input[type="checkbox"] { margin-top: 2px; flex-shrink: 0; }
.rf-preview-item-suspicious { border-color: #f53f3f; background: #fff7f6; }
.rf-preview-meta { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.rf-preview-title { font-weight: 600; font-size: 12px; word-break: break-all; }
.rf-preview-tags { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 3px); word-break: break-all; }
.rf-preview-reason { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 3px); word-break: break-all; }
.rf-preview-content {
  color: #4e5969; font-size: 12px; word-break: break-all;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.rf-record-card { border-color: #bedaff; }
.rf-struct-section { font-weight: 600; font-size: calc(var(--rf-fs, 14px) - 3px); color: #165dff; margin-top: 4px; }
.rf-struct-row {
  display: flex; flex-direction: column; gap: 2px;
  border-top: 1px dashed #e5e6eb; padding-top: 4px;
}
.rf-struct-label { font-size: calc(var(--rf-fs, 14px) - 3px); color: #86909c; }
.rf-struct-value { font-size: 12px; color: #1f2329; word-break: break-all; }
.rf-struct-empty { color: #c9cdd4; }
.rf-fit-tag { font-size: calc(var(--rf-fs, 14px) - 4px); border-radius: 3px; padding: 1px 5px; flex-shrink: 0; }
.rf-fit-ok { background: #e8f7ee; color: #00a854; }
.rf-fit-warn { background: #fff7e6; color: #ff7d00; }
.rf-fit-bad { background: #fff1f0; color: #f53f3f; }
.rf-fit-hint {
  color: #ff7d00; font-size: calc(var(--rf-fs, 14px) - 3px); background: #fff7e6;
  border-radius: 6px; padding: 5px 8px; margin-bottom: 4px;
}
.rf-edit-area-tall { min-height: 140px; }
.rf-edit-counter { color: #86909c; font-size: calc(var(--rf-fs, 14px) - 3px); text-align: right; }
.rf-btn-star { color: #c9cdd4; }
.rf-btn-star-on { color: #ff7d00; border-color: #ffd77a; }
.rf-card-tag-sens { background: #fff1f0; color: #f53f3f; }
/* ===== 边框缩放手柄（panelResizeService，拖动缩放与标题栏拖动互斥） ===== */
.rf-panel.rf-resizing, .rf-panel.rf-resizing * { user-select: none !important; }
.rf-resize-r { position: absolute; top: 0; right: 0; width: 6px; height: 100%; cursor: ew-resize; }
.rf-resize-l { position: absolute; top: 0; left: 0; width: 6px; height: 100%; cursor: ew-resize; }
.rf-resize-b { position: absolute; left: 0; bottom: 0; width: 100%; height: 6px; cursor: ns-resize; }
.rf-resize-t { position: absolute; left: 0; top: 0; width: 100%; height: 4px; cursor: ns-resize; }
.rf-resize-br {
  position: absolute; right: 0; bottom: 0; width: 14px; height: 14px; cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, #c9cdd4 50%); border-bottom-right-radius: 10px;
}
/* ===== 滚动条（仅 Shadow DOM 内部，不影响页面） ===== */
.rf-panel ::-webkit-scrollbar { width: 8px; height: 8px; }
.rf-panel ::-webkit-scrollbar-thumb { background: #c9cdd4; border-radius: 4px; }
.rf-panel ::-webkit-scrollbar-thumb:hover { background: #86909c; }
.rf-panel ::-webkit-scrollbar-track { background: transparent; }
.rf-panel ::-webkit-scrollbar-corner { background: transparent; }
.rf-panel.rf-scroll-always .rf-body { overflow-x: scroll; }
/* ===== 文本换行与溢出 ===== */
.rf-card-content, .rf-struct-value, .rf-target-bar, .rf-toast { overflow-wrap: anywhere; }
/* ===== 全库搜索结果 ===== */
.rf-search-row { display: flex; align-items: center; gap: 8px; }
.rf-search-row .rf-search { flex: 1; min-width: 0; }
.rf-search-note { color: #165dff; background: #f0f7ff; border-radius: 6px; padding: 4px 8px; word-break: break-all; }
.rf-search-group-title {
  font-weight: 600; color: #4e5969; border-left: 3px solid #409eff;
  padding-left: 6px; margin-top: 4px;
}
.rf-search-group-bad { border-left-color: #f53f3f; color: #f53f3f; }
.rf-card-tag-cat { background: #f0f7ff; color: #165dff; }
.rf-search-empty {
  border: 1px dashed #c9cdd4; border-radius: 8px; padding: 10px;
  display: flex; flex-direction: column; gap: 6px;
}
.rf-search-empty-title { font-weight: 600; }
.rf-search-empty-tip { color: #86909c; }
.rf-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.rf-chip {
  border: 1px solid #bedaff; background: #f0f7ff; color: #165dff;
  border-radius: 12px; padding: 2px 10px; cursor: pointer;
}
.rf-chip:hover { background: #d6e8ff; }
/* ===== 显示设置弹窗 ===== */
.rf-settings-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.rf-settings-label { flex: 0 0 72px; color: #4e5969; }
.rf-settings-custom { width: 96px; }
/* ===== 显示模式（简洁/详细/调试） ===== */
.rf-debug-info { display: none; color: #86909c; word-break: break-all; }
.rf-panel.rf-mode-debug .rf-debug-info { display: block; }
.rf-panel.rf-mode-simple .rf-card-keywords,
.rf-panel.rf-mode-simple .rf-card-key,
.rf-panel.rf-mode-simple .rf-target-detail { display: none; }
/* ===== 响应式布局：<420 窄屏 / 420-640 标准 / >=640 宽屏双列 / 高度不足折叠次要区域 ===== */
.rf-panel.rf-narrow .rf-quick-grid { grid-template-columns: repeat(2, 1fr); }
.rf-panel.rf-narrow .rf-card-content { -webkit-line-clamp: 2; max-height: 40px; }
.rf-panel.rf-wide .rf-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; align-items: start; }
.rf-panel.rf-wide .rf-cards > .rf-record-card,
.rf-panel.rf-wide .rf-cards > .rf-search-group-title,
.rf-panel.rf-wide .rf-cards > .rf-search-note,
.rf-panel.rf-wide .rf-cards > .rf-search-empty,
.rf-panel.rf-wide .rf-cards > .rf-fit-hint,
.rf-panel.rf-wide .rf-cards > .rf-empty,
.rf-panel.rf-wide .rf-cards > .rf-btn,
.rf-panel.rf-wide .rf-cards > .rf-card-actions { grid-column: 1 / -1; }
.rf-panel.rf-short .rf-quick-grid { display: none; }
.rf-panel.rf-short .rf-search-row { position: sticky; top: -10px; z-index: 2; background: #fff; padding-top: 2px; }
`;
