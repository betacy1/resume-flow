package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.TemplateExperienceConfigDTO;
import com.resumeflow.entity.InternshipExperience;
import com.resumeflow.entity.ProjectExperience;
import com.resumeflow.entity.TemplateExperienceConfig;
import com.resumeflow.repository.InternshipExperienceRepository;
import com.resumeflow.repository.ProjectExperienceRepository;
import com.resumeflow.repository.TemplateExperienceConfigRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板-经历关系配置服务：控制各模板下实习/项目经历的展示与自动填充策略
 */
@Service
@RequiredArgsConstructor
public class TemplateExperienceConfigService {

    private final TemplateExperienceConfigRepository configRepository;
    private final InternshipExperienceRepository internshipRepository;
    private final ProjectExperienceRepository projectRepository;
    private final ProfileVersionService versionService;

    /** 查询模板下全部经历配置（含经历名称回填，未配置的实习/项目以默认值补齐返回） */
    public List<TemplateExperienceConfigDTO> listByTemplate(Long templateId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TemplateExperienceConfig> configs = configRepository
                .findByUserIdAndTemplateIdAndDeletedFalse(userId, templateId);

        List<InternshipExperience> internships = internshipRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<ProjectExperience> projects = projectRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);

        List<TemplateExperienceConfigDTO> result = new ArrayList<>();
        List<TemplateExperienceConfigDTO> internConfigs = new ArrayList<>();
        List<TemplateExperienceConfigDTO> projectConfigs = new ArrayList<>();
        for (TemplateExperienceConfig config : configs) {
            TemplateExperienceConfigDTO dto = toDTO(config);
            if ("internship".equals(config.getSourceType())) {
                dto.setSourceName(internships.stream()
                        .filter(i -> config.getSourceId().equals(i.getId()))
                        .map(i -> i.getShortName() != null ? i.getShortName() : i.getCompany())
                        .findFirst().orElse(null));
                internConfigs.add(dto);
            } else {
                dto.setSourceName(projects.stream()
                        .filter(p -> config.getSourceId().equals(p.getId()))
                        .map(p -> p.getShortName() != null ? p.getShortName() : p.getProjectName())
                        .findFirst().orElse(null));
                projectConfigs.add(dto);
            }
        }
        // 未配置的经历以默认值（全部启用）补齐，方便后台一次性编辑
        for (InternshipExperience internship : internships) {
            if (internConfigs.stream().noneMatch(c -> internship.getId().equals(c.getSourceId()))) {
                internConfigs.add(defaultDTO(templateId, "internship", internship.getId(),
                        internship.getShortName() != null ? internship.getShortName() : internship.getCompany()));
            }
        }
        for (ProjectExperience project : projects) {
            if (projectConfigs.stream().noneMatch(c -> project.getId().equals(c.getSourceId()))) {
                projectConfigs.add(defaultDTO(templateId, "project", project.getId(),
                        project.getShortName() != null ? project.getShortName() : project.getProjectName()));
            }
        }
        result.addAll(internConfigs);
        result.addAll(projectConfigs);
        return result;
    }

    /** 批量保存某模板下的经历配置（按 templateId + sourceType + sourceId 更新或新增） */
    @Transactional
    public void saveByTemplate(Long templateId, List<TemplateExperienceConfigDTO> dtos) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (dtos == null) {
            return;
        }
        for (TemplateExperienceConfigDTO dto : dtos) {
            if (dto.getSourceType() == null || dto.getSourceId() == null) {
                throw new BusinessException("经历类型与经历 ID 不能为空");
            }
            TemplateExperienceConfig entity = configRepository
                    .findByUserIdAndTemplateIdAndSourceTypeAndSourceIdAndDeletedFalse(
                            userId, templateId, dto.getSourceType(), dto.getSourceId())
                    .orElseGet(() -> {
                        TemplateExperienceConfig config = new TemplateExperienceConfig();
                        config.setUserId(userId);
                        config.setTemplateId(templateId);
                        config.setSourceType(dto.getSourceType());
                        config.setSourceId(dto.getSourceId());
                        return config;
                    });
            entity.setIncludedInResume(dto.getIncludedInResume() == null || dto.getIncludedInResume());
            entity.setAutoFillEnabled(dto.getAutoFillEnabled() == null || dto.getAutoFillEnabled());
            entity.setAutoFillPriority(dto.getAutoFillPriority());
            entity.setManualSelectable(dto.getManualSelectable() == null || dto.getManualSelectable());
            entity.setEmphasisTags(dto.getEmphasisTags());
            entity.setDisplayOrder(dto.getDisplayOrder());
            configRepository.save(entity);
        }
        versionService.bump(userId);
    }

    private TemplateExperienceConfigDTO toDTO(TemplateExperienceConfig config) {
        TemplateExperienceConfigDTO dto = new TemplateExperienceConfigDTO();
        dto.setId(config.getId());
        dto.setTemplateId(config.getTemplateId());
        dto.setSourceType(config.getSourceType());
        dto.setSourceId(config.getSourceId());
        dto.setIncludedInResume(config.getIncludedInResume());
        dto.setAutoFillEnabled(config.getAutoFillEnabled());
        dto.setAutoFillPriority(config.getAutoFillPriority());
        dto.setManualSelectable(config.getManualSelectable());
        dto.setEmphasisTags(config.getEmphasisTags());
        dto.setDisplayOrder(config.getDisplayOrder());
        return dto;
    }

    private TemplateExperienceConfigDTO defaultDTO(Long templateId, String sourceType, Long sourceId, String name) {
        TemplateExperienceConfigDTO dto = new TemplateExperienceConfigDTO();
        dto.setTemplateId(templateId);
        dto.setSourceType(sourceType);
        dto.setSourceId(sourceId);
        dto.setSourceName(name);
        dto.setIncludedInResume(true);
        dto.setAutoFillEnabled(true);
        dto.setManualSelectable(true);
        return dto;
    }
}
