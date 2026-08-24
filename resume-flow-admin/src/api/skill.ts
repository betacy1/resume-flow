import request from '@/utils/request';

export interface SkillProfileDTO {
  id?: number;
  skillKey?: string;
  skillName?: string;
  level?: string;
  category?: string;
  content?: string;
  sortOrder?: number;
}

export interface SkillVariantVO {
  id: number;
  audienceType: string;
  fieldType: string;
  lengthType: string;
  content: string;
}

export interface SkillBundle {
  skillKeys: Record<string, string>;
  skills: SkillProfileDTO[];
  variants: SkillVariantVO[];
}

export const skillApi = {
  /** 查询专业技能（七个分组 + 各模板技能内容版本） */
  bundle: () => request.get<any, SkillBundle>('/skills'),
  /** 批量保存专业技能并重新生成各模板技能版本 */
  save: (data: { skills: SkillProfileDTO[]; keywords?: string }) => request.put('/skills', data),
  /** 手动重新生成技能 100/200/300/500 字与完整版本 */
  regenerate: () => request.post('/skills/regenerate'),
};
