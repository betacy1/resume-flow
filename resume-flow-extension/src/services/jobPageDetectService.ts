/**
 * jobPageDetectService：招聘网站页面采集服务
 * 识别当前页面的公司、机构、岗位、投递网址、简历编辑网址、简历修改时间，
 * 供悬浮面板"投递记录"区域展示并写入投递信息表。
 */

export interface JobPageDetectResult {
  /** 当前页面是否疑似招聘网站 */
  isJobSite: boolean;
  companyName: string;
  organizationName: string;
  positionName: string;
  pageUrl: string;
  pageTitle: string;
  domain: string;
  recruitmentUrl: string;
  /** 如果当前页面是简历填写页/网申编辑页，则记录当前 URL */
  resumeEditUrl: string;
  /** 简历修改时间（ISO 字符串），无法确定时为空 */
  resumeModifiedAt: string;
  /** page_text / save_action / detected_time */
  resumeModifiedSource: string;
  /** 识别置信度 0-1 */
  confidenceScore: number;
  /** 识别来源说明 */
  reasons: string[];
}

/** 已知域名 → 公司名称映射（域名包含匹配） */
const DOMAIN_COMPANY_MAP: Array<[string, string]> = [
  ['kuaishou', '快手'],
  ['efunds', '易方达基金'],
  ['nanfund', '南方基金'],
  ['cmbchina', '招商银行'],
  ['cmbnt', '招银网科'],
  ['icbc', '工行'],
  ['ccb', '建行'],
  ['boc', '中国银行'],
  ['abchina', '农行'],
  ['psbc', '邮储银行'],
  ['bankcomm', '交行'],
  ['cebbank', '光大银行'],
  ['cib', '兴业银行'],
  ['citicbank', '中信银行'],
  ['cmbc', '民生银行'],
  ['bjb', '北京银行'],
  ['shopee', 'shopee'],
  ['byd', '比亚迪'],
];

/** 招聘网站域名关键词 */
const JOB_DOMAIN_KEYWORDS = [
  'campus', 'recruit', 'careers', 'career', 'zhaopin', 'job', 'jobs', 'hr', 'talent',
  'join', 'hotjob', 'moka', 'beisen', 'woshixi', 'shixiseng', 'liepin', '51job', 'zhipin',
  'nowcoder', 'offer', 'apply',
];

/** 招聘页面文本特征 */
const JOB_TEXT_KEYWORDS = ['校园招聘', '社会招聘', '网申', '投递', '招聘岗位', '职位列表', '加入我们', '招贤纳士'];

/** 公司名称 label 关键词 */
const COMPANY_LABELS = ['公司名称', '单位名称', '招聘单位', '企业名称', '机构名称', '用人单位'];

/** 岗位名称 label 关键词 */
const POSITION_LABELS = ['岗位名称', '职位名称', '应聘岗位', '申请岗位', '投递岗位', '意向岗位', '申请职位', '岗位', '职位'];

/** 机构/部门 label 关键词 */
const ORG_LABELS = ['所属部门', '招聘部门', '用人部门', '机构', '部门', '中心', '分行', '事业部'];

/** 简历修改时间文本关键词 */
const RESUME_TIME_KEYWORDS = ['最后修改时间', '简历更新时间', '更新时间', '修改于', '保存时间', '最近更新'];

/** 保存/投递动作按钮关键词（点击后记录为简历修改时间 fallback） */
const SAVE_ACTION_KEYWORDS = ['保存', '提交', '投递', '下一步', '确认投递'];

/** 最近一次"保存/投递"动作时间（由点击监听记录） */
let lastSaveActionAt = 0;
let saveWatcherInstalled = false;
let urlWatcherInstalled = false;

/**
 * 安装保存动作监听：点击"保存/提交/投递/下一步"类按钮后记录当前时间，
 * 作为简历修改时间的推断来源（save_action）。
 */
export function installSaveActionWatcher(): void {
  if (saveWatcherInstalled) return;
  saveWatcherInstalled = true;
  document.addEventListener('click', (e) => {
    const target = (e.target as HTMLElement)?.closest?.('button, [role="button"], a, input[type="submit"]');
    if (!target) return;
    const text = (target.textContent || (target as HTMLInputElement).value || '').trim().slice(0, 30);
    if (SAVE_ACTION_KEYWORDS.some((k) => text.includes(k))) {
      lastSaveActionAt = Date.now();
    }
  }, true);
}

/** 安装 SPA 路由变化监听（轮询 location.href，变化时回调） */
export function installUrlWatcher(onChange: (url: string) => void): void {
  if (urlWatcherInstalled) return;
  urlWatcherInstalled = true;
  let lastUrl = location.href;
  window.setInterval(() => {
    if (location.href !== lastUrl) {
      lastUrl = location.href;
      onChange(lastUrl);
    }
  }, 1500);
}

/** 识别当前页面是否为招聘网站 */
export function isJobSite(domain: string, title: string, bodyText: string): boolean {
  const d = domain.toLowerCase();
  if (JOB_DOMAIN_KEYWORDS.some((k) => d.includes(k))) return true;
  if (/招聘|校招|网申|投递|加入我们/.test(title)) return true;
  const sample = bodyText.slice(0, 3000);
  return JOB_TEXT_KEYWORDS.filter((k) => sample.includes(k)).length >= 2;
}

/**
 * 采集当前页面投递信息。
 */
export function detectJobPage(): JobPageDetectResult {
  const reasons: string[] = [];
  const pageUrl = location.href;
  const pageTitle = document.title || '';
  const domain = location.hostname || '';
  const title = pageTitle.trim();
  const bodyText = document.body?.innerText || '';

  const jobSite = isJobSite(domain, title, bodyText);

  // ---- 公司名称 ----
  let companyName = '';
  let companyScore = 0;
  // 1) 域名映射
  for (const [key, name] of DOMAIN_COMPANY_MAP) {
    if (domain.toLowerCase().includes(key)) {
      companyName = name;
      companyScore = 0.5;
      reasons.push(`公司：域名映射 ${domain} → ${name}`);
      break;
    }
  }
  // 2) label 附近的表单值
  if (!companyName) {
    const labeled = findControlValueByLabels(COMPANY_LABELS);
    if (labeled) {
      companyName = labeled;
      companyScore = 0.55;
      reasons.push('公司：页面 label 字段值');
    }
  }
  // 3) 标题解析："岗位名-公司名" / "公司名校园招聘" / "公司名招聘"
  if (!companyName) {
    const fromTitle = extractCompanyFromTitle(title);
    if (fromTitle) {
      companyName = fromTitle;
      companyScore = 0.4;
      reasons.push('公司：页面标题解析');
    }
  }

  // ---- 岗位名称 ----
  let positionName = '';
  let positionScore = 0;
  const labeledPos = findControlValueByLabels(POSITION_LABELS);
  if (labeledPos) {
    positionName = labeledPos;
    positionScore = 0.55;
    reasons.push('岗位：页面 label 字段值');
  }
  if (!positionName) {
    const heading = findPositionHeading();
    if (heading) {
      positionName = heading;
      positionScore = 0.45;
      reasons.push('岗位：页面标题（h1/h2）');
    }
  }
  if (!positionName) {
    const fromTitle = extractPositionFromTitle(title);
    if (fromTitle) {
      positionName = fromTitle;
      positionScore = 0.35;
      reasons.push('岗位：页面标题解析');
    }
  }

  // ---- 机构 / 部门 ----
  let organizationName = '';
  const labeledOrg = findControlValueByLabels(ORG_LABELS);
  if (labeledOrg) {
    organizationName = labeledOrg;
    reasons.push('机构：页面 label 字段值');
  }
  if (!organizationName) {
    const fromText = extractOrgFromText(bodyText);
    if (fromText) {
      organizationName = fromText;
      reasons.push('机构：页面文本解析');
    }
  }

  // ---- 简历编辑网址 ----
  const resumeEditUrl = looksLikeResumeEditPage(pageUrl, bodyText) ? pageUrl : '';
  if (resumeEditUrl) {
    reasons.push('当前页面为简历填写/网申编辑页');
  }

  // ---- 简历修改时间 ----
  let resumeModifiedAt = '';
  let resumeModifiedSource = '';
  const fromPage = extractResumeModifiedTime(bodyText);
  if (fromPage) {
    resumeModifiedAt = fromPage;
    resumeModifiedSource = 'page_text';
    reasons.push('简历修改时间：页面文本');
  } else if (lastSaveActionAt > 0 && Date.now() - lastSaveActionAt < 10 * 60 * 1000) {
    resumeModifiedAt = new Date(lastSaveActionAt).toISOString();
    resumeModifiedSource = 'save_action';
    reasons.push('简历修改时间：保存动作时间');
  }

  // ---- 置信度 ----
  let confidence = 0;
  confidence += Math.min(0.5, companyScore);
  confidence += Math.min(0.35, positionScore * 0.6);
  if (organizationName) confidence += 0.05;
  if (jobSite) confidence += 0.1;
  if (companyName && positionName) confidence += 0.05;
  confidence = Math.min(1, Math.round(confidence * 100) / 100);

  const recruitmentUrl = jobSite ? `${location.protocol}//${domain}/` : '';

  return {
    isJobSite: jobSite,
    companyName,
    organizationName,
    positionName,
    pageUrl,
    pageTitle: title.slice(0, 200),
    domain,
    recruitmentUrl,
    resumeEditUrl,
    resumeModifiedAt,
    resumeModifiedSource,
    confidenceScore: confidence,
    reasons,
  };
}

// ==================== 内部工具 ====================

/** 从标题提取公司名：支持"岗位-公司""公司校园招聘""公司招聘-岗位"等常见格式 */
function extractCompanyFromTitle(title: string): string {
  if (!title) return '';
  const cleaned = title.replace(/[-_|—·]+$/, '').trim();
  // "XXX校园招聘" / "XXX招聘官网"
  const m1 = /^(.{2,20}?)(校园招聘|社会招聘|招聘官网|招聘系统|招聘网|招聘)/.exec(cleaned);
  if (m1) return m1[1].trim();
  // "岗位-公司" 或 "公司-岗位"：取更像公司名的一段
  const parts = cleaned.split(/[-_|—·]/).map((s) => s.trim()).filter(Boolean);
  if (parts.length >= 2) {
    for (const p of parts) {
      if (looksLikeCompany(p)) return p;
    }
  }
  return '';
}

/** 从标题提取岗位名 */
function extractPositionFromTitle(title: string): string {
  if (!title) return '';
  const parts = title.split(/[-_|—·]/).map((s) => s.trim()).filter(Boolean);
  for (const p of parts) {
    if (looksLikePosition(p) && !looksLikeCompany(p)) return p;
  }
  return '';
}

function looksLikeCompany(text: string): boolean {
  if (text.length < 2 || text.length > 30) return false;
  return /(公司|集团|银行|证券|基金|保险|研究院|研究所|中心|总行|总部|科技|电子|信息)/.test(text)
    || /^[A-Za-z][A-Za-z0-9\s]{1,20}$/.test(text);
}

function looksLikePosition(text: string): boolean {
  if (text.length < 2 || text.length > 40) return false;
  return /(工程师|开发|算法|岗位|专员|经理|管培|实习|研究员|分析师|产品|运营|顾问|方向)/.test(text);
}

/** 岗位详情页 h1/h2 识别 */
function findPositionHeading(): string {
  const headings = document.querySelectorAll<HTMLElement>('h1, h2');
  for (const h of Array.from(headings).slice(0, 6)) {
    const text = (h.textContent || '').trim();
    if (text && text.length <= 40 && looksLikePosition(text)) {
      return text;
    }
  }
  return '';
}

/**
 * 查找带指定 label 的表单控件值。
 * 策略：先查 input/select/textarea 的 name/id/placeholder/aria-label；
 * 再查 label 元素与表格单元格附近的控件。
 */
function findControlValueByLabels(labels: string[]): string {
  const controls = document.querySelectorAll<HTMLElement>('input, select, textarea');
  // 1) 控件自身属性命中
  for (const c of Array.from(controls).slice(0, 300)) {
    const attrs = [
      c.getAttribute('name') || '', c.getAttribute('id') || '',
      c.getAttribute('placeholder') || '', c.getAttribute('aria-label') || '',
    ].join(' ');
    for (const label of labels) {
      if (attrs.includes(label)) {
        const value = readControlValue(c);
        if (value) return value;
      }
    }
  }
  // 2) label 文本附近的控件
  const candidates = document.querySelectorAll<HTMLElement>('label, th, td, span, div, p');
  for (const node of Array.from(candidates).slice(0, 2000)) {
    const own = node.childNodes.length === 1 && node.childNodes[0].nodeType === 3
      ? (node.textContent || '').trim() : '';
    if (!own || own.length > 20) continue;
    if (!labels.some((l) => own === l || own.startsWith(l + '：') || own.startsWith(l + ':'))) continue;
    const scope = node.closest('tr, li, form, .form-item, [class*="form"], [class*="row"]') || node.parentElement;
    if (!scope) continue;
    const control = scope.querySelector<HTMLElement>('input, select, textarea');
    if (control) {
      const value = readControlValue(control);
      if (value) return value;
    }
    // 相邻文本（如"岗位名称：后端开发工程师"）
    const sibling = node.nextElementSibling;
    const sibText = (sibling?.textContent || '').trim();
    if (sibText && sibText.length <= 50) return sibText;
  }
  return '';
}

function readControlValue(control: HTMLElement): string {
  const tag = control.tagName.toLowerCase();
  if (tag === 'select') {
    const sel = control as HTMLSelectElement;
    return sel.selectedOptions[0]?.textContent?.trim() || '';
  }
  const value = ((control as HTMLInputElement).value || '').trim();
  if (value && value.length <= 80) return value;
  return '';
}

/** 从页面文本中提取机构/部门（面包屑、表格行） */
function extractOrgFromText(bodyText: string): string {
  const m = /(所属部门|招聘部门|用人部门|部门)[：:]\s*([\u4e00-\u9fa5A-Za-z0-9（）()·\-\s]{2,30})/.exec(bodyText.slice(0, 5000));
  if (m) return m[2].trim();
  return '';
}

/** 当前页面是否为简历填写/网申编辑页 */
function looksLikeResumeEditPage(url: string, bodyText: string): boolean {
  const u = url.toLowerCase();
  if (/(resume|简历|wangshen|网申|apply|application)/.test(u)
    && /(edit|fill|create|write|新增|编辑|填写)/.test(u)) {
    return true;
  }
  const sample = bodyText.slice(0, 3000);
  const hasForm = document.querySelectorAll('input, textarea, select').length >= 5;
  return hasForm && /(填写简历|编辑简历|我的简历|网申信息|申请信息|简历信息)/.test(sample);
}

/** 从页面文本中提取简历修改时间 */
function extractResumeModifiedTime(bodyText: string): string {
  const sample = bodyText.slice(0, 8000);
  for (const kw of RESUME_TIME_KEYWORDS) {
    const idx = sample.indexOf(kw);
    if (idx < 0) continue;
    const window = sample.slice(idx, idx + 60);
    const m = /(\d{4})[-/年.](\d{1,2})[-/月.](\d{1,2})日?(?:\s*(\d{1,2})[:：](\d{2}))?/.exec(window);
    if (m) {
      const pad = (n: string) => n.padStart(2, '0');
      const time = m[4] ? `${pad(m[4])}:${m[5]}` : '00:00';
      return `${m[1]}-${pad(m[2])}-${pad(m[3])}T${time}:00`;
    }
  }
  return '';
}
