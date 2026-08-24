import request from '@/utils/request';

export interface ContentVariant {
  id?: number;
  /** internship / project / material */
  sourceType?: string;
  sourceId?: number;
  /** big_tech / state_owned / bank / general */
  audienceType?: string;
  /** backend / ai / fintech / general */
  jobDirection?: string;
  /** internship_overview / internship_responsibility / internship_result / internship_tech_stack / internship_combined / project_* / combined */
  fieldType?: string;
  /** within_200 / within_300 / within_500 / within_1000 */
  lengthType?: string;
  content?: string;
  enabled?: boolean;
}

export const variantApi = {
  list: (sourceType?: string, sourceId?: number) => {
    const params = sourceType && sourceId ? { sourceType, sourceId } : {};
    return request.get<any, ContentVariant[]>('/content-variants', { params });
  },
  save: (data: ContentVariant) => request.post('/content-variants', data),
  delete: (id: number) => request.delete(`/content-variants/${id}`),
};
