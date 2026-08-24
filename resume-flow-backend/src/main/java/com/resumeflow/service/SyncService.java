package com.resumeflow.service;

import com.resumeflow.dto.ContentVariantDTO;
import com.resumeflow.dto.InternshipExperienceDTO;
import com.resumeflow.dto.ProjectExperienceDTO;
import com.resumeflow.dto.TemplateExperienceConfigDTO;
import com.resumeflow.dto.UserCustomFieldDTO;
import com.resumeflow.entity.ContentVariant;
import com.resumeflow.security.SecurityUtils;
import com.resumeflow.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件数据同步 Service
 * /api/sync/status 返回版本号与哈希供插件判断是否有更新；
 * /api/sync/full 返回完整可填写数据（全部字段无过滤，参与自动填充）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final ProfileVersionService versionService;
    private final ProfileService profileService;
    private final AnswerMaterialService materialService;
    private final UserCustomFieldService customFieldService;
    private final ApplicationTemplateService templateService;
    private final TemplateExperienceConfigService configService;
    private final ContentVariantService variantService;

    /**
     * 同步状态：版本号、数据哈希、最后更新时间
     */
    public Map<String, Object> status() {
        return versionService.status(SecurityUtils.getCurrentUserId());
    }

    /**
     * 全量同步数据：基础信息、教育、实习、项目、技能、奖项、开放题素材、
     * 字段匹配规则（自定义字段）、模板、模板经历配置、内容版本、用户偏好
     */
    public Map<String, Object> fullPayload() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> syncStatus = versionService.status(userId);

        // 简历主体：全部字段（身份证号、紧急联系人、银行卡等）均按普通字段下发，参与自动填充
        ProfileVO profile = profileService.getProfile();

        // 字段匹配规则：全部自定义字段
        List<UserCustomFieldDTO> customFields = customFieldService.list(null, null, null, null);

        // 模板与模板经历配置
        var templates = templateService.listTemplates();
        List<TemplateExperienceConfigDTO> templateConfigs = new ArrayList<>();
        for (var template : templates) {
            templateConfigs.addAll(configService.listByTemplate(template.getId()));
        }

        // 内容版本（含专业技能版本）
        List<ContentVariantDTO> variants = variantService.listAll().stream()
                .map(this::toVariantDTO).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profileVersion", syncStatus.get("profileVersion"));
        payload.put("dataHash", syncStatus.get("dataHash"));
        payload.put("updatedAt", syncStatus.get("updatedAt"));
        payload.put("basicInfo", profile.getBasicInfo());
        payload.put("educationList", profile.getEducationList());
        // 实习/项目：结构化子字段 + 别名字段 + 各字数版本变体挂载，插件手动/自动填充共用同一份结构化数据
        payload.put("internshipList", enrichInternships(profile.getInternshipList(), variants));
        payload.put("projectList", enrichProjects(profile.getProjectList(), variants));
        payload.put("skillList", profile.getSkillList());
        payload.put("awardList", profile.getAwardList());
        payload.put("familyList", profile.getFamilyList());
        payload.put("emergencyContactList", profile.getEmergencyContactList());
        payload.put("materials", materialService.listMaterials(null, null));
        payload.put("customFields", customFields);
        payload.put("templates", templates);
        payload.put("templateConfigs", templateConfigs);
        payload.put("contentVariants", variants);
        log.info("用户 {} 全量同步：版本 {}，内容版本 {} 条", userId, syncStatus.get("profileVersion"), variants.size());
        return payload;
    }

    private ContentVariantDTO toVariantDTO(ContentVariant e) {
        ContentVariantDTO dto = new ContentVariantDTO();
        dto.setId(e.getId());
        dto.setSourceType(e.getSourceType());
        dto.setSourceId(e.getSourceId());
        dto.setAudienceType(e.getAudienceType());
        dto.setJobDirection(e.getJobDirection());
        dto.setFieldType(e.getFieldType());
        dto.setLengthType(e.getLengthType());
        dto.setContent(e.getContent());
        dto.setEnabled(e.getEnabled());
        return dto;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** 时间范围：2025-11 → 2025.11 格式拼接 */
    private String formatDateRange(String start, String end) {
        if (!hasText(start) && !hasText(end)) {
            return "";
        }
        String s = hasText(start) ? start.replace('-', '.') : "";
        String e = hasText(end) ? end.replace('-', '.') : "";
        return s + " - " + e;
    }

    private List<ContentVariantDTO> pickVariants(List<ContentVariantDTO> variants, String sourceType,
                                                 Long sourceId, List<String> fieldTypes) {
        if (sourceId == null) {
            return List.of();
        }
        return variants.stream()
                .filter(v -> sourceType.equals(v.getSourceType()) && sourceId.equals(v.getSourceId())
                        && fieldTypes.contains(v.getFieldType()))
                .toList();
    }

    /**
     * 实习经历结构化下发：保留原有字段（company/position 等，兼容存量插件）+
     * 别名字段（recordName/companyName/positionName/jobTitle/dateRange/city）+
     * 职责/成果/合并版的多字数版本变体。
     */
    private List<Map<String, Object>> enrichInternships(List<InternshipExperienceDTO> list,
                                                        List<ContentVariantDTO> variants) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InternshipExperienceDTO n : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("recordName", hasText(n.getShortName()) ? n.getShortName() : n.getCompany());
            m.put("companyName", n.getCompany());
            m.put("company", n.getCompany());
            m.put("positionName", n.getPosition());
            m.put("position", n.getPosition());
            m.put("jobTitle", n.getPosition());
            m.put("startDate", n.getStartDate());
            m.put("endDate", n.getEndDate());
            m.put("dateRange", formatDateRange(n.getStartDate(), n.getEndDate()));
            m.put("department", n.getDepartment());
            m.put("city", n.getCity());
            m.put("techStack", n.getTechStack());
            m.put("description", n.getDescription());
            m.put("highlights", n.getHighlights());
            m.put("shortName", n.getShortName());
            m.put("sortOrder", n.getSortOrder());
            m.put("certifierName", n.getCertifierName());
            m.put("certifierCompany", n.getCertifierCompany());
            m.put("certifierPosition", n.getCertifierPosition());
            m.put("certifierCompanyAndPosition", n.getCertifierCompanyAndPosition());
            m.put("certifierPhone", n.getCertifierPhone());
            m.put("certifierEmail", n.getCertifierEmail());
            m.put("certifierRelation", n.getCertifierRelation());
            m.put("certifierRemark", n.getCertifierRemark());
            m.put("responsibilityVariants", pickVariants(variants, "internship", n.getId(),
                    List.of("internship_responsibility")));
            m.put("resultVariants", pickVariants(variants, "internship", n.getId(),
                    List.of("internship_result")));
            m.put("combinedVariants", pickVariants(variants, "internship", n.getId(),
                    List.of("internship_combined", "combined")));
            out.add(m);
        }
        return out;
    }

    /**
     * 项目经历结构化下发：原有字段 + dateRange 别名 +
     * 描述/主要工作/成果/合并版的多字数版本变体。
     */
    private List<Map<String, Object>> enrichProjects(List<ProjectExperienceDTO> list,
                                                     List<ContentVariantDTO> variants) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProjectExperienceDTO p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("recordName", hasText(p.getShortName()) ? p.getShortName() : p.getProjectName());
            m.put("projectName", p.getProjectName());
            m.put("role", p.getRole());
            m.put("startDate", p.getStartDate());
            m.put("endDate", p.getEndDate());
            m.put("dateRange", formatDateRange(p.getStartDate(), p.getEndDate()));
            m.put("techStack", p.getTechStack());
            m.put("description", p.getDescription());
            m.put("projectIntro", p.getProjectIntro());
            m.put("responsibilities", p.getResponsibilities());
            m.put("result", p.getResult());
            m.put("shortName", p.getShortName());
            m.put("sortOrder", p.getSortOrder());
            m.put("descriptionVariants", pickVariants(variants, "project", p.getId(),
                    List.of("project_overview")));
            m.put("responsibilityVariants", pickVariants(variants, "project", p.getId(),
                    List.of("project_responsibility")));
            m.put("resultVariants", pickVariants(variants, "project", p.getId(),
                    List.of("project_result")));
            m.put("combinedVariants", pickVariants(variants, "project", p.getId(),
                    List.of("project_combined", "combined")));
            out.add(m);
        }
        return out;
    }
}
