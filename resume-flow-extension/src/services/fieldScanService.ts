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

/** 模块标题 → 块类型（长关键词包含匹配，短关键词精确匹配） */
const SECTION_TITLE_MAP: Array<{ type: 'internship' | 'project' | 'language' | 'education' | 'award' | 'family'; titles: string[] }> = [
  {
    type: 'family',
    titles: ['家庭情况', '家庭成员', '家庭信息', '家庭关系', '家庭联系人', '亲属信息', '亲属情况',
      '主要家庭成员', '家庭主要成员', '社会关系', '近亲属信息', '家属信息', '直系亲属',
      'family information', 'family members', 'relatives'],
  },
  {
    type: 'internship',
    titles: ['工作经历', '实习经历', '工作经验', '实习经验', '任职经历', '职业经历', '从业经历',
      '社会实践', '实践经历', '工作履历', '实习履历', '工作背景', '职业背景',
      '实习信息', '工作信息', '工作/实习经历', '工作实习经历', '实习情况',
      'work experience', 'internship experience', 'employment history', 'employment',
      'professional experience', 'career experience', 'internship'],
  },
  {
    type: 'project',
    titles: ['项目经历', '项目经验', '项目实践', '项目介绍', '项目履历', '项目背景', '参与项目',
      '研发项目', '项目成果', '项目情况', '项目信息',
      'project experience', 'projects', 'project'],
  },
  {
    type: 'education',
    titles: ['教育经历', '教育背景', '学习经历', '学历经历', '在校经历', '院校经历', '学校经历',
      '求学经历', '教育信息', '学历信息', '教育情况', '最高学历',
      'education background', 'education experience', 'education', 'academic background',
      'educational experience'],
  },
  {
    type: 'award',
    titles: ['个人荣誉', '荣誉奖项', '获奖经历', '奖励情况', '奖惩情况', '证书奖项', '奖项证书',
      '荣誉证书', '获奖情况', '奖项', '荣誉', 'awards', 'honors', 'rewards', 'award experience'],
  },
  {
    type: 'language',
    titles: ['语言能力', '语言技能', '外语能力', '语言水平', '外语水平', '英语水平', '技能水平',
      'language proficiency', 'language skills', 'language ability', 'languages', 'foreign language'],
  },
];

/** 短关键词精确匹配（避免“添加/项目”等子串误命中） */
const SHORT_TITLE_EXACT = 6;

interface SectionInfo {
  type: 'internship' | 'project' | 'language' | 'education' | 'award' | 'family';
  title: string;
  heading: HTMLElement;
  container: HTMLElement | null;
}

/** 模块内字段块归属：块容器 → 块序号 */
interface BlockRef {
  blockType: string;
  blockIndex: number;
  sectionTitle: string;
}

export function scanFields(): FieldInfo[] {
  const fields: FieldInfo[] = [];
  const seen = new Set<string>();
  const seenEls = new WeakSet<Element>();
  const selectors = ['input', 'textarea', 'select', RICH_EDITORS_SELECTOR];
  const sections = detectSections();
  const blockRefs = buildBlockRefs(sections);

  document.querySelectorAll(selectors.join(',')).forEach((node) => {
    const el = normalizeEditableElement(node as HTMLElement);
    if (!el || seenEls.has(el)) return;
    seenEls.add(el);
    const info = extractFieldInfo(el);
    if (!info || seen.has(info.fieldId)) return;
    // 隐藏 / 禁用 / 只读字段默认跳过，避免虚拟 DOM 镜像输入框重复入预览
    if (!info.visible || info.disabled || (el as HTMLInputElement).readOnly) return;
    const block = blockRefs.get(el);
    if (block) {
      info.blockType = block.blockType;
      info.blockIndex = block.blockIndex;
      info.sectionTitle = block.sectionTitle;
    }
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

/** 统计页面上指定类型经历块数量（与后端经历计划比对，判断是否需新增） */
export function countBlocks(blockType: 'internship' | 'project' | 'language' | 'education' | 'award' | 'family'): number {
  const sections = detectSections().filter((s) => s.type === blockType);
  let total = 0;
  for (const section of sections) {
    total += Math.max(1, collectBlockContainers(section).length);
  }
  return total;
}

/** 查找指定类型模块的容器元素（添加按钮安全点击策略：优先点击目标模块内的按钮） */
export function findSectionContainer(blockType: string): HTMLElement | null {
  const section = detectSections().find((s) => s.type === blockType);
  return section?.container || null;
}

/** 识别页面中的经历/项目/语言模块标题 */
function detectSections(): SectionInfo[] {
  const sections: SectionInfo[] = [];
  const headingSelector = 'h1,h2,h3,h4,h5,h6,legend,.ant-divider-inner-text,.el-divider__text,[class*="title" i],[class*="header" i],[class*="tab" i],div,span';
  document.querySelectorAll<HTMLElement>(headingSelector).forEach((node) => {
    const text = (node.textContent || '').replace(/\s+/g, ' ').trim();
    if (!text || text.length > 24) return;
    // 标题元素自身不应包含输入控件（避免误把整个表单容器当标题）
    if (node.querySelector('input,textarea,select,[contenteditable="true"]')) return;
    const lower = text.toLowerCase().replace(/[*:：·•]/g, '');
    for (const entry of SECTION_TITLE_MAP) {
      for (const title of entry.titles) {
        // 短中文标题用前缀匹配（不会误命中“添加工作经历”按钮）；英文标题用包含匹配
        const hit = title.length <= SHORT_TITLE_EXACT && !/[a-z]/i.test(title)
          ? lower.startsWith(title)
          : lower.includes(title);
        if (!hit) continue;
        // 同一标题文本只保留最内层节点，避免父子重复
        const dup = sections.find((s) => s.type === entry.type && s.title === text);
        if (dup && (dup.heading.contains(node) || node.contains(dup.heading))) {
          if (node.contains(dup.heading)) {
            dup.heading = node;
            dup.container = resolveSectionContainer(node);
          }
          return;
        }
        sections.push({ type: entry.type, title: text, heading: node, container: resolveSectionContainer(node) });
        return;
      }
    }
  });
  return sections;
}

/** 从标题向上找包含输入控件的最近祖先，作为模块容器 */
function resolveSectionContainer(heading: HTMLElement): HTMLElement | null {
  let current: HTMLElement | null = heading.parentElement;
  for (let i = 0; i < 8 && current && current !== document.body; i++) {
    if (current.querySelector('input:not([type="hidden"]),textarea,select,[contenteditable="true"]')) {
      return current;
    }
    current = current.parentElement;
  }
  return heading.parentElement;
}

const FIELD_SELECTOR = 'input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="file"]),textarea,select,[contenteditable="true"]';

/** 模块内找重复块容器：优先取含 2 个以上同构兄弟的祖先 */
function collectBlockContainers(section: SectionInfo): HTMLElement[] {
  const container = section.container;
  if (!container) return [];
  const fieldEls = Array.from(container.querySelectorAll<HTMLElement>(FIELD_SELECTOR))
    .filter((f) => !section.heading.contains(f) && isVisible(f));
  if (fieldEls.length === 0) return [];
  // 向上找“含同构兄弟”的最近祖先作为块容器（从浅到深取第一个命中）
  for (const el of fieldEls) {
    let current: HTMLElement | null = el;
    let found: HTMLElement | null = null;
    while (current && current !== container) {
      const node: HTMLElement = current;
      const parent: HTMLElement | null = node.parentElement;
      if (!parent) break;
      const withFields = Array.from(parent.children).filter(
        (c: Element) => c.tagName === node.tagName && (c === node || (c as HTMLElement).querySelector(FIELD_SELECTOR)),
      );
      if (withFields.length >= 2) {
        found = node;
        break;
      }
      current = parent;
    }
    if (found) {
      // 统一收集该层级的全部块容器（按 DOM 顺序）
      const holder: HTMLElement = found;
      const parent: HTMLElement = holder.parentElement!;
      return Array.from(parent.children).filter(
        (c: Element) => c.tagName === holder.tagName && (c as HTMLElement).querySelector(FIELD_SELECTOR),
      ) as HTMLElement[];
    }
  }
  // 无重复结构：整个模块视为单个块（语言模块等）
  return [];
}

/** 为每个字段元素计算块归属 */
function buildBlockRefs(sections: SectionInfo[]): Map<HTMLElement, BlockRef> {
  const refs = new Map<HTMLElement, BlockRef>();
  for (const section of sections) {
    const container = section.container;
    if (!container) continue;
    const blocks = collectBlockContainers(section);
    const fieldEls = Array.from(container.querySelectorAll<HTMLElement>(FIELD_SELECTOR))
      .filter((f) => !section.heading.contains(f));
    if (blocks.length > 0) {
      fieldEls.forEach((el) => {
        const idx = blocks.findIndex((b) => b.contains(el));
        if (idx >= 0) {
          refs.set(el, { blockType: section.type, blockIndex: idx, sectionTitle: section.title });
        }
      });
    } else {
      fieldEls.forEach((el) => {
        refs.set(el, { blockType: section.type, blockIndex: 0, sectionTitle: section.title });
      });
    }
  }
  return refs;
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
    /(?:不超过|最多|限制在|限|请输入)\s*(\d{2,4})\s*字/g,
    /(\d{2,4})\s*字(?:以内|内|左右|以下)/g,
    /\/\s*(\d{2,4})(?!\d)/g,
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
