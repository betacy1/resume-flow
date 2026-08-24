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
  sensitive?: boolean;
  enabled?: boolean;
  sortOrder?: number;
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
}

export interface MatchResult {
  fieldId: string;
  matchedFieldKey: string;
  matchedFieldName: string;
  value: string;
  confidence: number;
  sensitive: boolean;
  reason: string;
  variantDesc?: string;
}

export interface SkippedField {
  fieldId: string;
  reason: string;
  sensitive?: boolean;
}

export interface UnmatchedField {
  fieldId: string;
  reason: string;
}

export interface AutofillMatchResponse {
  matches: MatchResult[];
  skipped: SkippedField[];
  unmatched: UnmatchedField[];
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
