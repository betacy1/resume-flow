package com.resumeflow.service;

import com.resumeflow.dto.SkillProfileDTO;
import com.resumeflow.entity.ApplicationTemplate;
import com.resumeflow.entity.ContentVariant;
import com.resumeflow.entity.SkillProfile;
import com.resumeflow.repository.ApplicationTemplateRepository;
import com.resumeflow.repository.ContentVariantRepository;
import com.resumeflow.repository.SkillProfileRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专业技能 Service
 * skill_profile 保存七个技能分组（后端开发、数据库与中间件、分布式与稳定性、AI 应用工程化、
 * DevOps 与平台工程、前端与工程工具、计算机基础）；
 * content_variant 保存各模板受众下的技能版本（sourceType=skill, sourceId=0，
 * fieldType=skill_full / skill_short / skill_keywords，lengthType=within_100/200/300/500/full）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    /** 技能内容版本来源标识（用户级数据，sourceId 固定为 0） */
    public static final String SKILL_SOURCE_TYPE = "skill";
    public static final Long SKILL_SOURCE_ID = 0L;

    /** 七个技能分组的默认顺序与标题 */
    public static final Map<String, String> SKILL_KEYS = new LinkedHashMap<>(Map.of(
            "skill_backend", "后端开发",
            "skill_database_middleware", "数据库与中间件",
            "skill_distributed_stability", "分布式与稳定性",
            "skill_ai_engineering", "AI 应用工程化",
            "skill_devops_platform", "DevOps 与平台工程",
            "skill_frontend_tools", "前端与工程工具",
            "skill_computer_basic", "计算机基础"));

    private final SkillProfileRepository skillRepository;
    private final ContentVariantRepository variantRepository;
    private final ApplicationTemplateRepository templateRepository;
    private final ProfileVersionService versionService;

    /**
     * 查询专业技能：七个分组 + 全部技能内容版本
     */
    public Map<String, Object> getBundle() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<SkillProfileDTO> skills = skillRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId)
                .stream().map(this::toDTO).toList();
        List<Map<String, Object>> variants = variantRepository
                .findByUserIdAndSourceTypeAndDeletedFalse(userId, SKILL_SOURCE_TYPE)
                .stream().map(v -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", v.getId());
                    m.put("audienceType", v.getAudienceType());
                    m.put("fieldType", v.getFieldType());
                    m.put("lengthType", v.getLengthType());
                    m.put("content", v.getContent());
                    return m;
                }).toList();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("skillKeys", SKILL_KEYS);
        bundle.put("skills", skills);
        bundle.put("variants", variants);
        return bundle;
    }

    /**
     * 批量保存专业技能分组，并重新生成各模板下的技能内容版本
     */
    @Transactional
    public void saveBundle(List<SkillProfileDTO> skills, String keywords) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (skills != null) {
            for (SkillProfileDTO dto : skills) {
                if (dto.getSkillKey() == null || dto.getSkillKey().isBlank()) {
                    continue;
                }
                SkillProfile entity = skillRepository
                        .findByUserIdAndSkillKeyAndDeletedFalse(userId, dto.getSkillKey())
                        .orElseGet(() -> {
                            SkillProfile e = new SkillProfile();
                            e.setUserId(userId);
                            e.setSkillKey(dto.getSkillKey());
                            return e;
                        });
                entity.setSkillName(dto.getSkillName() != null
                        ? dto.getSkillName() : SKILL_KEYS.getOrDefault(dto.getSkillKey(), dto.getSkillKey()));
                entity.setContent(dto.getContent());
                entity.setLevel(dto.getLevel());
                entity.setCategory(dto.getCategory() != null ? dto.getCategory() : "专业技能");
                if (dto.getSortOrder() != null) {
                    entity.setSortOrder(dto.getSortOrder());
                } else if (entity.getSortOrder() == null) {
                    entity.setSortOrder(new ArrayList<>(SKILL_KEYS.keySet()).indexOf(dto.getSkillKey()));
                }
                skillRepository.save(entity);
            }
        }
        // 技能关键词为公共字段，统一更新到全部模板
        if (keywords != null && !keywords.isBlank()) {
            for (ApplicationTemplate template
                    : templateRepository.findByUserIdAndDeletedFalseOrderByIsDefaultDescIdAsc(userId)) {
                template.setSkillKeywords(keywords);
                templateRepository.save(template);
            }
        }
        regenerateVariants(userId);
        versionService.bump(userId);
    }

    /**
     * 手动重新生成各模板下的技能多字数版本（100/200/300/500/完整）
     */
    @Transactional
    public void regenerate() {
        Long userId = SecurityUtils.getCurrentUserId();
        regenerateVariants(userId);
        versionService.bump(userId);
    }

    /**
     * 按模板技能排序生成技能内容版本：
     * skill_full 完整版按模板排序拼接七个分组；
     * skill_short 简短版保留既有完整文本（无则用完整版兜底），派生 100/200/300/500 字档；
     * skill_keywords 关键词版取自模板技能关键词。
     */
    private void regenerateVariants(Long userId) {
        // 保留已有的 skill_short 完整文本（用户可在版本管理中自定义），其余技能版本重建
        Map<String, String> existingShort = new LinkedHashMap<>();
        for (ContentVariant v : variantRepository.findByUserIdAndSourceTypeAndDeletedFalse(userId, SKILL_SOURCE_TYPE)) {
            if ("skill_short".equals(v.getFieldType()) && "full".equals(v.getLengthType())) {
                existingShort.put(v.getAudienceType(), v.getContent());
            }
            v.setDeleted(true);
            variantRepository.save(v);
        }

        List<SkillProfile> skills = skillRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        for (ApplicationTemplate template
                : templateRepository.findByUserIdAndDeletedFalseOrderByIsDefaultDescIdAsc(userId)) {
            String audience = normalizeAudience(template.getAudienceType());
            List<String> order = parseSkillOrder(template.getSkillOrder());
            String full = composeFull(skills, order);

            saveVariant(userId, audience, "skill_full", "full", full);
            saveVariant(userId, audience, "skill_full", "within_500", truncate(full, 500));
            saveVariant(userId, audience, "skill_full", "within_300", truncate(full, 300));

            String shortText = existingShort.getOrDefault(audience, truncate(full, 300));
            saveVariant(userId, audience, "skill_short", "full", shortText);
            saveVariant(userId, audience, "skill_short", "within_500", truncate(shortText, 500));
            saveVariant(userId, audience, "skill_short", "within_300", truncate(shortText, 300));
            saveVariant(userId, audience, "skill_short", "within_200", truncate(shortText, 200));
            saveVariant(userId, audience, "skill_short", "within_100", truncate(shortText, 100));

            String keywords = template.getSkillKeywords() == null ? "" : template.getSkillKeywords();
            saveVariant(userId, audience, "skill_keywords", "within_200", truncate(keywords, 200));
        }
        log.info("用户 {} 专业技能版本生成完成，共 {} 条", userId,
                variantRepository.countByUserIdAndSourceTypeAndDeletedFalse(userId, SKILL_SOURCE_TYPE));
    }

    /** 按模板排序拼接完整专业技能：1、分组标题：内容 */
    private String composeFull(List<SkillProfile> skills, List<String> order) {
        Map<String, SkillProfile> byKey = new LinkedHashMap<>();
        for (SkillProfile skill : skills) {
            if (skill.getSkillKey() != null) {
                byKey.put(skill.getSkillKey(), skill);
            }
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (String key : order) {
            SkillProfile skill = byKey.get(key);
            if (skill == null || skill.getContent() == null || skill.getContent().isBlank()) {
                continue;
            }
            sb.append(index++).append("、").append(skill.getSkillName()).append("：")
                    .append(skill.getContent().trim()).append("\n");
        }
        return sb.toString().trim();
    }

    private List<String> parseSkillOrder(String skillOrder) {
        List<String> result = new ArrayList<>();
        if (skillOrder != null && !skillOrder.isBlank()) {
            for (String key : skillOrder.split(",")) {
                if (!key.isBlank() && !result.contains(key.trim())) {
                    result.add(key.trim());
                }
            }
        }
        for (String key : SKILL_KEYS.keySet()) {
            if (!result.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private void saveVariant(Long userId, String audience, String fieldType, String lengthType, String content) {
        ContentVariant variant = new ContentVariant();
        variant.setUserId(userId);
        variant.setSourceType(SKILL_SOURCE_TYPE);
        variant.setSourceId(SKILL_SOURCE_ID);
        variant.setAudienceType(audience);
        variant.setJobDirection("general");
        variant.setFieldType(fieldType);
        variant.setLengthType(lengthType);
        variant.setContent(content);
        variant.setEnabled(true);
        variantRepository.save(variant);
    }

    /** general_backend 模板的内容版本受众统一为 general */
    private String normalizeAudience(String audienceType) {
        if (audienceType == null || audienceType.isBlank()) {
            return "general";
        }
        return "general_backend".equals(audienceType) ? "general" : audienceType;
    }

    /** 按句边界截断到指定字数以内 */
    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        String cut = text.substring(0, limit);
        int idx = Math.max(cut.lastIndexOf('。'), Math.max(cut.lastIndexOf('；'),
                Math.max(cut.lastIndexOf(';'), Math.max(cut.lastIndexOf('，'), cut.lastIndexOf('\n')))));
        if (idx > limit / 2) {
            return cut.substring(0, idx + 1);
        }
        return cut;
    }

    private SkillProfileDTO toDTO(SkillProfile e) {
        SkillProfileDTO dto = new SkillProfileDTO();
        dto.setId(e.getId());
        dto.setSkillKey(e.getSkillKey());
        dto.setSkillName(e.getSkillName());
        dto.setLevel(e.getLevel());
        dto.setCategory(e.getCategory());
        dto.setContent(e.getContent());
        dto.setSortOrder(e.getSortOrder());
        return dto;
    }
}
