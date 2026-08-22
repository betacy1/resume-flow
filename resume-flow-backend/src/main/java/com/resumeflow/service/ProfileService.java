package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.*;
import com.resumeflow.entity.*;
import com.resumeflow.repository.*;
import com.resumeflow.security.SecurityUtils;
import com.resumeflow.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 简历 Profile Service（聚合：基础信息 + 教育经历 + 实习经历 + 项目经历 + 技能）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository userProfileRepository;
    private final EducationExperienceRepository educationRepository;
    private final InternshipExperienceRepository internshipRepository;
    private final ProjectExperienceRepository projectRepository;
    private final SkillProfileRepository skillRepository;
    private final AwardCertificateRepository awardRepository;

    /**
     * 查询当前用户完整简历信息
     */
    public ProfileVO getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();

        UserProfile profile = userProfileRepository.findByUserIdAndDeletedFalse(userId).orElse(null);
        UserProfileDTO basicInfo = null;
        if (profile != null) {
            basicInfo = toUserProfileDTO(profile);
        }

        List<EducationExperienceDTO> educationList = educationRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId)
                .stream().map(this::toEducationDTO).toList();

        List<InternshipExperienceDTO> internshipList = internshipRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId)
                .stream().map(this::toInternshipDTO).toList();

        List<ProjectExperienceDTO> projectList = projectRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId)
                .stream().map(this::toProjectDTO).toList();

        List<SkillProfileDTO> skillList = skillRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId)
                .stream().map(this::toSkillDTO).toList();

        List<AwardCertificateDTO> awardList = awardRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId)
                .stream().map(this::toAwardDTO).toList();

        return ProfileVO.builder()
                .basicInfo(basicInfo)
                .educationList(educationList)
                .internshipList(internshipList)
                .projectList(projectList)
                .skillList(skillList)
                .awardList(awardList)
                .build();
    }

    /**
     * 保存（新建或更新）用户简历基础信息
     */
    @Transactional
    public void saveOrUpdateProfile(UserProfileDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        UserProfile profile = userProfileRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(UserProfile::new);

        if (profile.getId() == null) {
            profile.setUserId(userId);
        }

        profile.setName(dto.getName());
        profile.setGender(dto.getGender());
        profile.setPhone(dto.getPhone());
        profile.setEmail(dto.getEmail());
        profile.setQq(dto.getQq());
        profile.setWechat(dto.getWechat());
        profile.setCurrentLocation(dto.getCurrentLocation());
        profile.setPoliticalStatus(dto.getPoliticalStatus());
        profile.setIdCard(dto.getIdCard());
        profile.setEmergencyContact(dto.getEmergencyContact());
        profile.setEmergencyPhone(dto.getEmergencyPhone());
        profile.setReferencePhone(dto.getReferencePhone());
        profile.setBankCard(dto.getBankCard());
        profile.setFamilyMembers(dto.getFamilyMembers());
        profile.setApplicantType(dto.getApplicantType());
        profile.setTargetPosition(dto.getTargetPosition());
        profile.setTargetCity(dto.getTargetCity());
        profile.setAcceptOtherCity(dto.getAcceptOtherCity());
        profile.setSchool(dto.getSchool());
        profile.setMajor(dto.getMajor());
        profile.setDegree(dto.getDegree());
        profile.setGraduationDate(dto.getGraduationDate());
        profile.setExpectedCity(dto.getExpectedCity());
        profile.setExpectedPosition(dto.getExpectedPosition());
        profile.setSelfIntroduction(dto.getSelfIntroduction());

        userProfileRepository.save(profile);
    }

    // ========== 教育经历 ==========

    @Transactional
    public void saveEducation(EducationExperienceDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        EducationExperience entity;
        if (dto.getId() != null) {
            entity = educationRepository.findById(dto.getId())
                    .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                    .orElseThrow(() -> new BusinessException("教育经历不存在"));
        } else {
            entity = new EducationExperience();
            entity.setUserId(userId);
        }
        entity.setSchool(dto.getSchool());
        entity.setSchoolTags(dto.getSchoolTags());
        entity.setMajor(dto.getMajor());
        entity.setDegree(dto.getDegree());
        entity.setCollege(dto.getCollege());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setGpa(dto.getGpa());
        entity.setRank(dto.getRank());
        entity.setAdvisor(dto.getAdvisor());
        entity.setLab(dto.getLab());
        entity.setResearchDirection(dto.getResearchDirection());
        entity.setThesis(dto.getThesis());
        entity.setHonors(dto.getHonors());
        entity.setIsDefault(dto.getIsDefault());
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        educationRepository.save(entity);
    }

    @Transactional
    public void deleteEducation(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        EducationExperience entity = educationRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BusinessException("教育经历不存在"));
        entity.setDeleted(true);
        educationRepository.save(entity);
    }

    // ========== 实习经历 ==========

    @Transactional
    public void saveInternship(InternshipExperienceDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        InternshipExperience entity;
        if (dto.getId() != null) {
            entity = internshipRepository.findById(dto.getId())
                    .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                    .orElseThrow(() -> new BusinessException("实习经历不存在"));
        } else {
            entity = new InternshipExperience();
            entity.setUserId(userId);
        }
        entity.setCompany(dto.getCompany());
        entity.setDepartment(dto.getDepartment());
        entity.setPosition(dto.getPosition());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setTechStack(dto.getTechStack());
        entity.setHighlights(dto.getHighlights());
        entity.setIsDefault(dto.getIsDefault());
        entity.setShortName(dto.getShortName());
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        internshipRepository.save(entity);
    }

    @Transactional
    public void deleteInternship(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        InternshipExperience entity = internshipRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BusinessException("实习经历不存在"));
        entity.setDeleted(true);
        internshipRepository.save(entity);
    }

    // ========== 项目经历 ==========

    @Transactional
    public void saveProject(ProjectExperienceDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        ProjectExperience entity;
        if (dto.getId() != null) {
            entity = projectRepository.findById(dto.getId())
                    .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                    .orElseThrow(() -> new BusinessException("项目经历不存在"));
        } else {
            entity = new ProjectExperience();
            entity.setUserId(userId);
        }
        entity.setProjectName(dto.getProjectName());
        entity.setRole(dto.getRole());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setIsDefault(dto.getIsDefault());
        entity.setShortName(dto.getShortName());
        entity.setDescription(dto.getDescription());
        entity.setProjectIntro(dto.getProjectIntro());
        entity.setResponsibilities(dto.getResponsibilities());
        entity.setResult(dto.getResult());
        entity.setTechStack(dto.getTechStack());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        projectRepository.save(entity);
    }

    @Transactional
    public void deleteProject(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ProjectExperience entity = projectRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BusinessException("项目经历不存在"));
        entity.setDeleted(true);
        projectRepository.save(entity);
    }

    // ========== 技能信息 ==========

    @Transactional
    public void saveSkill(SkillProfileDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        SkillProfile entity;
        if (dto.getId() != null) {
            entity = skillRepository.findById(dto.getId())
                    .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                    .orElseThrow(() -> new BusinessException("技能不存在"));
        } else {
            entity = new SkillProfile();
            entity.setUserId(userId);
        }
        entity.setSkillName(dto.getSkillName());
        entity.setLevel(dto.getLevel());
        entity.setCategory(dto.getCategory());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        skillRepository.save(entity);
    }

    @Transactional
    public void deleteSkill(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        SkillProfile entity = skillRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BusinessException("技能不存在"));
        entity.setDeleted(true);
        skillRepository.save(entity);
    }

    // ========== 奖项证书 ==========

    @Transactional
    public void saveAward(AwardCertificateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        AwardCertificate entity;
        if (dto.getId() != null) {
            entity = awardRepository.findById(dto.getId())
                    .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                    .orElseThrow(() -> new BusinessException("奖项不存在"));
        } else {
            entity = new AwardCertificate();
            entity.setUserId(userId);
        }
        entity.setAwardName(dto.getAwardName());
        entity.setAwardType(dto.getAwardType());
        entity.setAwardYear(dto.getAwardYear());
        entity.setDescription(dto.getDescription());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        awardRepository.save(entity);
    }

    @Transactional
    public void deleteAward(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        AwardCertificate entity = awardRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BusinessException("奖项不存在"));
        entity.setDeleted(true);
        awardRepository.save(entity);
    }

    // ========== Entity -> DTO 转换 ==========

    private UserProfileDTO toUserProfileDTO(UserProfile e) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setName(e.getName());
        dto.setGender(e.getGender());
        dto.setPhone(e.getPhone());
        dto.setEmail(e.getEmail());
        dto.setQq(e.getQq());
        dto.setWechat(e.getWechat());
        dto.setCurrentLocation(e.getCurrentLocation());
        dto.setPoliticalStatus(e.getPoliticalStatus());
        dto.setIdCard(e.getIdCard());
        dto.setEmergencyContact(e.getEmergencyContact());
        dto.setEmergencyPhone(e.getEmergencyPhone());
        dto.setReferencePhone(e.getReferencePhone());
        dto.setBankCard(e.getBankCard());
        dto.setFamilyMembers(e.getFamilyMembers());
        dto.setApplicantType(e.getApplicantType());
        dto.setTargetPosition(e.getTargetPosition());
        dto.setTargetCity(e.getTargetCity());
        dto.setAcceptOtherCity(e.getAcceptOtherCity());
        dto.setSchool(e.getSchool());
        dto.setMajor(e.getMajor());
        dto.setDegree(e.getDegree());
        dto.setGraduationDate(e.getGraduationDate());
        dto.setExpectedCity(e.getExpectedCity());
        dto.setExpectedPosition(e.getExpectedPosition());
        dto.setSelfIntroduction(e.getSelfIntroduction());
        return dto;
    }

    private EducationExperienceDTO toEducationDTO(EducationExperience e) {
        EducationExperienceDTO dto = new EducationExperienceDTO();
        dto.setId(e.getId());
        dto.setSchool(e.getSchool());
        dto.setSchoolTags(e.getSchoolTags());
        dto.setMajor(e.getMajor());
        dto.setDegree(e.getDegree());
        dto.setCollege(e.getCollege());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setGpa(e.getGpa());
        dto.setRank(e.getRank());
        dto.setAdvisor(e.getAdvisor());
        dto.setLab(e.getLab());
        dto.setResearchDirection(e.getResearchDirection());
        dto.setThesis(e.getThesis());
        dto.setHonors(e.getHonors());
        dto.setIsDefault(e.getIsDefault());
        dto.setDescription(e.getDescription());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }

    private InternshipExperienceDTO toInternshipDTO(InternshipExperience e) {
        InternshipExperienceDTO dto = new InternshipExperienceDTO();
        dto.setId(e.getId());
        dto.setCompany(e.getCompany());
        dto.setDepartment(e.getDepartment());
        dto.setPosition(e.getPosition());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setTechStack(e.getTechStack());
        dto.setHighlights(e.getHighlights());
        dto.setIsDefault(e.getIsDefault());
        dto.setShortName(e.getShortName());
        dto.setDescription(e.getDescription());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }

    private ProjectExperienceDTO toProjectDTO(ProjectExperience e) {
        ProjectExperienceDTO dto = new ProjectExperienceDTO();
        dto.setId(e.getId());
        dto.setProjectName(e.getProjectName());
        dto.setRole(e.getRole());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setIsDefault(e.getIsDefault());
        dto.setShortName(e.getShortName());
        dto.setDescription(e.getDescription());
        dto.setProjectIntro(e.getProjectIntro());
        dto.setResponsibilities(e.getResponsibilities());
        dto.setResult(e.getResult());
        dto.setTechStack(e.getTechStack());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }

    private SkillProfileDTO toSkillDTO(SkillProfile e) {
        SkillProfileDTO dto = new SkillProfileDTO();
        dto.setId(e.getId());
        dto.setSkillName(e.getSkillName());
        dto.setLevel(e.getLevel());
        dto.setCategory(e.getCategory());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }

    private AwardCertificateDTO toAwardDTO(AwardCertificate e) {
        AwardCertificateDTO dto = new AwardCertificateDTO();
        dto.setId(e.getId());
        dto.setAwardName(e.getAwardName());
        dto.setAwardType(e.getAwardType());
        dto.setAwardYear(e.getAwardYear());
        dto.setDescription(e.getDescription());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }
}
