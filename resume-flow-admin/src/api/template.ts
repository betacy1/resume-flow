import request from '@/utils/request';

export interface ApplicationTemplateDTO {
  id?: number;
  name?: string;
  category?: string;
  /** big_tech / state_owned / bank / general_backend，决定内容版本受众风格 */
  audienceType?: string;
  description?: string;
  selfEvaluation?: string;
  internshipDescription?: string;
  projectDescription?: string;
  careerPlan?: string;
  aiCollaboration?: string;
  skillKeywords?: string;
  /** 专业技能排序（逗号分隔的 skillKey，决定各模板下技能展示顺序） */
  skillOrder?: string;
  isDefault?: boolean;
}

export interface AnswerMaterialDTO {
  id?: number;
  title?: string;
  materialType?: string;
  content?: string;
  shortName?: string;
  templateId?: number;
  wordLimitType?: string;
  enabled?: boolean;
  sortOrder?: number;
  updateTime?: string;
}

export interface UserCustomFieldDTO {
  id?: number;
  templateId?: number;
  fieldKey?: string;
  fieldName?: string;
  fieldType?: string;
  fieldCategory?: string;
  fieldValue?: string;
  matchKeywords?: string[];
  sensitive?: boolean;
  enabled?: boolean;
  sortOrder?: number;
  updateTime?: string;
}

export const templateApi = {
  list: () => request.get<any, ApplicationTemplateDTO[]>('/templates'),
  create: (data: ApplicationTemplateDTO) => request.post<any, number>('/templates', data),
  update: (id: number, data: ApplicationTemplateDTO) => request.put(`/templates/${id}`, data),
  delete: (id: number) => request.delete(`/templates/${id}`),
  /** 按模板生成完整简历预览（经历范围由配置表决定，必含专业技能模块） */
  resumePreview: (id: number) => request.get<any, ResumePreviewVO>(`/templates/${id}/resume-preview`),
};

/** 简历预览载荷 */
export interface ResumePreviewVO {
  template: { id: number; name: string; audienceType: string; description: string };
  basicInfo: Record<string, any> | null;
  educationList: any[];
  internships: Array<{ source: Record<string, any>; emphasisTags?: string; displayOrder?: number }>;
  projects: Array<{ source: Record<string, any>; emphasisTags?: string; displayOrder?: number }>;
  skills: {
    ordered: Array<{ skillKey: string; title: string; content: string }>;
    keywords: string;
    short: string;
    full: string;
  };
  awards: any[];
  selfEvaluation: string;
  careerPlan: string;
  aiCollaboration: string;
}

/** 模板-经历关系配置：控制某模板下实习/项目的展示与自动填充策略 */
export interface TemplateExperienceConfigDTO {
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

export const templateConfigApi = {
  list: (templateId: number) =>
    request.get<any, TemplateExperienceConfigDTO[]>('/template-configs', { params: { templateId } }),
  save: (templateId: number, data: TemplateExperienceConfigDTO[]) =>
    request.put(`/template-configs/${templateId}`, data),
};

export const materialApi = {
  list: (params?: { materialType?: string; templateId?: number }) =>
    request.get<any, AnswerMaterialDTO[]>('/materials', { params }),
  create: (data: AnswerMaterialDTO) => request.post<any, number>('/materials', data),
  update: (id: number, data: AnswerMaterialDTO) => request.put(`/materials/${id}`, data),
  delete: (id: number) => request.delete(`/materials/${id}`),
};

export const customFieldApi = {
  list: (params?: { keyword?: string; category?: string; enabled?: boolean; templateId?: number }) =>
    request.get<any, UserCustomFieldDTO[]>('/custom-fields', { params }),
  create: (data: UserCustomFieldDTO) => request.post<any, number>('/custom-fields', data),
  update: (id: number, data: UserCustomFieldDTO) => request.put(`/custom-fields/${id}`, data),
  delete: (id: number) => request.delete(`/custom-fields/${id}`),
  setEnabled: (id: number, enabled: boolean) =>
    request.put(`/custom-fields/${id}/enabled`, null, { params: { enabled } }),
};

export const autofillApi = {
  getLogs: (page: number, size: number) =>
    request.get<any, any>('/autofill/logs', { params: { page, size } }),
};
