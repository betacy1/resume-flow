package com.resumeflow.vo;

import com.resumeflow.dto.AwardCertificateDTO;
import com.resumeflow.dto.EducationExperienceDTO;
import com.resumeflow.dto.EmergencyContactDTO;
import com.resumeflow.dto.FamilyMemberDTO;
import com.resumeflow.dto.InternshipExperienceDTO;
import com.resumeflow.dto.ProjectExperienceDTO;
import com.resumeflow.dto.SkillProfileDTO;
import com.resumeflow.dto.UserProfileDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 完整简历 Profile VO（基础信息 + 教育 + 实习 + 项目 + 技能 + 奖项 + 家庭成员 + 紧急联系人）
 */
@Data
@Builder
public class ProfileVO {

    private UserProfileDTO basicInfo;
    private List<EducationExperienceDTO> educationList;
    private List<InternshipExperienceDTO> internshipList;
    private List<ProjectExperienceDTO> projectList;
    private List<SkillProfileDTO> skillList;
    private List<AwardCertificateDTO> awardList;
    private List<FamilyMemberDTO> familyList;
    private List<EmergencyContactDTO> emergencyContactList;
}
