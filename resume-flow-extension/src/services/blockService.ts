import { countBlocks, findSectionContainer } from './fieldScanService';

/** 每类经历块最多新增数量，防止死循环 */
const MAX_ADD_PER_TYPE = 10;
/** 点击新增后等待 DOM 更新的时间（毫秒） */
const WAIT_AFTER_CLICK = 500;

/** 新增按钮文案（长文案包含匹配，短文案精确匹配） */
const ADD_BUTTON_TEXTS = [
  // 工作/实习经历
  '添加工作经历', '新增工作经历', '增加工作经历',
  '添加实习经历', '新增实习经历', '增加实习经历',
  '添加工作经验', '新增工作经验', '添加实习经验', '新增实习经验',
  '添加任职经历', '新增任职经历', '添加职业经历', '新增职业经历',
  '添加社会实践', '新增社会实践',
  // 项目经历
  '添加项目经历', '新增项目经历', '增加项目经历', '添加项目经验', '新增项目经验',
  '添加项目', '新增项目', '添加研发项目', '新增研发项目', '添加经历',
  // 教育经历
  '添加教育经历', '新增教育经历', '增加教育经历', '添加学习经历', '新增学习经历',
  '添加学历经历', '新增学历经历', '添加教育背景', '添加教育', '新增教育',
  // 家庭成员
  '添加家庭成员', '新增家庭成员', '增加家庭成员', '添加家庭信息', '新增家庭信息',
  '添加亲属', '新增亲属', '添加亲属信息', '新增亲属信息', '添加社会关系', '新增社会关系',
  '添加成员', '新增成员',
  // 紧急联系人
  '添加紧急联系人', '新增紧急联系人', '添加紧急联络人', '新增紧急联络人',
  '添加应急联系人', '新增应急联系人',
  // 语言能力
  '添加语言能力', '新增语言能力', '增加语言能力', '添加外语能力', '新增外语能力',
  '添加语言', '新增语言',
  // 荣誉奖项 / 专利成果
  '添加荣誉奖项', '新增荣誉奖项', '增加荣誉奖项', '添加获奖经历', '新增获奖经历',
  '添加奖励', '新增奖励', '添加奖项', '新增奖项', '添加证书', '新增证书',
  '添加奖项', '新增奖项', '添加荣誉', '新增荣誉',
  '添加专利成果', '新增专利成果', '添加科研成果', '新增科研成果',
  '添加论文专利', '新增论文专利', '添加学术成果', '新增学术成果', '添加专利', '新增专利',
  // 校园经历
  '添加校园经历', '新增校园经历', '添加校内经历', '新增校内经历',
  '添加学生工作', '新增学生工作', '添加志愿服务', '新增志愿服务',
  // 通用
  '添加一条', '新增一条', '增加一条', '继续添加', '添加更多', '新增更多',
  '添加记录', '新增记录', '增加记录', '添加信息', '新增信息', '增加信息',
  '添加经历', '新增经历', '增加经历', '添加条目', '新增条目', '增加条目',
  'add work experience', 'add internship experience', 'add employment', 'add professional experience',
  'add project experience', 'add project', 'add internship', 'add experience',
  'add education', 'add education experience', 'add academic background',
  'add family member', 'add relative', 'add emergency contact',
  'add language', 'add language skill', 'add language ability',
  'add award', 'add honor', 'add prize', 'add patent', 'add research output', 'add publication',
  'add campus experience', 'add social practice', 'add volunteer experience',
  'add more', 'add item', 'add record', 'add another', 'create',
];
/** 短文案：仅当按钮文本精确等于它时才命中（避免误点“提交/保存”） */
const ADD_BUTTON_SHORT = [
  '添加', '新增', '增加', '+ 添加', '+添加', '＋ 添加', '＋添加',
  '+ 新增', '+新增', '＋ 新增', '＋新增', '+', '＋', 'add', 'new',
];

/**
 * 危险/无关按钮文案：命中即拒绝点击。
 * 不点删除/移除/清空/取消；不点“添加附件/上传附件/添加简历/添加投递”等无关按钮；不自动提交表单。
 */
const DANGEROUS_TEXTS = [
  '删除', '移除', '清空', '取消', '附件', '上传', '下载', '简历', '投递', '提交', '保存',
  '登录', '注册', '刷新', '关闭', '返回', '退出',
  'delete', 'remove', 'clear', 'cancel', 'upload', 'download', 'submit', 'save', 'close',
];

const CLICKABLE_SELECTOR = 'button, a, [role="button"], [tabindex], .ant-btn, .el-button, [class*="add" i], [class*="plus" i], [class*="create" i], [class*="new" i], [class*="btn" i], span, div';

export interface EnsureBlocksResult {
  added: number;
  current: number;
  failed: boolean;
}

export type RepeatableBlockType = 'internship' | 'project' | 'education' | 'award' | 'family';

/** 各块类型的模块范围关键词（用于把按钮与目标模块关联） */
function scopeKeywordsOf(blockType: RepeatableBlockType): string[] {
  switch (blockType) {
    case 'internship':
      return ['工作', '实习', '经验', '履历', '经历', 'experience', 'internship', 'employment'];
    case 'project':
      return ['项目', 'project'];
    case 'education':
      return ['教育', '学习', '学历', 'education', 'academic'];
    case 'award':
      return ['奖项', '荣誉', '获奖', '奖励', '证书', '专利', 'award', 'honor', 'prize', 'patent'];
    case 'family':
      return ['家庭', '成员', '亲属', '社会关系', 'family', 'relative'];
    default:
      return [];
  }
}

/** 元素是否安全可点击：可见、未禁用、非危险文案 */
function isSafeElement(el: HTMLElement): boolean {
  if (el.hasAttribute('disabled') || el.getAttribute('aria-disabled') === 'true') return false;
  const style = window.getComputedStyle(el);
  if (style.display === 'none' || style.visibility === 'hidden') return false;
  const text = cleanText(el).toLowerCase();
  if (DANGEROUS_TEXTS.some((d) => text.includes(d.toLowerCase()))) return false;
  return true;
}

/**
 * 找到当前页面上指定类型经历模块的“新增”按钮。
 * 识别方式：按钮文本、aria-label、title、className（add/plus/create/new）、纯“+”按钮；
 * 安全策略：优先取目标模块容器内的按钮，拒绝删除/附件/上传/提交类按钮，不点全局无关按钮。
 */
export function findAddButton(blockType: RepeatableBlockType): HTMLElement | null {
  const scopeKeywords = scopeKeywordsOf(blockType);
  const sectionContainer = findSectionContainer(blockType);
  const candidates = Array.from(document.querySelectorAll<HTMLElement>(CLICKABLE_SELECTOR))
    .filter((el) => isAddLikeElement(el));

  // 一轮：目标模块容器内 + 文案明确包含“添加/新增 + 模块关键词”
  if (sectionContainer) {
    for (const el of candidates) {
      if (!sectionContainer.contains(el) || !isSafeElement(el)) continue;
      const lower = clickableText(el).toLowerCase();
      if (hasAddVerb(lower) && scopeKeywords.some((k) => lower.includes(k))) return el;
    }
    // 二轮：模块内纯“+/＋”按钮（图标按钮），模块标题已确定归属
    for (const el of candidates) {
      if (!sectionContainer.contains(el) || !isSafeElement(el)) continue;
      const text = cleanText(el);
      if (text === '+' || text === '＋') return el;
    }
    // 三轮：模块内 aria-label/title 含 add/create/plus/添加/新增 的元素
    for (const el of candidates) {
      if (!sectionContainer.contains(el) || !isSafeElement(el)) continue;
      const attr = ((el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '')).toLowerCase();
      if (/添加|新增|增加|add|create|plus|new/.test(attr)) return el;
    }
  }
  // 四轮（全局兜底）：文案明确包含“添加/新增 + 模块关键词”，且不位于其他类型模块容器内
  for (const el of candidates) {
    if (!isSafeElement(el)) continue;
    const lower = clickableText(el).toLowerCase();
    if (!hasAddVerb(lower)) continue;
    if (!scopeKeywords.some((k) => lower.includes(k))) continue;
    if (belongsOtherSection(el, blockType)) continue;
    return el;
  }
  // 五轮：通用“添加经历”文案（实习类兜底）
  if (blockType === 'internship') {
    for (const el of candidates) {
      if (!isSafeElement(el)) continue;
      const text = cleanText(el).toLowerCase();
      if ((text === '添加经历' || text === '新增经历') && !belongsOtherSection(el, blockType)) return el;
    }
  }
  return null;
}

/** 按钮是否位于其他类型模块的容器内（避免把“添加教育经历”点到工作经历模块） */
function belongsOtherSection(el: HTMLElement, blockType: RepeatableBlockType): boolean {
  const others: RepeatableBlockType[] = (['internship', 'project', 'education', 'award', 'family'] as RepeatableBlockType[])
    .filter((t) => t !== blockType);
  for (const t of others) {
    const container = findSectionContainer(t);
    if (container && container.contains(el)) return true;
  }
  return false;
}

/** 可点击文本：优先可见文本，其次 aria-label/title */
function clickableText(el: HTMLElement): string {
  const text = cleanText(el);
  if (text) return text;
  return `${el.getAttribute('aria-label') || ''} ${el.getAttribute('title') || ''}`.trim();
}

function hasAddVerb(lower: string): boolean {
  return /添加|新增|增加|^[\+＋]\s*|add|create|^new\b/.test(lower);
}

/** 元素是否具备“添加按钮”形态：文本/图标/className/aria 任一命中 */
function isAddLikeElement(el: HTMLElement): boolean {
  // 排除包含输入控件的大容器
  if (el.querySelectorAll('input,textarea,select').length > 0) return false;
  const text = clickableText(el);
  if (!text || text.length > 20) return false;
  const lower = text.toLowerCase();
  const cls = (el.className?.toString?.() || '').toLowerCase();
  const known = ADD_BUTTON_TEXTS.some((t) => lower.includes(t))
    || ADD_BUTTON_SHORT.some((t) => lower === t || lower === `+ ${t}`);
  if (known) return true;
  if (/添加|新增/.test(lower)) return true;
  // 纯图标按钮：className 含 add/plus/create/new 且文本为空或仅为符号
  if (/add|plus|create|new/.test(cls) && /^[\s\+＋]*$/.test(cleanText(el))) return true;
  // svg 加号图标按钮
  if (el.querySelector('svg') && /add|plus|create|new/.test(cls)) return true;
  return false;
}

function cleanText(el: HTMLElement): string {
  return (el.textContent || '').replace(/\s+/g, ' ').trim();
}

/**
 * 确保页面上指定类型的经历块数量达到 needed：
 * 不足时逐个点击“新增”按钮，每次点击后等待 DOM 更新并重新计数；
 * 最多新增 MAX_ADD_PER_TYPE 条；新增失败（按钮不存在或点击无效）时返回 failed，提示用户手动添加。
 */
export async function ensureBlocks(
  blockType: RepeatableBlockType,
  needed: number,
): Promise<EnsureBlocksResult> {
  let current = countBlocks(blockType);
  if (current >= needed) return { added: 0, current, failed: false };

  let added = 0;
  let stagnant = 0;
  let lastBtn: HTMLElement | null = null;
  while (current < needed && added < MAX_ADD_PER_TYPE) {
    const btn = findAddButton(blockType);
    if (!btn) {
      return { added, current, failed: true };
    }
    btn.scrollIntoView({ block: 'center' });
    btn.click();
    await sleep(WAIT_AFTER_CLICK);
    const next = countBlocks(blockType);
    if (next <= current) {
      // 点击后块数未变化：可能是异步渲染，再等一次；连续两次无效判定失败
      await sleep(WAIT_AFTER_CLICK);
      const retry = countBlocks(blockType);
      if (retry <= current) {
        stagnant++;
        // 同一个按钮连续无效：停止并提示手动添加，防止死循环重复点击
        if (stagnant >= 2 || btn === lastBtn) return { added, current, failed: true };
        continue;
      }
      current = retry;
    } else {
      current = next;
      stagnant = 0;
    }
    lastBtn = btn;
    added++;
  }
  return { added, current, failed: current < needed };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
