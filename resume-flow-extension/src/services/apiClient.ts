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
): Promise<AutofillMatchResponse> {
  return request<AutofillMatchResponse>('/api/autofill/match', {
    method: 'POST',
    body: JSON.stringify({ templateId, pageUrl, pageTitle, fields, audienceType }),
  });
}

export async function addFieldKeyword(fieldId: number, keyword: string): Promise<void> {
  return request<void>(`/api/custom-fields/${fieldId}/keywords?keyword=${encodeURIComponent(keyword)}`, {
    method: 'POST',
  });
}
