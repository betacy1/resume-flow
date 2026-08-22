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
  position?: string;
  startDate?: string;
  endDate?: string;
  techStack?: string;
  highlights?: string;
  isDefault?: boolean;
  shortName?: string;
  description?: string;
  sortOrder?: number;
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
};
