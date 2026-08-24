import { getAuth, getBackendUrl } from './storageService';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

async function request<T>(path: string, options: RequestInit = {}, baseUrl?: string): Promise<T> {
  const backendUrl = baseUrl || await getBackendUrl();
  const auth = await getAuth();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };

  if (auth?.token) {
    headers.Authorization = `Bearer ${auth.token}`;
  }

  const response = await fetch(`${backendUrl}${path}`, { ...options, headers });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const result: ApiResponse<T> = await response.json();
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败');
  }
  return result.data;
}

export async function login(username: string, password: string, backendUrl: string) {
  return request<{ token: string; userId: number; username: string }>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }, backendUrl);
}

export interface TemplateItem {
  id: number;
  name: string;
  category: string;
  audienceType?: string;
  description?: string;
  isDefault: boolean;
}

export async function getTemplates(): Promise<TemplateItem[]> {
  return request<TemplateItem[]>('/api/templates');
}

export interface MaterialItem {
  id?: number;
  title: string;
  materialType: string;
  content: string;
  shortName?: string;
  templateId?: number;
  wordLimitType?: string;
  enabled?: boolean;
}

export async function getMaterials(templateId?: number): Promise<MaterialItem[]> {
  const query = templateId ? `?templateId=${templateId}` : '';
  return request<MaterialItem[]>(`/api/materials${query}`);
}

export async function createMaterial(data: MaterialItem): Promise<number> {
  return request<number>('/api/materials', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export interface CustomFieldItem {
  id?: number;
  templateId?: number;
  fieldKey: string;
  fieldName: string;
  fieldType: string;
  fieldCategory?: string;
  fieldValue?: string;
  matchKeywords: string[];
  templateIds?: number[];
  lengthType?: string;
  autoFillEnabled?: boolean;
  manualFillEnabled?: boolean;
  version?: number;
  enabled?: boolean;
  sortOrder?: number;
  updateTime?: string;
  /** 结构化记录展开的只读卡片（家庭成员/紧急联系人/证明人），编辑请前往管理后台 */
  readOnly?: boolean;
}

export async function getCustomFields(templateId?: number): Promise<CustomFieldItem[]> {
  const query = templateId ? `?templateId=${templateId}` : '';
  return request<CustomFieldItem[]>(`/api/custom-fields${query}`);
}

export async function updateCustomField(id: number, data: CustomFieldItem): Promise<void> {
  return request<void>(`/api/custom-fields/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

// ==================== 插件字段接口（/api/plugin/fields） ====================

/** 409 冲突：服务端版本更新，携带服务端最新字段 */
export class ConflictError extends Error {
  serverField: CustomFieldItem;
  constructor(message: string, serverField: CustomFieldItem) {
    super(message);
    this.serverField = serverField;
  }
}

/** 字段写操作结果：同步状态 + 字段最新快照 */
export interface PluginFieldWriteResult {
  success: boolean;
  id?: number;
  field?: CustomFieldItem;
  profileVersion: number;
  dataHash: string;
  updatedAt: string;
  /** 内容质量检查提醒（超字数/英文大小写，不阻断保存） */
  warnings?: string[];
}

/** 识别 background 代理透传的 409 冲突体，其余原样返回（面板侧使用） */
export function unwrapOrConflict<T>(data: any): T {
  if (data && data.__rfStatus === 409) {
    throw new ConflictError(data.message || '该内容已在网页端更新', data.data as CustomFieldItem);
  }
  return data as T;
}

export interface FieldInfo {
  fieldId: string;
  tagName: string;
  label: string;
  placeholder: string;
  type: string;
  name: string;
  id: string;
  className: string;
  ariaLabel: string;
  parentText: string;
  questionText: string;
  nearbyText: string;
  wordLimit?: number;
  visible: boolean;
  disabled: boolean;
  /** 所属重复块类型：internship / project / language，无块为 undefined */
  blockType?: string;
  /** 所属块序号（0 起），同一块内字段绑定同一条经历记录 */
  blockIndex?: number;
  /** 所属模块标题（如“工作经历”） */
  sectionTitle?: string;
}

export interface MatchResult {
  fieldId: string;
  matchedFieldKey: string;
  matchedFieldName: string;
  value: string;
  confidence: number;
  reason: string;
  variantDesc?: string;
  /** 绑定的经历记录引用，如 internship:2 / project:5 */
  recordRef?: string;
  /** 绑定记录名称（预览分组展示用） */
  recordName?: string;
  /** 预览分组：work_experience / project_experience / skill / material / education / basic */
  group?: string;
  /** 疑似错误：值类型与字段语义冲突（后端类型校验），默认不勾选 */
  suspicious?: boolean;
  /** 疑似错误原因 */
  suspiciousReason?: string;
}

/** 当前模板应填经历计划项（有序） */
export interface ExperiencePlanItem {
  type: 'internship' | 'project' | string;
  id: number;
  name: string;
  startDate?: string;
  endDate?: string;
}

export interface SkippedField {
  fieldId: string;
  reason: string;
}

export interface UnmatchedField {
  fieldId: string;
  reason: string;
}

export interface AutofillMatchResponse {
  matches: MatchResult[];
  skipped: SkippedField[];
  unmatched: UnmatchedField[];
  /** 当前模板应填经历计划（有序）：插件据此判断需要新增多少个经历块 */
  experiencePlan?: ExperiencePlanItem[];
}

export async function autofillMatch(
  templateId: number | null,
  pageUrl: string,
  pageTitle: string,
  fields: FieldInfo[],
  audienceType?: string,
  jobDirection?: string,
  preferredInternshipId?: number,
): Promise<AutofillMatchResponse> {
  return request<AutofillMatchResponse>('/api/autofill/match', {
    method: 'POST',
    body: JSON.stringify({ templateId, pageUrl, pageTitle, fields, audienceType, jobDirection, preferredInternshipId }),
  });
}

export interface InternshipItem {
  id: number;
  company?: string;
  department?: string;
  position?: string;
  shortName?: string;
}

/** 获取实习经历列表，用于插件端选择优先经历 */
export async function getInternships(): Promise<InternshipItem[]> {
  const profile = await request<{ internshipList?: InternshipItem[] }>('/api/profile');
  return profile?.internshipList || [];
}

export interface TemplateExperienceConfigItem {
  id?: number;
  templateId?: number;
  sourceType?: string;
  sourceId?: number;
  sourceName?: string;
  includedInResume?: boolean;
  autoFillEnabled?: boolean;
  autoFillPriority?: number;
  manualSelectable?: boolean;
  emphasisTags?: string;
  displayOrder?: number;
}

/** 获取模板下的经历配置，用于手选经历时判断是否默认展示 */
export async function getTemplateConfigs(templateId: number): Promise<TemplateExperienceConfigItem[]> {
  return request<TemplateExperienceConfigItem[]>(`/api/template-configs?templateId=${templateId}`);
}

export async function addFieldKeyword(fieldId: number, keyword: string): Promise<void> {
  return request<void>(`/api/custom-fields/${fieldId}/keywords?keyword=${encodeURIComponent(keyword)}`, {
    method: 'POST',
  });
}

// ==================== 同步接口 ====================

export interface SyncStatus {
  profileVersion: number;
  dataHash: string;
  updatedAt: string;
}

/** 服务端数据版本状态（版本号 + 内容哈希 + 更新时间） */
export async function getSyncStatus(): Promise<SyncStatus> {
  return request<SyncStatus>('/api/sync/status');
}

/** 全量同步载荷：基础信息/教育/实习/项目/技能/素材/字段规则/模板配置/内容版本 */
export interface SyncFullPayload {
  profileVersion: number;
  dataHash: string;
  updatedAt: string;
  basicInfo: Record<string, any> | null;
  educationList: any[];
  internshipList: any[];
  projectList: any[];
  skillList: any[];
  awardList: any[];
  /** 家庭成员（父亲/母亲等，含单位/职务/电话） */
  familyList: any[];
  /** 紧急联系人（与家庭成员分别独立维护） */
  emergencyContactList: any[];
  materials: any[];
  customFields: CustomFieldItem[];
  templates: TemplateItem[];
  templateConfigs: TemplateExperienceConfigItem[];
  contentVariants: any[];
}

export async function getSyncFull(): Promise<SyncFullPayload> {
  return request<SyncFullPayload>('/api/sync/full');
}

/** 查询专业技能（七个分组 + 各模板技能内容版本） */
export async function getSkills(): Promise<{
  skillKeys: Record<string, string>;
  skills: Array<{ skillKey: string; skillName: string; content: string; sortOrder?: number }>;
  variants: Array<{ id: number; audienceType: string; fieldType: string; lengthType: string; content: string }>;
}> {
  return request('/api/skills');
}

// ==================== 投递信息表采集接口（/api/application-records） ====================

/** 插件采集投递信息请求体 */
export interface ApplicationCapturePayload {
  companyName?: string;
  organizationName?: string;
  positionName?: string;
  pageUrl?: string;
  pageTitle?: string;
  domain?: string;
  recruitmentUrl?: string;
  resumeEditUrl?: string;
  resumeModifiedAt?: string;
  resumeModifiedSource?: string;
  detectedAt?: string;
  source?: string;
  confidenceScore?: number;
  /** 用户在插件中确认后强制保存（低置信度也入库） */
  confirmed?: boolean;
}

/** 采集结果：created=新增 / updated=已存在仅更新 / need_confirm=需用户确认 */
export interface ApplicationCaptureResult {
  action: 'created' | 'updated' | 'need_confirm';
  recordId?: number;
  applyStatus?: string;
  matchedSummary?: string;
  message?: string;
}

export async function captureApplication(data: ApplicationCapturePayload): Promise<ApplicationCaptureResult> {
  return request<ApplicationCaptureResult>('/api/application-records/capture', {
    method: 'POST',
    body: JSON.stringify({ source: 'plugin', detectedAt: new Date().toISOString(), ...data }),
  });
}
