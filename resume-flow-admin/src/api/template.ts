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
