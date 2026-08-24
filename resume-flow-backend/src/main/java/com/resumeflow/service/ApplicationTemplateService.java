package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.ApplicationTemplateDTO;
import com.resumeflow.entity.ApplicationTemplate;
import com.resumeflow.repository.ApplicationTemplateRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 岗位模板 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationTemplateService {

    private final ApplicationTemplateRepository templateRepository;
    private final ProfileVersionService versionService;

    /**
     * 查询当前用户所有岗位模板
     */
    public List<ApplicationTemplateDTO> listTemplates() {
        Long userId = SecurityUtils.getCurrentUserId();
        return templateRepository.findByUserIdAndDeletedFalseOrderByIsDefaultDescIdAsc(userId)
                .stream().map(this::toDTO).toList();
    }

    /**
     * 查询单个模板（校验归属）
     */
    public ApplicationTemplate getTemplateById(Long templateId, Long userId) {
        return templateRepository.findById(templateId)
                .filter(t -> t.getUserId().equals(userId) && !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new BusinessException("模板不存在"));
    }

    /**
     * 新建岗位模板
     */
    @Transactional
    public Long createTemplate(ApplicationTemplateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        ApplicationTemplate entity = new ApplicationTemplate();
        entity.setUserId(userId);
        applyDTO(dto, entity);

        templateRepository.save(entity);
        versionService.bump(userId);
        log.info("用户 {} 创建岗位模板: {}", userId, dto.getName());
        return entity.getId();
    }

    /**
     * 更新岗位模板
     */
    @Transactional
    public void updateTemplate(Long id, ApplicationTemplateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationTemplate entity = getTemplateById(id, userId);
        applyDTO(dto, entity);
        templateRepository.save(entity);
        versionService.bump(userId);
    }

    /**
     * 删除岗位模板
     */
    @Transactional
    public void deleteTemplate(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationTemplate entity = getTemplateById(id, userId);
        entity.setDeleted(true);
        templateRepository.save(entity);
        versionService.bump(userId);
    }

    private void applyDTO(ApplicationTemplateDTO dto, ApplicationTemplate entity) {
        entity.setName(dto.getName());
        entity.setCategory(dto.getCategory());
        if (dto.getAudienceType() != null) {
            entity.setAudienceType(dto.getAudienceType());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        entity.setSelfEvaluation(dto.getSelfEvaluation());
        entity.setInternshipDescription(dto.getInternshipDescription());
        entity.setProjectDescription(dto.getProjectDescription());
        entity.setCareerPlan(dto.getCareerPlan());
        entity.setAiCollaboration(dto.getAiCollaboration());
        entity.setSkillKeywords(dto.getSkillKeywords());
        if (dto.getSkillOrder() != null) {
            entity.setSkillOrder(dto.getSkillOrder());
        }
        if (dto.getIsDefault() != null) {
            entity.setIsDefault(dto.getIsDefault());
        }
    }

    private ApplicationTemplateDTO toDTO(ApplicationTemplate e) {
        ApplicationTemplateDTO dto = new ApplicationTemplateDTO();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setAudienceType(e.getAudienceType());
        dto.setDescription(e.getDescription());
        dto.setCategory(e.getCategory());
        dto.setSelfEvaluation(e.getSelfEvaluation());
        dto.setInternshipDescription(e.getInternshipDescription());
        dto.setProjectDescription(e.getProjectDescription());
        dto.setCareerPlan(e.getCareerPlan());
        dto.setAiCollaboration(e.getAiCollaboration());
        dto.setSkillKeywords(e.getSkillKeywords());
        dto.setSkillOrder(e.getSkillOrder());
        dto.setIsDefault(e.getIsDefault());
        return dto;
    }
}
