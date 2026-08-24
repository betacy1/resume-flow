import request from '@/utils/request';

export interface UserProfileDTO {
  name?: string;
  gender?: string;
  phone?: string;
  email?: string;
  qq?: string;
  wechat?: string;
  currentLocation?: string;
  politicalStatus?: string;
  idCard?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  referencePhone?: string;
  bankCard?: string;
  familyMembers?: string;
  applicantType?: string;
  targetPosition?: string;
  targetCity?: string;
  acceptOtherCity?: string;
  school?: string;
  major?: string;
  degree?: string;
  graduationDate?: string;
  expectedCity?: string;
  expectedPosition?: string;
  selfIntroduction?: string;
}

export interface EducationExperienceDTO {
  id?: number;
  school?: string;
  schoolTags?: string;
  major?: string;
  degree?: string;
  college?: string;
  startDate?: string;
  endDate?: string;
  /** 学号 */
  studentNumber?: string;
  /** 学历（硕士研究生/大学本科/高中） */
  educationLevel?: string;
  /** 学位（硕士/学士） */
  academicDegree?: string;
  /** 学习形式 */
  studyMode?: string;
  /** 主修课程及成绩 */
  courses?: string;
  /** 高考录取批次 */
  admissionBatch?: string;
  /** 显示专业 */
  displayMajor?: string;
  gpa?: string;
  rank?: string;
  advisor?: string;
  lab?: string;
  researchDirection?: string;
  thesis?: string;
  honors?: string;
  isDefault?: boolean;
  description?: string;
  sortOrder?: number;
}

export interface InternshipExperienceDTO {
  id?: number;
  company?: string;
  department?: string;
  /** 工作地点（城市） */
  city?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
  techStack?: string;
  highlights?: string;
  isDefault?: boolean;
  shortName?: string;
  description?: string;
  sortOrder?: number;
  /** 排除的场景模板（逗号分隔，如 big_tech） */
  audienceExclude?: string;
  /** 各场景模板下的优先级 JSON，如 {"bank":1,"state_owned":2}，数值越小越优先 */
  templatePriority?: string;
  /** 证明人姓名 */
  certifierName?: string;
  /** 证明人单位 */
  certifierCompany?: string;
  /** 证明人职务 */
  certifierPosition?: string;
  /** 证明人单位及职务 */
  certifierCompanyAndPosition?: string;
  /** 证明人联系电话 */
  certifierPhone?: string;
  /** 证明人邮箱 */
  certifierEmail?: string;
  /** 证明人与本人关系 */
  certifierRelation?: string;
  /** 证明人备注 */
  certifierRemark?: string;
}

/** 家庭成员（父亲/母亲等，每个用户默认都有该结构，内容可为空） */
export interface FamilyMemberDTO {
  id?: number;
  relation?: string;
  name?: string;
  company?: string;
  position?: string;
  phone?: string;
  email?: string;
  politicalStatus?: string;
  address?: string;
  remark?: string;
  sortOrder?: number;
  enabled?: boolean;
}

/** 紧急联系人（与家庭成员分别独立维护） */
export interface EmergencyContactDTO {
  id?: number;
  name?: string;
  relation?: string;
  phone?: string;
  company?: string;
  position?: string;
  address?: string;
  remark?: string;
  enabled?: boolean;
}

export interface ProjectExperienceDTO {
  id?: number;
  projectName?: string;
  role?: string;
  startDate?: string;
  endDate?: string;
  isDefault?: boolean;
  shortName?: string;
  description?: string;
  projectIntro?: string;
  responsibilities?: string;
  result?: string;
  techStack?: string;
  sortOrder?: number;
  /** 排除的场景模板（逗号分隔，如 big_tech） */
  audienceExclude?: string;
}

export interface SkillProfileDTO {
  id?: number;
  skillName?: string;
  level?: string;
  category?: string;
  sortOrder?: number;
}

export interface AwardCertificateDTO {
  id?: number;
  awardName?: string;
  awardType?: string;
  awardYear?: string;
  /** 奖项级别（院校级/省部级/国家级） */
  awardLevel?: string;
  description?: string;
  sortOrder?: number;
}

export interface ProfileVO {
  basicInfo: UserProfileDTO | null;
  educationList: EducationExperienceDTO[];
  internshipList: InternshipExperienceDTO[];
  projectList: ProjectExperienceDTO[];
  skillList: SkillProfileDTO[];
  awardList: AwardCertificateDTO[];
  familyList: FamilyMemberDTO[];
  emergencyContactList: EmergencyContactDTO[];
}

export const profileApi = {
  getProfile: () => request.get<any, ProfileVO>('/profile'),
  saveProfile: (data: UserProfileDTO) => request.put('/profile', data),

  saveEducation: (data: EducationExperienceDTO) => request.post('/profile/education', data),
  deleteEducation: (id: number) => request.delete(`/profile/education/${id}`),

  saveInternship: (data: InternshipExperienceDTO) => request.post('/profile/internship', data),
  deleteInternship: (id: number) => request.delete(`/profile/internship/${id}`),

  saveProject: (data: ProjectExperienceDTO) => request.post('/profile/project', data),
  deleteProject: (id: number) => request.delete(`/profile/project/${id}`),

  saveSkill: (data: SkillProfileDTO) => request.post('/profile/skill', data),
  deleteSkill: (id: number) => request.delete(`/profile/skill/${id}`),

  saveAward: (data: AwardCertificateDTO) => request.post('/profile/award', data),
  deleteAward: (id: number) => request.delete(`/profile/award/${id}`),

  saveFamily: (data: FamilyMemberDTO) => request.post('/profile/family', data),
  deleteFamily: (id: number) => request.delete(`/profile/family/${id}`),

  saveEmergencyContact: (data: EmergencyContactDTO) => request.post('/profile/emergency-contact', data),
  deleteEmergencyContact: (id: number) => request.delete(`/profile/emergency-contact/${id}`),
};
