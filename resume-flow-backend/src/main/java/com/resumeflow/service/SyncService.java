package com.resumeflow.service;

import com.resumeflow.dto.ContentVariantDTO;
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
        payload.put("internshipList", profile.getInternshipList());
        payload.put("projectList", profile.getProjectList());
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
}
