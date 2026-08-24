/**
 * 统一搜索服务（内容库全库搜索）
 * 基于 /api/sync/full 的本地缓存构建搜索索引（纯本地，搜索不发后端请求），
 * 搜索范围覆盖：字段名/fieldKey/分类/内容/匹配关键词/别名/模板类型/字数版本，
 * 实习（公司/职位/起止时间/证明人…）、项目（名称/技术栈/简介/主要工作/成果）、
 * 教育（学校/专业）、家庭成员、紧急联系人、基础信息、荣誉奖项、技能与素材。
 * 支持同义词扩展（公司/单位/岗位/时间/证明人/电话/邮箱/学校/项目/语言…），
 * 结果按匹配质量分组：精确匹配 / 同义词匹配 / 内容匹配 / 相关字段。
 */

import type { SyncCache } from './syncCacheService';

/** 搜索分组（按匹配质量） */
export type SearchGroup = 'exact' | 'synonym' | 'content' | 'related';

/** 统一搜索索引条目：一个可填子字段 / 字段 / 素材 */
export interface SearchEntry {
  key: string;
  /** 所属分类（实习经历/项目经历/家庭成员/基础信息/荣誉奖项/开放题素材…） */
  category: string;
  /** 记录名（京东实习 / Cloud IoT / 父亲…） */
  recordName: string;
  /** 子字段名称（单位名称 / 开始时间 / 证明人联系电话…） */
  fieldName: string;
  /** 内容 */
  content: string;
  /** 结构化类型（与面板 StructFieldType 取值一致，联动当前输入框适配状态） */
  structType: string;
  fieldKey: string;
  matchKeywords: string[];
  aliases: string[];
  /** 原自定义字段 id / 素材 id（无则 null） */
  fieldId: number | null;
  materialId: number | null;
  kind: 'field' | 'material' | 'structured';
  templateType: string;
  lengthType: string;
  sortOrder: number;
  /** 原字段对象引用（填入/编辑复用面板既有逻辑） */
  refField: any | null;
  refMaterial: any | null;
  /** 预计算的小写检索文本 */
  _name: string;
  _meta: string;
  _content: string;
}

export interface ScoredSearchResult {
  entry: SearchEntry;
  group: SearchGroup;
  score: number;
  /** 命中原因（字段名命中 / 同义词“公司” / 内容命中…） */
  reasons: string[];
}

// ==================== 同义词扩展 ====================

/** 同义词组：组内任意词命中即整组扩展（双向包含匹配） */
const SYNONYM_GROUPS: string[][] = [
  ['公司', '单位', '企业', '单位名称', '公司名称', '企业名称', '工作单位', '实习单位', '任职单位',
    '所在公司', '雇主', '父亲单位', '母亲单位', '证明人单位', '单位及职务', 'companyname', 'company', 'employer', 'organization'],
  ['职位', '岗位', '职务', '职位名称', '岗位名称', '工作岗位', '实习岗位', '项目角色', '证明人职务',
    'positionname', 'position', 'jobtitle', 'role'],
  ['时间', '日期', '开始时间', '结束时间', '入职时间', '离职时间', '项目时间', '时间范围', '起止时间',
    '开始日期', '结束日期', '毕业时间', '获奖时间', '申请时间', 'startdate', 'enddate', 'daterange', 'date'],
  ['证明人', '证明人姓名', '证明人单位', '证明人职务', '证明人单位及职务', '证明人联系电话', '证明人邮箱',
    '联系人', '推荐人', '主管', '紧急联系人', 'certifier', 'reference', 'referee'],
  ['电话', '手机', '手机号', '联系电话', '紧急联系电话', '证明人联系电话', '父亲联系电话', '母亲联系电话',
    'phone', 'mobile', 'tel'],
  ['邮箱', '电子邮箱', '备用邮箱', '证明人邮箱', 'email', 'mail', 'e-mail'],
  ['学校', '学校名称', '毕业院校', '院校', '大学', '中学', 'school', 'university', 'college'],
  ['项目', '项目名称', '项目简介', '项目描述', '项目角色', '技术栈', '主要工作', '项目成果', 'project', 'projectname'],
  ['语言', '英语', '语言能力', '语言类型', 'cet', '四六级', '听说', '读写', '掌握程度', '证书', '成绩', '分数'],
  ['职责', '工作职责', '工作内容', '工作描述', '主要职责', '工作职责内容', 'responsibility'],
  ['姓名', '名字', '本人姓名', 'name'],
  ['专业', '专业名称', 'major'],
  ['荣誉', '奖项', '荣誉奖项', '获奖', 'award'],
  ['部门', '所在部门', 'department'],
  ['地点', '工作地点', '城市', '所在地', 'city', 'location'],
  ['简介', '自我介绍', '项目简介', '描述', 'description', 'intro'],
];

/** 查询词同义词扩展：返回去重后的小写扩展词列表（含原词） */
export function expandQuery(query: string): string[] {
  const q = query.trim().toLowerCase();
  const set = new Set<string>([q]);
  if (!q) return [];
  for (const group of SYNONYM_GROUPS) {
    const hit = group.some((t) => t.includes(q) || q.includes(t));
    if (hit) group.forEach((t) => set.add(t));
  }
  return Array.from(set);
}

// ==================== 索引构建 ====================

function mkEntry(partial: Partial<SearchEntry> & Pick<SearchEntry, 'key' | 'category' | 'recordName' | 'fieldName' | 'content'>): SearchEntry {
  const e: SearchEntry = {
    structType: 'other', fieldKey: '', matchKeywords: [], aliases: [],
    fieldId: null, materialId: null, kind: 'structured', templateType: '', lengthType: '',
    sortOrder: 0, refField: null, refMaterial: null,
    _name: '', _meta: '', _content: '',
    ...partial,
  };
  e.content = String(e.content || '').trim();
  e._name = `${e.category} ${e.recordName} ${e.fieldName} ${e.fieldKey} ${e.templateType} ${e.lengthType}`.toLowerCase();
  e._meta = [...e.matchKeywords, ...e.aliases].join(' ').toLowerCase();
  e._content = e.content.toLowerCase();
  return e;
}

/** 有值才产出条目（空字段不进索引） */
function pushIf(entries: SearchEntry[], category: string, recordName: string, fieldName: string,
  value: string | null | undefined, structType: string, extra?: Partial<SearchEntry>): void {
  const v = value && String(value).trim() ? String(value).trim() : '';
  if (!v) return;
  entries.push(mkEntry({
    key: `${category}|${recordName}|${fieldName}`, category, recordName, fieldName, content: v, structType, ...extra,
  }));
}

/** 基础信息 key → 中文标签 / 结构化类型 */
const BASIC_INFO_LABELS: Record<string, [string, string]> = {
  name: ['姓名', 'personName'], gender: ['性别', 'other'], phone: ['手机号', 'phone'], email: ['邮箱', 'email'],
  qq: ['QQ', 'other'], wechat: ['微信号', 'other'], currentLocation: ['当前所在地', 'other'],
  politicalStatus: ['政治面貌', 'other'], idCard: ['身份证号', 'other'],
  emergencyContact: ['紧急联系人姓名', 'personName'], emergencyPhone: ['紧急联系电话', 'phone'],
  referencePhone: ['证明人联系电话', 'phone'], bankCard: ['银行卡号', 'other'],
  applicantType: ['应聘者类型', 'other'], targetPosition: ['目标职位', 'position'], targetCity: ['目标城市', 'other'],
  acceptOtherCity: ['是否接受调剂', 'other'], school: ['学校', 'other'], major: ['专业', 'other'],
  degree: ['学历', 'other'], graduationDate: ['毕业时间', 'endDate'],
  expectedCity: ['期望城市', 'other'], expectedPosition: ['期望职位', 'position'],
  selfIntroduction: ['自我介绍', 'responsibility'],
};

/**
 * 基于本地同步缓存构建全库搜索索引（同步完成后调用；字段变更后重建）。
 * 只搜本地缓存，不请求后端。
 */
export function buildSearchIndex(cache: SyncCache | null): SearchEntry[] {
  const entries: SearchEntry[] = [];
  if (!cache) return entries;

  // 1. 自定义字段（字段名 / key / 分类 / 内容 / 关键词 / 别名 / 模板类型 / 字数版本）
  (cache.cachedFields || []).forEach((f: any) => {
    const v = String(f.fieldValue || '').trim();
    if (!v) return;
    entries.push(mkEntry({
      key: `field|${f.id ?? f.fieldKey}`, category: f.fieldCategory || '其他', recordName: '',
      fieldName: f.fieldName || '', content: v, fieldKey: f.fieldKey || '',
      matchKeywords: f.matchKeywords || [], aliases: f.aliases || [],
      fieldId: f.id ?? null, kind: 'field', templateType: f.templateType || '',
      lengthType: f.lengthType || '', sortOrder: f.sortOrder ?? 0, refField: f,
    }));
  });

  // 2. 实习经历结构化子字段
  (cache.cachedInternships || []).forEach((n: any) => {
    const t = `${n.shortName || n.company || '实习'}实习`;
    const kw = (extra: string[]) => ({ matchKeywords: extra });
    pushIf(entries, '工作/实习经历', t, '单位名称', n.companyName || n.company, 'company',
      kw(['单位', '公司', '企业名称', '工作单位', '实习单位', '雇主']));
    pushIf(entries, '工作/实习经历', t, '职位名称', n.positionName || n.position, 'position',
      kw(['职位', '岗位', '职务', '岗位名称']));
    pushIf(entries, '工作/实习经历', t, '岗位头衔', n.jobTitle, 'position', kw(['岗位', '头衔']));
    pushIf(entries, '工作/实习经历', t, '开始时间', n.startDate, 'startDate', kw(['开始时间', '入职时间']));
    pushIf(entries, '工作/实习经历', t, '结束时间', n.endDate, 'endDate', kw(['结束时间', '离职时间']));
    pushIf(entries, '工作/实习经历', t, '时间范围', n.dateRange, 'dateRange', kw(['时间范围', '起止时间', '实习时间']));
    pushIf(entries, '工作/实习经历', t, '部门', n.department, 'other', kw(['部门']));
    pushIf(entries, '工作/实习经历', t, '工作地点', n.city, 'other', kw(['工作地点', '城市']));
    pushIf(entries, '工作/实习经历', t, '技术栈', n.techStack, 'other', kw(['技术栈', '技术']));
    pushIf(entries, '工作/实习经历', t, '工作职责', n.description, 'responsibility', kw(['工作职责', '工作内容', '实习内容']));
    pushIf(entries, '工作/实习经历', t, '工作成果', n.highlights, 'responsibility', kw(['工作成果', '业绩']));
    pushIf(entries, '工作/实习经历', t, '证明人姓名', n.certifierName, 'certifier', kw(['证明人', '证明人姓名', '联系人', '推荐人', '主管']));
    pushIf(entries, '工作/实习经历', t, '证明人单位', n.certifierCompany, 'certifier', kw(['证明人单位']));
    pushIf(entries, '工作/实习经历', t, '证明人职务', n.certifierPosition, 'certifier', kw(['证明人职务']));
    pushIf(entries, '工作/实习经历', t, '证明人单位及职务', n.certifierCompanyAndPosition, 'certifier', kw(['证明人单位及职务', '单位及职务']));
    pushIf(entries, '工作/实习经历', t, '证明人联系电话', n.certifierPhone, 'phone', kw(['证明人联系电话', '证明人电话']));
    pushIf(entries, '工作/实习经历', t, '证明人邮箱', n.certifierEmail, 'email', kw(['证明人邮箱']));
  });

  // 3. 项目经历结构化子字段
  (cache.cachedProjects || []).forEach((p: any) => {
    const t = p.shortName || p.projectName || '项目';
    const kw = (extra: string[]) => ({ matchKeywords: extra });
    pushIf(entries, '项目经历', t, '项目名称', p.projectName, 'projectName', kw(['项目名称', '项目名']));
    pushIf(entries, '项目经历', t, '项目角色', p.role, 'other', kw(['项目角色', '担任角色']));
    pushIf(entries, '项目经历', t, '开始时间', p.startDate, 'startDate', kw(['开始时间', '项目开始']));
    pushIf(entries, '项目经历', t, '结束时间', p.endDate, 'endDate', kw(['结束时间', '项目结束']));
    pushIf(entries, '项目经历', t, '时间范围', p.dateRange, 'dateRange', kw(['项目时间', '时间范围']));
    pushIf(entries, '项目经历', t, '技术栈', p.techStack, 'other', kw(['技术栈', '使用技术']));
    pushIf(entries, '项目经历', t, '项目简介', p.projectIntro || p.description, 'projectDesc', kw(['项目简介', '项目描述', '项目介绍']));
    pushIf(entries, '项目经历', t, '主要工作', p.responsibilities, 'responsibility', kw(['主要工作', '项目职责']));
    pushIf(entries, '项目经历', t, '项目成果', p.result, 'responsibility', kw(['项目成果', '项目业绩']));
  });

  // 4. 教育经历（学校 / 专业 / 学历 / 起止时间）
  (cache.cachedEducation || []).forEach((e: any) => {
    const t = e.school || '教育经历';
    pushIf(entries, '教育经历', t, '学校', e.school, 'other', { matchKeywords: ['学校', '毕业院校', '院校'] });
    pushIf(entries, '教育经历', t, '专业', e.major, 'other', { matchKeywords: ['专业'] });
    pushIf(entries, '教育经历', t, '学历', e.degree, 'other', { matchKeywords: ['学历', '学位'] });
    pushIf(entries, '教育经历', t, '开始时间', e.startDate, 'startDate', { matchKeywords: ['教育经历开始日期', '入学时间'] });
    pushIf(entries, '教育经历', t, '结束时间', e.endDate, 'endDate', { matchKeywords: ['教育经历结束日期', '毕业时间'] });
  });

  // 5. 家庭成员（姓名 / 单位 / 职务 / 电话 / 邮箱）
  (cache.cachedFamilyList || []).forEach((m: any) => {
    const t = m.relation || '家庭成员';
    pushIf(entries, '家庭成员', t, '姓名', m.name, 'personName', { matchKeywords: ['姓名'] });
    pushIf(entries, '家庭成员', t, '单位', m.company, 'company', { matchKeywords: ['单位', '工作单位', '父亲单位', '母亲单位'] });
    pushIf(entries, '家庭成员', t, '职务', m.position, 'position', { matchKeywords: ['职务', '工作岗位'] });
    pushIf(entries, '家庭成员', t, '联系电话', m.phone, 'phone', { matchKeywords: ['联系电话', '父亲联系电话', '母亲联系电话'] });
    pushIf(entries, '家庭成员', t, '邮箱', m.email, 'email', { matchKeywords: ['邮箱'] });
    pushIf(entries, '家庭成员', t, '地址', m.address, 'other', { matchKeywords: ['地址', '住址'] });
  });

  // 6. 紧急联系人（姓名 / 关系 / 电话 / 单位 / 职务）
  (cache.cachedEmergencyContactList || []).forEach((c: any) => {
    const t = c.name || '紧急联系人';
    pushIf(entries, '紧急联系人', t, '姓名', c.name, 'personName', { matchKeywords: ['紧急联系人', '姓名'] });
    pushIf(entries, '紧急联系人', t, '与本人关系', c.relation, 'other', { matchKeywords: ['关系'] });
    pushIf(entries, '紧急联系人', t, '联系电话', c.phone, 'phone', { matchKeywords: ['紧急联系电话', '电话'] });
    pushIf(entries, '紧急联系人', t, '单位', c.company, 'company', { matchKeywords: ['单位'] });
    pushIf(entries, '紧急联系人', t, '职务', c.position, 'position', { matchKeywords: ['职务'] });
  });

  // 7. 基础信息（姓名 / 手机 / 邮箱 / 目标职位…）
  const basic = cache.cachedProfileData || {};
  for (const [k, v] of Object.entries(basic)) {
    const [label, st] = BASIC_INFO_LABELS[k] || [k, 'other'];
    pushIf(entries, '基础信息', '', label, v as any, st);
  }

  // 8. 荣誉奖项（名称 / 类别 / 时间 / 级别）
  (cache.cachedAwardList || []).forEach((a: any) => {
    const t = a.awardName || '荣誉奖项';
    pushIf(entries, '荣誉奖项', t, '奖项名称', a.awardName, 'other', { matchKeywords: ['荣誉', '奖项', '获奖'] });
    pushIf(entries, '荣誉奖项', t, '奖项类别', a.awardType, 'other', { matchKeywords: ['奖项类别', '类型'] });
    pushIf(entries, '荣誉奖项', t, '获奖时间', a.awardYear, 'other', { matchKeywords: ['获奖时间', '时间'] });
    pushIf(entries, '荣誉奖项', t, '奖项级别', a.awardLevel, 'other', { matchKeywords: ['级别', '奖项级别'] });
  });

  // 9. 专业技能
  (cache.cachedSkillProfiles || []).forEach((s: any) => {
    pushIf(entries, '专业技能', '', s.skillName || '技能', s.content, 'other', { matchKeywords: ['技能', '专业技能'] });
  });

  // 10. 开放题素材
  (cache.cachedMaterials || []).forEach((m: any) => {
    if (m.enabled === false) return;
    const v = String(m.content || '').trim();
    if (!v) return;
    entries.push(mkEntry({
      key: `material|${m.id}`, category: '开放题素材', recordName: m.materialType || '',
      fieldName: m.title || '素材', content: v, materialId: m.id ?? null,
      kind: 'material', templateType: m.materialType || '', refMaterial: m,
    }));
  });

  // 11. 字数版本变体（lengthType 可检索，如 within_200 / 实习职责）
  (cache.cachedContentVariants || []).forEach((cv: any) => {
    const v = String(cv.content || '').trim();
    if (!v) return;
    const srcLabel = cv.sourceType === 'internship' ? '实习经历' : cv.sourceType === 'project' ? '项目经历' : (cv.sourceType || '其他');
    entries.push(mkEntry({
      key: `variant|${cv.id}`, category: '字数版本', recordName: srcLabel,
      fieldName: `${cv.fieldType || '内容'}·${cv.lengthType || ''}`, content: v,
      structType: /responsibility|result|combined|overview/.test(cv.fieldType || '') ? 'responsibility' : 'other',
      kind: 'structured', templateType: cv.audienceType || '', lengthType: cv.lengthType || '',
    }));
  });

  return entries;
}

// ==================== 搜索与排序 ====================

const GROUP_RANK: Record<SearchGroup, number> = { exact: 0, synonym: 1, content: 2, related: 3 };

/**
 * 全库统一搜索：同义词扩展 + 分组（精确/同义词/内容/相关）+ 打分。
 * onlyCategory 非空时仅搜指定分类（“仅搜索当前分类”开关）。
 */
export function unifiedSearch(query: string, index: SearchEntry[],
  opts?: { onlyCategory?: string }): ScoredSearchResult[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const terms = expandQuery(q);
  const synonyms = terms.filter((t) => t !== q);
  const out: ScoredSearchResult[] = [];

  for (const entry of index) {
    if (opts?.onlyCategory && entry.category !== opts.onlyCategory) continue;
    if (!entry.content) continue;
    let group: SearchGroup | null = null;
    let score = 0;
    const reasons: string[] = [];

    // 精确匹配：字段名 / 记录名 / 分类 / fieldKey 直接包含查询词
    if (entry._name.includes(q)) {
      group = 'exact';
      score = 100 + (entry.fieldName.toLowerCase().includes(q) ? 20 : 0) + Math.min(10, q.length);
      reasons.push('字段名命中');
    }
    // 同义词匹配：扩展词命中字段名/关键词/别名
    if (!group) {
      const hitTerm = synonyms.find((t) => t.length >= 2 && (entry._name.includes(t) || entry._meta.includes(t)));
      if (hitTerm) {
        group = 'synonym';
        score = 70 + Math.min(10, hitTerm.length);
        reasons.push(`同义词“${hitTerm}”`);
      }
    }
    // 内容匹配：正文包含查询词
    if (!group && entry._content.includes(q)) {
      group = 'content';
      score = 50 + Math.min(10, q.length);
      reasons.push('内容命中');
    }
    // 相关字段：同义词仅命中正文
    if (!group) {
      const hitTerm = synonyms.find((t) => t.length >= 2 && entry._content.includes(t));
      if (hitTerm) {
        group = 'related';
        score = 30;
        reasons.push(`内容与“${hitTerm}”相关`);
      }
    }
    if (!group) continue;
    // 关键词/别名命中加权
    if (entry._meta.includes(q)) score += 8;
    out.push({ entry, group, score, reasons });
  }

  out.sort((a, b) => (GROUP_RANK[a.group] - GROUP_RANK[b.group]) || (b.score - a.score)
    || a.entry.sortOrder - b.entry.sortOrder);
  return out;
}

/** 搜索分组标题（结果分组展示用） */
export const SEARCH_GROUP_LABELS: Record<SearchGroup, string> = {
  exact: '精确匹配', synonym: '同义词匹配', content: '内容匹配', related: '相关字段',
};

/** 空结果时的搜索建议词 */
export const SEARCH_SUGGESTIONS = ['单位', '公司', '职位', '时间', '证明人', '电话', '项目', '技能', '学校', '语言'];
