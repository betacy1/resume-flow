package com.resumeflow.service;

import com.resumeflow.entity.*;
import com.resumeflow.repository.*;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 简历预览 Service
 * 根据岗位模板生成完整简历预览：经历范围由 template_experience_config 决定
 * （仅包含 included_in_resume=true 的经历，按 display_order 排序），
 * 专业技能按模板 skill_order 排序展示，必须包含专业技能模块。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplatePreviewService {

    private final ApplicationTemplateRepository templateRepository;
    private final TemplateExperienceConfigRepository configRepository;
    private final UserProfileRepository userProfileRepository;
    private final EducationExperienceRepository educationRepository;
    private final InternshipExperienceRepository internshipRepository;
    private final ProjectExperienceRepository projectRepository;
    private final SkillProfileRepository skillRepository;
    private final AwardCertificateRepository awardRepository;
    private final ContentVariantRepository variantRepository;

    public Map<String, Object> preview(Long templateId) {
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationTemplate template = templateRepository.findById(templateId)
                .filter(t -> t.getUserId().equals(userId) && !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new com.resumeflow.common.BusinessException("模板不存在"));

        List<TemplateExperienceConfig> configs = configRepository
                .findByUserIdAndTemplateIdAndDeletedFalse(userId, templateId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("template", Map.of(
                "id", template.getId(),
                "name", nz(template.getName()),
                "audienceType", nz(template.getAudienceType()),
                "description", nz(template.getDescription())));
        result.put("basicInfo", userProfileRepository.findByUserIdAndDeletedFalse(userId).orElse(null));
        result.put("educationList", educationRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
        result.put("internships", filterExperiences(configs, "internship",
                internshipRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId).stream()
                        .collect(Collectors.toMap(InternshipExperience::getId, Function.identity()))));
        result.put("projects", filterExperiences(configs, "project",
                projectRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId).stream()
                        .collect(Collectors.toMap(ProjectExperience::getId, Function.identity()))));
        result.put("skills", buildSkills(userId, template));
        result.put("awards", awardRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
        result.put("selfEvaluation", nz(template.getSelfEvaluation()));
        result.put("careerPlan", nz(template.getCareerPlan()));
        result.put("aiCollaboration", nz(template.getAiCollaboration()));
        return result;
    }

    /** 仅保留该模板下 included_in_resume=true 的经历，按 display_order → auto_fill_priority 排序 */
    private <T> List<Map<String, Object>> filterExperiences(List<TemplateExperienceConfig> configs,
                                                            String sourceType, Map<Long, T> sourceMap) {
        List<TemplateExperienceConfig> included = configs.stream()
                .filter(c -> sourceType.equals(c.getSourceType()))
                .filter(c -> Boolean.TRUE.equals(c.getIncludedInResume()))
                .sorted(Comparator
                        .comparing((TemplateExperienceConfig c) -> c.getDisplayOrder() == null
                                ? Integer.MAX_VALUE : c.getDisplayOrder())
                        .thenComparing(c -> c.getAutoFillPriority() == null
                                ? Integer.MAX_VALUE : c.getAutoFillPriority()))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TemplateExperienceConfig config : included) {
            T source = sourceMap.get(config.getSourceId());
            if (source == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", source);
            item.put("emphasisTags", config.getEmphasisTags());
            item.put("displayOrder", config.getDisplayOrder());
            result.add(item);
        }
        // 无配置记录的经历默认全部展示（兼容未初始化配置的场景）
        if (result.isEmpty()) {
            for (T source : sourceMap.values()) {
                result.add(Map.of("source", source));
            }
        }
        return result;
    }

    /** 专业技能模块：按模板排序展示七个分组 + 该受众下的简短版/完整版/关键词 */
    private Map<String, Object> buildSkills(Long userId, ApplicationTemplate template) {
        List<SkillProfile> skills = skillRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        Map<String, SkillProfile> byKey = skills.stream()
                .filter(s -> s.getSkillKey() != null)
                .collect(Collectors.toMap(SkillProfile::getSkillKey, Function.identity(), (a, b) -> a));

        List<String> order = new ArrayList<>();
        if (template.getSkillOrder() != null && !template.getSkillOrder().isBlank()) {
            for (String key : template.getSkillOrder().split(",")) {
                if (!key.isBlank() && !order.contains(key.trim())) {
                    order.add(key.trim());
                }
            }
        }
        for (String key : SkillService.SKILL_KEYS.keySet()) {
            if (!order.contains(key)) {
                order.add(key);
            }
        }

        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String key : order) {
            SkillProfile skill = byKey.get(key);
            if (skill == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillKey", key);
            item.put("title", skill.getSkillName());
            item.put("content", skill.getContent());
            ordered.add(item);
        }

        String audience = template.getAudienceType() == null || template.getAudienceType().isBlank()
                ? "general"
                : ("general_backend".equals(template.getAudienceType()) ? "general" : template.getAudienceType());
        List<ContentVariant> variants = variantRepository
                .findByUserIdAndSourceTypeAndSourceIdAndAudienceTypeAndEnabledTrueAndDeletedFalse(
                        userId, SkillService.SKILL_SOURCE_TYPE, SkillService.SKILL_SOURCE_ID, audience);

        Map<String, Object> skillsSection = new LinkedHashMap<>();
        skillsSection.put("ordered", ordered);
        skillsSection.put("keywords", nz(template.getSkillKeywords()));
        skillsSection.put("short", pickVariantContent(variants, "skill_short"));
        skillsSection.put("full", pickVariantContent(variants, "skill_full"));
        return skillsSection;
    }

    /** 优先取完整版，其次取更长档位 */
    private String pickVariantContent(List<ContentVariant> variants, String fieldType) {
        return variants.stream()
                .filter(v -> fieldType.equals(v.getFieldType()))
                .sorted((a, b) -> ContentVariantService.LENGTH_ORDER.indexOf(b.getLengthType())
                        - ContentVariantService.LENGTH_ORDER.indexOf(a.getLengthType()))
                .map(ContentVariant::getContent)
                .filter(c -> c != null && !c.isBlank())
                .findFirst().orElse("");
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }
}
