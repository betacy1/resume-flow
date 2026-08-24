package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.AnswerMaterialDTO;
import com.resumeflow.entity.AnswerMaterial;
import com.resumeflow.repository.AnswerMaterialRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 素材库 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerMaterialService {

    private final AnswerMaterialRepository materialRepository;
    private final ProfileVersionService versionService;

    /**
     * 查询当前用户所有素材
     */
    public List<AnswerMaterialDTO> listMaterials(String materialType, Long templateId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AnswerMaterial> list = materialRepository.findByConditions(
                userId,
                hasText(materialType) ? materialType : null,
                templateId
        );
        return list.stream().map(this::toDTO).toList();
    }

    /**
     * 新建素材
     */
    @Transactional
    public Long createMaterial(AnswerMaterialDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();

        AnswerMaterial entity = new AnswerMaterial();
        entity.setUserId(userId);
        applyDTO(dto, entity);

        materialRepository.save(entity);
        versionService.bump(userId);
        log.info("用户 {} 创建素材: {}", userId, dto.getTitle());
        return entity.getId();
    }

    /**
     * 更新素材
     */
    @Transactional
    public void updateMaterial(Long id, AnswerMaterialDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        AnswerMaterial entity = getMaterialById(id, userId);
        applyDTO(dto, entity);
        materialRepository.save(entity);
        versionService.bump(userId);
    }

    /**
     * 删除素材
     */
    @Transactional
    public void deleteMaterial(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        AnswerMaterial entity = getMaterialById(id, userId);
        entity.setDeleted(true);
        materialRepository.save(entity);
        versionService.bump(userId);
    }

    /**
     * 内部方法：查询单个素材（校验归属）
     */
    public AnswerMaterial getMaterialById(Long id, Long userId) {
        return materialRepository.findById(id)
                .filter(m -> m.getUserId().equals(userId) && !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> new BusinessException("素材不存在"));
    }

    private void applyDTO(AnswerMaterialDTO dto, AnswerMaterial entity) {
        entity.setTitle(dto.getTitle());
        entity.setMaterialType(dto.getMaterialType());
        entity.setContent(dto.getContent());
        entity.setShortName(dto.getShortName());
        entity.setTemplateId(dto.getTemplateId());
        entity.setWordLimitType(dto.getWordLimitType());
        entity.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
    }

    private AnswerMaterialDTO toDTO(AnswerMaterial e) {
        AnswerMaterialDTO dto = new AnswerMaterialDTO();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setMaterialType(e.getMaterialType());
        dto.setContent(e.getContent());
        dto.setShortName(e.getShortName());
        dto.setTemplateId(e.getTemplateId());
        dto.setWordLimitType(e.getWordLimitType());
        dto.setEnabled(e.getEnabled());
        dto.setSortOrder(e.getSortOrder());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
